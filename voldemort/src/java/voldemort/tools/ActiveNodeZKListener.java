package voldemort.tools;

import com.google.common.collect.Lists;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import voldemort.utils.ConfigurationException;

public class ActiveNodeZKListener implements Watcher {

    private static final Logger logger = Logger.getLogger(ActiveNodeZKListener.class);
    private ZooKeeper zooKeeper;
    private boolean connected;
    private String znode;
    private List<Watcher> watchers;

    private List<String> deferredZnodewatchlist;


    /**
     * Watches a znode on a given cluster for children events.
     * Can pass on certain events to given watchers.
     * @param zkConnectionUrl
     * @param znode
     */
    public ActiveNodeZKListener(String zkConnectionUrl, String znode) {
        connected = false;
        this.znode = znode;
        deferredZnodewatchlist = Lists.newArrayList();
        zooKeeper = setupZooKeeper(zkConnectionUrl);
        registerWatch();
    }

    private void registerWatch() {
        try {
            List<String> children = zooKeeper.getChildren(znode, true);
        } catch (InterruptedException | KeeperException e) {
            logger.debug("Set watch for children on znode: " + znode + " failed: ", e);
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

            case NodeChildrenChanged:
                handleNodeChildrenChanged(event);

        }

    }

    private void handleStateChange(WatchedEvent event) {

        switch (event.getState()) {

            case Disconnected:
                this.connected = false;

            case SyncConnected:
                this.connected = true;
                registerWatch();

            case Expired:
                handleExpired();
        }

    }


    private void handleExpired() {
        logger.info("ZooKeeper session expired, not implemented handling yet!");
    }

    private void handleNodeChildrenChanged(WatchedEvent event) {
        logger.info("Children changed: " + event.getPath());
        for (Watcher w : watchers) {
            w.process(event);
        }

    }

    public void addWatcher(Watcher watcher) {

        if(watchers.contains(watcher)) {
            logger.info("Tried to register already registered watcher: " + watcher);
            return;
        }

        watchers.add(watcher);
    }
}
