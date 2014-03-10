package voldemort.server;


import org.apache.log4j.Logger;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import voldemort.VoldemortException;
import voldemort.store.configuration.ConfigurationStorageEngine;
import voldemort.utils.ConfigurationException;
import voldemort.utils.Props;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

public class VoldemortZooKeeperConfig extends VoldemortConfig implements Watcher {
    private final static Logger logger = Logger.getLogger(VoldemortZooKeeperConfig.class);

    public final Object readyLock = new Object();
    private ZooKeeper zk = null;

    private boolean isReady = false;

    private String zkURL;
    private String hostname;
    private String voldemortHome;
    private String voldemortConfigDir;
    private Watcher watcher;
    public VoldemortZooKeeperConfig(String voldemortHome, String voldemortConfigDir, String zkurl) throws ConfigurationException {
        zkURL = zkurl;
        this.voldemortHome = voldemortHome;
        this.voldemortConfigDir = voldemortConfigDir;
        this.watcher = this;
        try {
            this.hostname = InetAddress.getLocalHost().getCanonicalHostName().toString();
        } catch (UnknownHostException e) {
            throw new ConfigurationException("Unable to determine hostname of host", e);
        }
        this.zk = setupZooKeeper(zkurl, this.watcher);

        tryToReadConfig();
    }

    private synchronized void tryToReadConfig() {
        logger.info("Trying to (re?)read config...");

        // try to create a working config, if we exception, set up an exist watch and try to re-read config
        // after an event happens
        try {
            Props props = generateProps();
            setProps(props);

            setReady(true);
            synchronized (readyLock) {
                readyLock.notifyAll();
            }
        } catch (Exception e) {

            try {
                this.zk.exists("/config/nodes/" + this.hostname + "/server.properties", true);
            } catch (KeeperException e1) {
                e1.printStackTrace();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }

        } finally {
            registerAliveness();
        }
    }

    private void registerAliveness() {
        Stat stat = null;

        String nodeid;
        if(isReady()) {
            nodeid = String.valueOf(getNodeId());
        } else {
            nodeid = VoldemortConfig.NEW_ACTIVE_NODE_STRING;
        }

        String path = "/active/" + this.hostname;

        try {
            stat = this.zk.exists(path, false);
            if (stat == null) {
                this.zk.create(path,
                        nodeid.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
            } else {
                zk.setData(path, nodeid.getBytes(), stat.getVersion());
            }
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private synchronized void setReady(boolean b) {
        this.isReady = b;
    }

    private Props generateProps() throws IOException {
        Props props = loadConfigs(this.zk);
        props.put("voldemort.home", voldemortHome);

        props.put("metadata.directory", voldemortConfigDir);

        return props;
    }

    private Props loadConfigs(ZooKeeper zk) throws IOException {

        Props properties = null;

        String nodeproperties = getNodeConfigFromZooKeeper(zk);

        Properties propertiesData = new Properties();
        propertiesData.load(new StringReader(nodeproperties));
        properties = new Props(propertiesData);

        return properties;
    }

    public static VoldemortConfig loadFromZooKeeper(String voldemorthome, String voldeConfig, String zookeeperurl) {
        if(voldeConfig == null) {
            voldeConfig = voldemorthome + "/config";
        }
        VoldemortZooKeeperConfig voldemortConfig = new VoldemortZooKeeperConfig(voldemorthome, voldeConfig, zookeeperurl);

        synchronized (voldemortConfig.readyLock) {
            while(!voldemortConfig.isReady()) {
                try {
                    voldemortConfig.readyLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return voldemortConfig;
    }

    private String getClusterConfigFromZooKeeper(ZooKeeper zk) {
        return getFileFromZooKeeper(zk, "/config/cluster.xml");
    }

    private String getFileFromZooKeeper(ZooKeeper zk, String path) throws VoldemortException {
        Stat stat = new Stat();
        String s = null;
        try {
            byte[] configdata = zk.getData(path, false, stat);
            s = new String(configdata);
        } catch (KeeperException e) {
            throw new VoldemortException(String.format("Error getting key from ZooKeeper: %s", path), e);
        } catch (InterruptedException e) {
            throw new ConfigurationException(String.format("Error getting key from ZooKeeper: %s", path), e);
        }
        return s;
    }

    public ZooKeeper setupZooKeeper(String zkURI, Watcher callback) {
        ZooKeeper zk = null;
        try {
            logger.info("creating a new zookeeper instance");
            zk = new ZooKeeper(zkURI, 3000, callback);
        } catch (IOException e) {
            throw new ConfigurationException("Error setting up ZooKeeper object!", e);
        }
        return zk;
    }

    @Override
    public void process(WatchedEvent event) {
        logger.info(String.format("Got event from ZooKeeper: %s", event));

        if(!isReady()) {
            tryToReadConfig();
        }

    }

    public String getNodeConfigFromZooKeeper(ZooKeeper zk) throws VoldemortException {
        return getFileFromZooKeeper(zk, "/config/nodes/"+this.hostname+"/server.properties");
    }

    private boolean isZooKeeperAlive() {
        if(zk == null || !zk.getState().isAlive()) {
            return false;
        }
        return true;
    }

    public ZooKeeper getZooKeeper() {
        if (isZooKeeperAlive())
            return this.zk;
        logger.info("No zookeeper instance found. Starting creating new zookeeper instance");
        zk = setupZooKeeper(zkURL, this.watcher);
        return zk;
    }
    public void setWatcher(Watcher watcher) {
        this.watcher = watcher;
        this.getZooKeeper().register(watcher);

        logger.info("Registered " + watcher + " as watcher for ZooKeeper instance.");
    }

    public String getHostname() {
        return hostname;
    }

    public boolean isReady() {
        return isReady;
    }

}
