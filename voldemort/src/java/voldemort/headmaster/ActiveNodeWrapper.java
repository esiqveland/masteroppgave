package voldemort.headmaster;


import com.google.common.collect.Lists;
import no.uio.master.autoscale.Autoscale;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import voldemort.cluster.Cluster;
import voldemort.cluster.Node;
import voldemort.server.VoldemortConfig;


import voldemort.tools.ActiveNodeZKListener;
import voldemort.tools.ZKDataListener;
import voldemort.tools.ZooKeeperHandler;
import voldemort.xml.ClusterMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;



public class ActiveNodeWrapper implements Runnable, Watcher, ZKDataListener {

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(ActiveNodeZKListener.class);
    public static final int DEFAULT_HTTP_PORT = 6881;
    public static final int DEFAULT_ADMIN_PORT = 6667;
    public static final int DEFAULT_SOCKET_PORT = 6666;


    public static final String defaultUrl = "voldemort1.idi.ntnu.no:2181/voldemort";

    private ActiveNodeZKListener anzkl;
    private ZooKeeperHandler zkhandler;
    String zkURL = defaultUrl;
    private String activePath = "/active";
    private Cluster currentCluster;
    private List<String> childrenList;
    private boolean idle = false;




    public ActiveNodeWrapper(String zkURL) {
        this.zkURL = zkURL;
        anzkl = new ActiveNodeZKListener(this.zkURL, activePath);
        anzkl.addDataListener(this);
        childrenList = Lists.newArrayList();
        String currentClusterString = anzkl.getStringFromZooKeeper("/config/cluster.xml");
        currentCluster = new ClusterMapper().readCluster(new StringReader(currentClusterString));
        zkhandler = new ZooKeeperHandler(zkURL, anzkl.getZooKeeper());

        for (Node node : currentCluster.getNodes()) {
            System.out.println("node.id: " + node.getId());
            System.out.println(node.toString());
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
                            "usage: %s [zookeeperurl]\nDefaults to %s", ActiveNodeWrapper.class.getCanonicalName(), defaultUrl));
        } else {
            url = args[0];
        }

        ActiveNodeWrapper awn = new ActiveNodeWrapper(url);

        Autoscale as = new Autoscale("127.0.0.1", 7788);



        Thread worker = new Thread(awn);
        worker.start();



    }

    public Node locateNewChildAndHandOutId(){

        for (String child : this.childrenList){

            String id = zkhandler.getStringFromZooKeeper("/active/" + child);

            if (id.equals(VoldemortConfig.NEW_ACTIVE_NODE_STRING)){

                int newId = currentCluster.getNumberOfNodes();
                zkhandler.uploadAndUpdateFile("/active/" + child, String.valueOf(newId));

                Node newNode = new Node(newId, child, DEFAULT_HTTP_PORT, DEFAULT_SOCKET_PORT,DEFAULT_ADMIN_PORT,new ArrayList<Integer>());
                return newNode;
            }

        }
        return null;
    }

    @Override
    public synchronized void childrenList(List<String> children) {
        this.childrenList = children;
        Node newNode = locateNewChildAndHandOutId();
        if ( newNode != null ){
            String interimClusterxml = createInterimClusterXML(newNode);

            //upload cluster.xml
            zkhandler.uploadAndUpdateFile("/config/cluster.xml", interimClusterxml);


            //create node in nodes and upload server.properties
            String serverProp = createServerProperties(newNode);
            zkhandler.uploadAndUpdateFile("/config/nodes/" + newNode.getHost(), "");
            zkhandler.uploadAndUpdateFile("/config/nodes/" + newNode.getHost() + "/server.properties", serverProp);
        }
    }

    private String createInterimClusterXML(Node newNode) {
        Collection nodeCollection = currentCluster.getNodes();

        List<Node> nodeList;
        if(nodeCollection instanceof List){
            nodeList = (List)nodeCollection;
        } else {
            nodeList = new ArrayList(nodeCollection);
        }

        nodeList.add(newNode);

        Cluster interimCluseter = new Cluster("picluster",nodeList);
        String interimClusterXML = new ClusterMapper().writeCluster(interimCluseter);
        return interimClusterXML;


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
            this.currentCluster =  new ClusterMapper().readCluster(new StringReader(content));

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


