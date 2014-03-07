package voldemort.headmaster;


import com.google.common.collect.Lists;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import voldemort.cluster.Cluster;
import voldemort.cluster.Node;
import voldemort.store.metadata.MetadataStore;
import voldemort.tools.ActiveNodeZKListener;
import voldemort.tools.ZKDataListener;
import voldemort.tools.ZooKeeperHandler;
import voldemort.xml.ClusterMapper;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ActiveNodeWrapper implements Watcher, ZKDataListener {

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(ActiveNodeZKListener.class);

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


        }

    }

    public void isThereNewChildren(){
        for (String child : this.childrenList){
            System.out.println(child);
        }
    }


    @Override
    public void childrenList(List<String> children) {
        this.childrenList = children;
        isThereNewChildren();
    }

    @Override
    public void dataChanged(String path, String content) {
        logger.info("Path: " + path);
        if(path.equals("/config/cluster.xml")){
            this.currentCluster =  new ClusterMapper().readCluster(new StringReader(content));

        }
    }
}


