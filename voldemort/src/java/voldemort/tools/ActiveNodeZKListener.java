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
    private List<ZKDataListener> zkDataListeners;
    /**
     * Watches a znode on a given cluster for children events.
     * Can pass on certain events to given zkDataListeners.
     * @param zkConnectionUrl
     * @param znode
     */
    public ActiveNodeZKListener(String zkConnectionUrl, String znode) {
        connected = false;
        zkDataListeners = Lists.newLinkedList();
        this.zkUrl = zkConnectionUrl;
        this.znode = znode;
        zooKeeper = setupZooKeeper(zkConnectionUrl);
        zkDataListeners = new ArrayList<>();
        registerWatches();
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
        String data = new String(getData(event.getPath()));
        for(ZKDataListener zkd : zkDataListeners) {
            zkd.dataChanged(event.getPath(), data);
        }
        resetWatch(event.getPath());
    }

    private byte[] getData(String path) {
        byte[] data = null;
        try {
            Stat stat = zooKeeper.exists(path, false);
            data = zooKeeper.getData(path, false, stat);
        } catch (InterruptedException | KeeperException e) {
            logger.error("error getting znode: " + path, e);
        }
        return data;
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
        List<String> children = getChildren();
        for (ZKDataListener w : zkDataListeners) {
            w.childrenList(children);
        }
    }

    public void addDataListener(ZKDataListener watcher) {

        if(zkDataListeners.contains(watcher)) {
            logger.info("Tried to register already registered watcher: " + watcher);
            return;
        }

        zkDataListeners.add(watcher);
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

    private void registerWatches() {
        try {
            List<String> children = zooKeeper.getChildren(znode, true);
            resetWatch(clusternode);
        } catch (InterruptedException | KeeperException e) {
            logger.debug("Setting watches failed: ", e);
        }
    }

    private synchronized ZooKeeper setupZooKeeper(String zkConnectionUrl) {
        ZooKeeper zk = null;
        try {
            zk = new ZooKeeper(zkConnectionUrl, 4000, this);
        } catch (IOException e) {
            logger.error("Could not connect to zooKeeper url: "+zkConnectionUrl);
            throw new ConfigurationException(e);
        }
        return zk;
    }

    public String getStringFromZooKeeper(String path){
        Stat stat = new Stat();
        String s = null;
        try {
            byte[] data = zooKeeper.getData(path, false, stat);
            s = new String(data);
            if(isBeingWatched(path)) {
                resetWatch(path);
            }
        } catch (KeeperException e) {
            throw new RuntimeException(String.format("Error getting key from ZooKeeper: %s", path), e);
        } catch (InterruptedException e) {
            throw new RuntimeException(String.format("Error getting key from ZooKeeper: %s", path), e);
        }
        return s;
    }

    private boolean isBeingWatched(String path) {
        if (path.equals(clusternode)) {
            return true;
        }
        return false;
    }

    @Override
    public void run() {
        while(true) {

        }
    }

    public ZooKeeper getZooKeeper() {
        return zooKeeper;
    }
}
