package voldemort.server;


import org.apache.log4j.Logger;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import voldemort.store.configuration.ConfigurationStorageEngine;
import voldemort.utils.ConfigurationException;
import voldemort.utils.Props;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

public class VoldemortZooKeeperConfig extends VoldemortConfig implements Watcher {
    private final static Logger logger = Logger.getLogger(VoldemortZooKeeperConfig.class);

    private ZooKeeper zk = null;
    private String zkURL;
    private Watcher watcher;

    public VoldemortZooKeeperConfig(String voldemortHome, String voldemortConfigDir, String zkurl) throws ConfigurationException {
        zkURL = zkurl;
        this.watcher = this;
        this.zk = VoldemortZooKeeperConfig.setupZooKeeper(zkurl, this.watcher);

        Props props = loadConfigs(this.zk);
        props.put("voldemort.home", voldemortHome);

        props.put("metadata.directory", voldemortConfigDir);

        setProps(props);
    }

    private static Props loadConfigs(ZooKeeper zk) {

        Props properties = null;

        try {
            String nodeproperties = getNodeConfigFromZooKeeper(zk);
            //String clusterxml = getClusterConfigFromZooKeeper(zk);

            Properties propertiesData = new Properties();
            propertiesData.load(new StringReader(nodeproperties));
            properties = new Props(propertiesData);

        } catch(IOException e) {
            throw new ConfigurationException("Error reading configs from ZooKeeper",e);
        }
        return properties;
    }

    public static VoldemortConfig loadFromZooKeeper(String voldemorthome, String voldeConfig, String zookeeperurl) {
        if(voldeConfig == null) {
            voldeConfig = voldemorthome + "/config";
        }
        VoldemortZooKeeperConfig voldemortConfig = new VoldemortZooKeeperConfig( voldemorthome, voldeConfig, zookeeperurl);
        return voldemortConfig;
    }

    private static String getClusterConfigFromZooKeeper(ZooKeeper zk) {
        return getFileFromZooKeeper(zk, "/config/cluster.xml");
    }

        private static String getFileFromZooKeeper(ZooKeeper zk, String path) {
            Stat stat = new Stat();
            String s = null;
            try {
                byte[] configdata = zk.getData(path, false, stat);
                s = new String(configdata);
            } catch (KeeperException e) {
                throw new RuntimeException(String.format("Error getting key from ZooKeeper: %s", path), e);
            } catch (InterruptedException e) {
                throw new RuntimeException(String.format("Error getting key from ZooKeeper: %s", path), e);
            }
            return s;
        }

    public static ZooKeeper setupZooKeeper(String zkURI, Watcher callback) {
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
        logger.info(String.format("Got event from ZooKeeper: %s", event.toString()));
    }

    public static String getNodeConfigFromZooKeeper(ZooKeeper zk) throws UnknownHostException {
        String hostname = new String(InetAddress.getLocalHost().getCanonicalHostName().toString());

        return getFileFromZooKeeper(zk, "/config/"+hostname+".properties");
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
}
