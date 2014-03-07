package voldemort.tools;

import com.google.common.collect.Lists;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.zookeeper.data.Stat;
import voldemort.utils.ConfigurationException;

public class ActiveNodeZKListener implements Watcher, Runnable {

    private static final Logger logger = Logger.getLogger(ActiveNodeZKListener.class);

    private static String clusternode = "/config/cluster.xml";

    private ZooKeeper zooKeeper;
    private String zkUrl;
    private boolean connected;
    private String znode;
    private List<Watcher> watchers;

    /**
     * Watches a znode on a given cluster for children events.
     * Can pass on certain events to given watchers.
     * @param zkConnectionUrl
     * @param znode
     */
    public ActiveNodeZKListener(String zkConnectionUrl, String znode) {
        connected = false;
        watchers = Lists.newLinkedList();
        this.zkUrl = zkConnectionUrl;
        this.znode = znode;
        zooKeeper = setupZooKeeper(zkConnectionUrl);
        watchers = new ArrayList<>();
        registerWatches();
    }

    private void registerWatches() {
        try {
            List<String> children = zooKeeper.getChildren(znode, true);
            resetWatch(clusternode);
        } catch (InterruptedException | KeeperException e) {
            logger.debug("Setting watches failed: ", e);
        }
    }

    private ZooKeeper setupZooKeeper(String zkConnectionUrl) {
        ZooKeeper zk = null;
        try {
            zk = new ZooKeeper(zkConnectionUrl, 4000, this);
        } catch (IOException e) {
            logger.error("Could not connect to zooKeeper url: "+zkConnectionUrl);
            throw new ConfigurationException(e);
        }
        return zk;
    }

    @Override
    public void process(WatchedEvent event) {

        switch (event.getType()) {

            case None:
                handleStateChange(event);
                break;

            case NodeDataChanged:
                handleNodeDataChanged(event);
                break;

            case NodeChildrenChanged:
                handleNodeChildrenChanged(event);
                break;

        }

    }

    private void handleNodeDataChanged(WatchedEvent event) {
        logger.info("nodeData changed: " + event);
        resetWatch(event.getPath());
    }

    private void resetWatch(String path) {
        try {
            Stat stat = zooKeeper.exists(path, true);
        } catch (InterruptedException | KeeperException e) {
            logger.debug("Setting watch failed: " + path + " - ", e);
        }
    }

    private void handleStateChange(WatchedEvent event) {

        switch (event.getState()) {

            case Disconnected:
                this.connected = false;
                break;

            case SyncConnected:
                this.connected = true;
                registerWatches();
                break;

            case Expired:
                handleExpired();
                break;
        }

    }


    private void handleExpired() {
        logger.info("ZooKeeper session expired and dead, trying to recreate...");
        zooKeeper = setupZooKeeper(zkUrl);
        registerWatches();
    }

    public List<String> getNodeList() {
        List<String> children = Lists.newArrayList();

        if(connected) {
            children = getChildren();
            // reset watch in case
            registerWatches();
        } else {
            logger.info("Tried fetching children list, but is not in connected state!");
        }

        return children;
    }

    private void handleNodeChildrenChanged(WatchedEvent event) {
        logger.info("Children changed: " + event);

        for (Watcher w : watchers) {
            w.process(event);
        }

        registerWatches();
    }

    public void addWatcher(Watcher watcher) {

        if(watchers.contains(watcher)) {
            logger.info("Tried to register already registered watcher: " + watcher);
            return;
        }

        watchers.add(watcher);
    }

    private List<String> getChildren() {
        List<String> children = Lists.newArrayList();

        try {
            children = zooKeeper.getChildren(znode, true);
        } catch (InterruptedException | KeeperException e) {
            logger.error("Failed getting children on znode: "+znode, e);
        }
        return children;
    }

    @Override
    public void run() {
        while(true) {

        }
    }
}
