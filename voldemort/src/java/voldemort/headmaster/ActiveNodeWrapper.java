package voldemort.headmaster;


import com.google.common.collect.Lists;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import voldemort.cluster.Cluster;
import voldemort.cluster.MutableCluster;
import voldemort.cluster.Node;
import voldemort.server.VoldemortConfig;
import voldemort.store.metadata.MetadataStore;
import voldemort.tools.ActiveNodeZKListener;
import voldemort.tools.ZKDataListener;
import voldemort.tools.ZooKeeperHandler;
import voldemort.xml.ClusterMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.logging.Logger;

public class ActiveNodeWrapper implements Watcher, ZKDataListener {

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(ActiveNodeZKListener.class);
    public static final int DEFAULT_HTTP_PORT = 6881;
    public static final int DEFAULT_ADMIN_PORT = 6667;
    public static final int DEFAULT_SOCKET_PORT = 6666;



    private ActiveNodeZKListener anzkl;
    private ZooKeeperHandler zkhandler;
    String zkURL = "voldemort1.idi.ntnu.no:2181/voldemort";
    private String activePath = "/active";
    private Cluster currentCluster;
    private List<String> childrenList;




    public ActiveNodeWrapper(){
        anzkl = new ActiveNodeZKListener(zkURL,activePath);
        anzkl.addDataListener(this);
        childrenList = Lists.newArrayList();
        String currentClusterString = anzkl.getStringFromZooKeeper("/config/cluster.xml");
        currentCluster = new ClusterMapper().readCluster(new StringReader(currentClusterString));
        zkhandler = new ZooKeeperHandler(zkURL);
        zkhandler.setupZooKeeper();

        for (Node node : currentCluster.getNodes()){
            System.out.println("nodeid: " + node.getId());
            System.out.println(node.toString());
        }


    }

    @Override
    public void process(WatchedEvent event) {
        logger.info("Event: " + event.getType() + " path: " + event.getPath());
        if (event.getType() == Event.EventType.NodeChildrenChanged){

        }



    }

    public static void main(String args[]){
        ActiveNodeWrapper awn = new ActiveNodeWrapper();

        while(1==1){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                // handle the exception...
                // For example consider calling Thread.currentThread().interrupt(); here.
            }

        }

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
}


