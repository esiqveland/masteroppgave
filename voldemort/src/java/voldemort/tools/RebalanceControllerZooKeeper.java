package voldemort.tools;

import voldemort.client.rebalance.RebalanceController;
import voldemort.client.rebalance.RebalancePlan;
import voldemort.cluster.Cluster;
import voldemort.store.StoreDefinition;
import voldemort.utils.RebalanceUtils;
import voldemort.xml.ClusterMapper;
import voldemort.xml.StoreDefinitionsMapper;

import java.io.StringReader;
import java.util.List;

public class RebalanceControllerZooKeeper {


    public static void main(String[] args) throws Exception {

        boolean debug = true;
        // Bootstrap & fetch current cluster/stores
        String bootstrapURL = "tcp://192.168.0.210:6667";
        String zkURL = "192.168.0.210:2181/voldemort/config";

        //Rebalance parameters
        int parallelism = 8;
        long proxyPauseSec = 30;

        RebalanceController rebalanceController;
        rebalanceController = new RebalanceController(bootstrapURL,
                parallelism,
                proxyPauseSec);

        //Get current cluster from voldemort nodes
        Cluster currentCluster = rebalanceController.getCurrentCluster();
        List<StoreDefinition> currentStoreDefs = rebalanceController.getCurrentStoreDefs();

        // If this test doesn't pass, something is wrong in prod!
        RebalanceUtils.validateClusterStores(currentCluster, currentStoreDefs);


        ZooKeeperHandler zkHandler = new ZooKeeperHandler(zkURL);
        zkHandler.setupZooKeeper();

        String finalClusterString = zkHandler.getStringFromZooKeeper("/cluster_final.xml");
        Cluster finalCluster = new ClusterMapper().readCluster(new StringReader(finalClusterString));

        String interrimClusterString = zkHandler.getStringFromZooKeeper("/interrim_cluster.xml");
        String originalClusterString = zkHandler.getStringFromZooKeeper("/cluster.xml");

        List<StoreDefinition> finalStoreDefs;
        String storesXML = zkHandler.getStringFromZooKeeper("/stores.xml");
            finalStoreDefs = new StoreDefinitionsMapper().readStoreList(new StringReader(storesXML));

        //Validate stores across clusters
        RebalanceUtils.validateClusterStores(finalCluster, finalStoreDefs);
        RebalanceUtils.validateCurrentFinalCluster(currentCluster, finalCluster);

        int batchSize = Integer.MAX_VALUE;
        String outputDir = "rebalance-out";

        System.out.println("Storename: " + currentStoreDefs.get(0).getName());
        System.out.println("Final clustername: " + finalCluster.getName());
        System.out.println("final store def: " + finalStoreDefs.get(0).getName());


        //Add new cluster.xml
        zkHandler.uploadAndUpdateFile("/cluster.xml",finalClusterString);


        // Plan & execute rebalancing.
        boolean failure = false;
        try{
            rebalanceController.rebalance(new RebalancePlan(currentCluster,
                currentStoreDefs,
                finalCluster,
                finalStoreDefs,
                batchSize,
                outputDir));
        } catch (Exception e){
            failure = true;
        }

        if(failure){
            //Issue rollback of cluster.xml
            zkHandler.uploadAndUpdateFile("/cluster.xml", originalClusterString);
        }


        //Write cluster_final.xml to zookeeper as cluster.xml


        //Uncomment to reset cluster.xml to interrim_cluster
//        zkHandler.uploadAndUpdateFile("/cluster.xml",interrimClusterString);
    }







}


