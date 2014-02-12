package voldemort.server;


import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import voldemort.utils.ConfigurationException;
import voldemort.utils.Props;

import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

public class VoldemortZooKeeperConfig extends VoldemortConfig implements Watcher {

    private ZooKeeper zk = null;
    private String zkURI = "localhost:3000";

    public VoldemortZooKeeperConfig(String voldemorthome, String zkurl) throws ConfigurationException {
        this.zk = VoldemortZooKeeperConfig.setupZooKeeper(zkurl, this);
        Props props = loadConfigs(this.zk);
        props.put("voldemort.home", voldemorthome);
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

    public static VoldemortConfig loadFromZooKeeper(String voldemorthome, String zookeeperurl) {
        VoldemortZooKeeperConfig voldemortConfig = new VoldemortZooKeeperConfig(voldemorthome, zookeeperurl);
        return voldemortConfig;
    }

    private static String getClusterConfigFromZooKeeper(ZooKeeper zk) {
        return getFileFromZooKeeper(zk, "/voldemort/config/cluster.xml");
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
        if(zk == null || !zk.getState().isAlive()) {
            try {
                zk = new ZooKeeper(zkURI, 3000, callback);
            } catch (IOException e) {
                throw new ConfigurationException("Error setting up ZooKeeper object!", e);
            }
        }
        return zk;
    }

    @Override
    public void process(WatchedEvent event) {

    }

    public static String getNodeConfigFromZooKeeper(ZooKeeper zk) throws UnknownHostException {
        String hostname = new String(InetAddress.getLocalHost().getCanonicalHostName().toString());

        return getFileFromZooKeeper(zk, "/voldemort/config/"+hostname+".properties");
    }
}
