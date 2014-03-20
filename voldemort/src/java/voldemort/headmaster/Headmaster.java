package voldemort.headmaster;


import com.google.common.collect.Lists;
import joptsimple.internal.Strings;
import no.uio.master.autoscale.Autoscale;
import org.apache.log4j.Logger;
import org.apache.zookeeper.CreateMode;
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


public class Headmaster implements Runnable, Watcher, ZKDataListener {

    private static final Logger logger = Logger.getLogger(Headmaster.class);

    public static final int DEFAULT_HTTP_PORT = 6881;
    public static final int DEFAULT_ADMIN_PORT = 6667;
    public static final int DEFAULT_SOCKET_PORT = 6666;
    public static final String HEADMASTER_ROOT_PATH = "/headmaster";
    public static final String HEADMASTER_ELECTION_PATH = "/headmaster_";


    public static final String defaultUrl = "voldemort1.idi.ntnu.no:2181/voldemortntnu";
    public static final String bootStrapUrl = "tcp://voldemort1.idi.ntnu.no:6667";
    private static final String HEADMASTER_REBALANCE_TOKEN = "/rebalalance_token";

    private ActiveNodeZKListener anzkl;
    private ZooKeeperHandler zkhandler;
    String zkURL = defaultUrl;
    private String activePath = "/active";
    private Cluster currentCluster;
    private List<String> childrenList;
    private String sampleServerProperties;
    private boolean idle = false;
    private String myHeadmaster;
    private String currentHeadmaster;

    private ConcurrentHashMap<String,Node> handledNodes;

    private Lock currentClusterLock;

    public Headmaster(String zkURL) {

        this.zkURL = zkURL;
        anzkl = new ActiveNodeZKListener(this.zkURL, activePath);


        currentClusterLock = new ReentrantLock();

        handledNodes = new ConcurrentHashMap<>();

        String currentClusterString = anzkl.getStringFromZooKeeper("/config/cluster.xml");
        currentCluster = new ClusterMapper().readCluster(new StringReader(currentClusterString));
        zkhandler = new ZooKeeperHandler(zkURL, anzkl.getZooKeeper());


        registerAsHeadmaster();
        leaderElection();
        
        if(isHeadmaster()){
            beHeadmaster();
        } 

    }

    private void beHeadmaster() {
        logger.debug("I AM HEADMASTER");
        anzkl.addDataListener(this);
        //Seed childrenListChanged method with initial children list
        childrenList = zkhandler.getChildren("/active");
        childrenList(childrenList);
    }

    public void registerAsHeadmaster(){
        String zkPath = zkhandler.uploadAndUpdateFileWithMode(HEADMASTER_ROOT_PATH + HEADMASTER_ELECTION_PATH, "", CreateMode.EPHEMERAL_SEQUENTIAL);
        myHeadmaster = zkPath.split("/")[2];
        System.out.println(myHeadmaster);
        logger.debug("Registered in zookeeper :" + myHeadmaster);
    }

    public boolean isHeadmaster(){
        return myHeadmaster.equals(currentHeadmaster);

    }

    public String leaderElection(){
        List<String> headmasters = zkhandler.getChildren(HEADMASTER_ROOT_PATH);

        //determine who is supposed to be leader
        String winner = "";
        int lowest_number = Integer.MAX_VALUE;
        for (String master : headmasters){
            int sequenceNumber = new Integer(master.split("_")[1]);
            if (sequenceNumber < lowest_number){
                lowest_number = sequenceNumber;
                winner = master;
            }
        }
        currentHeadmaster = winner;
        if (!winner.equals(myHeadmaster)){
            logger.debug("I did not win, setting watch on winner: " + HEADMASTER_ROOT_PATH+"/"+currentHeadmaster);
            zkhandler.setWatch(HEADMASTER_ROOT_PATH+"/"+currentHeadmaster,this);
        }
        return winner;
    }

    public void plan (){
        sampleServerProperties = zkhandler.getStringFromZooKeeper("/config/sample_files/server.properties");

        currentClusterLock.lock();

        try{
            RebalancePlannerZK rpzk = new RebalancePlannerZK(zkURL,zkhandler);
            RebalancePlan plan = rpzk.createRebalancePlan();
        } finally {
            currentClusterLock.unlock();
        }
    }

    public void rebalance(){
        currentClusterLock.lock();
        try {
            RebalancerZK rzk = new RebalancerZK(zkURL,bootStrapUrl,zkhandler);
            rzk.rebalance();
        } finally {
            currentClusterLock.unlock();
        }
    }

    @Override
    public void process(WatchedEvent event) {
        logger.info("Event: " + event.getType() + " path: " + event.getPath());
        if (event.getType() == Event.EventType.NodeChildrenChanged){

        }

        if(event.getType() == Event.EventType.NodeDeleted){
            if(event.getPath().equals(HEADMASTER_ROOT_PATH+"/" + currentHeadmaster)){
                //Leader has died, run new election
                leaderElection();

                if(isHeadmaster()){
                    beHeadmaster();
                }
            }
        }

        if(event.getType() == Event.EventType.NodeCreated){
            if(event.getPath().equals(HEADMASTER_ROOT_PATH+HEADMASTER_REBALANCE_TOKEN) && isHeadmaster()){
                plan();
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                rebalance();
            }
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

//        Autoscale as = new Autoscale("127.0.0.1", 7788);

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


