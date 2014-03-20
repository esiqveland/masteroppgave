package voldemort.tools;

import com.google.common.collect.Lists;
import org.apache.zookeeper.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.zookeeper.data.Stat;
import org.omg.SendingContext.RunTime;
import voldemort.utils.ConfigurationException;

public class ActiveNodeZKListener implements Watcher {

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
                // make sure to send event of children if we were previously not connected
                // this is to catch potential change events while we were between sessions
                if(!this.connected) {
                    handleNodeChildrenChanged(event);
                }
                this.connected = true;
                break;

            case Expired:
                handleExpired(event);
                break;
        }

    }

    private void handleExpired(WatchedEvent event) {
        logger.info("ZooKeeper session expired and dead, trying to recreate...");
        this.connected = false;
        zooKeeper = setupZooKeeper(zkUrl);
        registerWatches();
        for(ZKDataListener listener : zkDataListeners) {
            listener.process(event);
        }
    }

    /**
     * Gets children list for given bath. Does not leave a watch.
     * @param path
     * @return
     */
    public List<String> getNodeList(String path) {
        return getNodeList(path, false);
    }

    public List<String> getNodeList(String path, boolean watch) {
        List<String> children = Lists.newArrayList();

        if(connected) {
            children = getChildren(path, watch);
            // reset watch in case
            registerWatches();
        } else {
            logger.info("Tried fetching children list, but is not in connected state!");
            throw new RuntimeException("not connected when fetching children");
        }

        return children;
    }


    private void handleNodeChildrenChanged(WatchedEvent event) {
        logger.info("Children changed: " + event);
        List<String> children = getChildren(event.getPath(), true);
        for (ZKDataListener zkDataListener : zkDataListeners) {
            zkDataListener.childrenList(children);
        }
    }

    public void removeDatalistener(ZKDataListener listener) {
        if(zkDataListeners.contains(listener)) {
            zkDataListeners.remove(listener);
        }
    }

    public void addDataListener(ZKDataListener listener) {
        if(!zkDataListeners.contains(listener)) {
            zkDataListeners.add(listener);
        }
    }

    private List<String> getChildren(String path, boolean watch) {
        List<String> children = Lists.newArrayList();

        try {
            children = zooKeeper.getChildren(path, watch);
        } catch (InterruptedException | KeeperException e) {
            logger.error("Failed getting children on znode: " + path, e);
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
            zk = new ZooKeeper(zkConnectionUrl, 20000, this);
        } catch (IOException e) {
            logger.error("Could not connect to zooKeeper url: " + zkConnectionUrl);
            throw new ConfigurationException(e);
        }
        return zk;
    }

    public String getStringFromZooKeeper(String path){
        Stat stat = new Stat();
        String s = null;
        try {
            boolean resetWatch = false;
            if (isBeingWatched(path)) {
                resetWatch = true;
            }
            byte[] data = zooKeeper.getData(path, resetWatch, stat);
            s = new String(data);
        } catch (InterruptedException | KeeperException e) {
            throw new RuntimeException(String.format("Error getting key from ZooKeeper: %s", path), e);
        }
        return s;
    }

    public void uploadAndUpdateFile(String target, String content) {
        try {
            Stat stat = zooKeeper.exists(target, false);
            if(stat == null) {
                zooKeeper.create(target, content.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } else {
                zooKeeper.setData(target, content.getBytes(), stat.getVersion());
            }
        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
        }
    }


    private boolean isBeingWatched(String path) {
        if (path.equals(clusternode)) {
            return true;
        }
        return false;
    }

    public ZooKeeper getZooKeeper() {
        return zooKeeper;
    }

}
