package voldemort.headmaster;


import com.google.common.collect.Lists;
import no.uio.master.autoscale.Autoscale;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import voldemort.client.rebalance.RebalancePlan;
import voldemort.cluster.Cluster;
import voldemort.cluster.Node;
import voldemort.server.VoldemortConfig;


import voldemort.tools.*;
import voldemort.xml.ClusterMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class Headmaster implements Runnable, Watcher, ZKDataListener {

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(ActiveNodeZKListener.class);
    public static final int DEFAULT_HTTP_PORT = 6881;
    public static final int DEFAULT_ADMIN_PORT = 6667;
    public static final int DEFAULT_SOCKET_PORT = 6666;


    public static final String defaultUrl = "voldemort1.idi.ntnu.no:2181/voldemortntnu";
    public static final String bootStrapUrl = "tcp://voldemort1.idi.ntnu.no:6667";

    private ActiveNodeZKListener anzkl;
    private ZooKeeperHandler zkhandler;
    String zkURL = defaultUrl;
    private String activePath = "/active";
    private Cluster currentCluster;
    private List<String> childrenList;
    private String sampleServerProperties;
    private boolean idle = false;

    private ConcurrentHashMap<String,Node> handledNodes;

    private Lock currentClusterLock;

    public Headmaster(String zkURL) {
        this.zkURL = zkURL;
        anzkl = new ActiveNodeZKListener(this.zkURL, activePath);
        anzkl.addDataListener(this);

        currentClusterLock = new ReentrantLock();

        handledNodes = new ConcurrentHashMap<>();

        String currentClusterString = anzkl.getStringFromZooKeeper("/config/cluster.xml");
        currentCluster = new ClusterMapper().readCluster(new StringReader(currentClusterString));
        zkhandler = new ZooKeeperHandler(zkURL, anzkl.getZooKeeper());

        for (Node node : currentCluster.getNodes()) {
            System.out.println("node.id: " + node.getId());
            System.out.println(node.toString());
        }

        sampleServerProperties = zkhandler.getStringFromZooKeeper("/config/sample_files/server.properties");

        //Seed childrenListChanged method with initial children list
        childrenList = zkhandler.getChildren("/active");
        childrenList(childrenList);

        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        currentClusterLock.lock();

        try{
            RebalancePlannerZK rpzk = new RebalancePlannerZK(zkURL,zkhandler);
            RebalancePlan plan = rpzk.createRebalancePlan();

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println(plan.toString());

            System.out.println("EXECUTING REBLANCE FOR GLORY AND SHAME");

            RebalancerZK rzk = new RebalancerZK(zkURL,bootStrapUrl,zkhandler);
            rzk.rebalance();

            System.out.println("DONE!");

        } finally {
            currentClusterLock.unlock();
        }






    }

    @Override
    public void process(WatchedEvent event) {
        logger.info("Event: " + event.getType() + " path: " + event.getPath());
        if (event.getType() == Event.EventType.NodeChildrenChanged){

        }



    }

    public static void main(String args[]) {

        String url = defaultUrl;
        if (args.length == 0) {
            System.out.println(
                    String.format(
                            "usage: %s [zookeeperurl]\nDefaults to %s", Headmaster.class.getCanonicalName(), defaultUrl));
        } else {
            url = args[0];
        }

        Headmaster headmaster = new Headmaster(url);

        Autoscale as = new Autoscale("127.0.0.1", 7788);

        Thread worker = new Thread(headmaster);
        worker.start();
    }

    public Node locateNewChildAndHandOutId(String child){
        String id = zkhandler.getStringFromZooKeeper("/active/" + child);

        if (id.equals(VoldemortConfig.NEW_ACTIVE_NODE_STRING)){
            for (Node node : currentCluster.getNodes()) {

                if (node.getHost().equals(child) && !handledNodes.containsKey(child)){
                    logger.info("existing node with NEW in active: " + child + " node: " + node);
                    return node;
                }
            }
            int newId = currentCluster.getNumberOfNodes();

            Node newNode = new Node(newId, child, DEFAULT_HTTP_PORT, DEFAULT_SOCKET_PORT,DEFAULT_ADMIN_PORT,new ArrayList<Integer>());
            return newNode;
        }

        return null;
    }

    @Override
    public synchronized void childrenList(List<String> children) {
        currentClusterLock.lock();
        try {
            HashMap<String,Node> changeMap = new HashMap<>();

            logger.info("Start childer changed");
            this.childrenList = children;
            for (String child : children){
                Node newNode = locateNewChildAndHandOutId(child);
                if ( newNode != null ){
                    changeMap.put(child, newNode);
                }

            }
            if (changeMap.isEmpty()) {
                return;
            }

            String interimClusterxml = createInterimClusterXML(changeMap);
            currentCluster = new ClusterMapper().readCluster(new StringReader(interimClusterxml));

            //upload cluster.xml

            zkhandler.uploadAndUpdateFile("/config/cluster.xml", interimClusterxml);

            //create node in nodes and upload server.properties
            for (Node node : changeMap.values()){
                String serverProp = createServerProperties(node);
                zkhandler.uploadAndUpdateFile("/config/nodes/" + node.getHost(), "");
                zkhandler.uploadAndUpdateFile("/config/nodes/" + node.getHost() + "/server.properties", serverProp);
                handledNodes.put(node.getHost(),node);
            }
        } finally {
            currentClusterLock.unlock();
        }




    }

    private String createInterimClusterXML(HashMap<String,Node> map) {
        Collection nodeCollection = currentCluster.getNodes();

        List<Node> nodeList = Lists.newLinkedList();
        nodeList.addAll(nodeCollection);

        for(Node node : map.values()){
            if(!nodeList.contains(node)){
                nodeList.add(node);
            }
            Cluster interimCluseter = new Cluster("ntnucluster",nodeList);
            String interimClusterXML = new ClusterMapper().writeCluster(interimCluseter);
            return interimClusterXML;
        }

        return new ClusterMapper().writeCluster(currentCluster);
    }

    private String createServerProperties(Node node){
        String sampleServerProp = zkhandler.getStringFromZooKeeper("/config/sample_files/server.properties");

        BufferedReader br = new BufferedReader(new StringReader(sampleServerProp));

        String line;

        StringBuilder sb = new StringBuilder();
        try {
            while((line=br.readLine())!=null)
            {
                if (line.contains("node.id")){
                    sb.append("node.id=");
                    sb.append(node.getId());
                    sb.append(System.getProperty("line.separator"));
                } else {
                    sb.append(line);
                    sb.append(System.getProperty("line.separator"));
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return sb.toString();
    }

    @Override
    public void dataChanged(String path, String content) {
        logger.info("Path changed: " + path);
        if(path.equals("/config/cluster.xml")){
                try {
                    currentClusterLock.lock();
                    this.currentCluster =  new ClusterMapper().readCluster(new StringReader(content));
                } finally {
                    currentClusterLock.unlock();
                }


        }
    }

    public void setIdle() {
        synchronized (this) {
            this.idle = true;
            // Causes all waiters to wake up.
            this.notifyAll();
        }
    }

    @Override
    public void run() {
        synchronized (this) {
            while (true) {
                // If the flag is set, we're done.
                if (this.idle) { break; }
                // Go to sleep until another thread notifies us.
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    setIdle();
                }
            }
        }
    }
}


