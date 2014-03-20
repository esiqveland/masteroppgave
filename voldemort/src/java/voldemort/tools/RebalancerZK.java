package voldemort.tools;

import voldemort.client.rebalance.RebalanceController;
import voldemort.client.rebalance.RebalancePlan;
import voldemort.cluster.Cluster;
import voldemort.cluster.Node;
import voldemort.store.StoreDefinition;
import voldemort.store.metadata.MetadataStore;
import voldemort.utils.RebalanceUtils;
import voldemort.xml.ClusterMapper;
import voldemort.xml.StoreDefinitionsMapper;

import java.io.StringReader;
import java.util.List;

public class RebalancerZK {

    private String bootstrapUrl;
    private String zkUrl;
    private ZooKeeperHandler zkHandler;

    //Rebalance parameters
    private int parallelism = 8;
    private long proxyPauseSec = 30;
    private String outputDir = "rebalance-out";


    public RebalancerZK(String zkUrl, String bootstrapUrl, ZooKeeperHandler zkHandler){
        this.bootstrapUrl = bootstrapUrl;
        this.zkUrl = zkUrl;
        this.zkHandler = zkHandler;


    }

    public void rebalance(){
        RebalanceController rebalanceController;
        rebalanceController = new RebalanceController(bootstrapUrl,
                parallelism,
                proxyPauseSec);

        //Get current cluster from voldemort nodes
        Cluster currentCluster = rebalanceController.getCurrentCluster();
        List<StoreDefinition> currentStoreDefs = rebalanceController.getCurrentStoreDefs();

        // If this test doesn't pass, something is wrong in prod!
        RebalanceUtils.validateClusterStores(currentCluster, currentStoreDefs);

        //Fetch configfiles from ZooKeeper
        String finalClusterString = zkHandler.getStringFromZooKeeper("/config/cluster_final.xml");
        Cluster finalCluster = new ClusterMapper().readCluster(new StringReader(finalClusterString));

        String originalClusterString = zkHandler.getStringFromZooKeeper("/config/cluster.xml");

        List<StoreDefinition> finalStoreDefs;
        String storesXML = zkHandler.getStringFromZooKeeper("/config/stores.xml");
        finalStoreDefs = new StoreDefinitionsMapper().readStoreList(new StringReader(storesXML));

        //Validate stores across clusters
        RebalanceUtils.validateClusterStores(finalCluster, finalStoreDefs);
        RebalanceUtils.validateCurrentFinalCluster(currentCluster, finalCluster);

        int batchSize = Integer.MAX_VALUE;

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
            zkHandler.uploadAndUpdateFile("/config/cluster.xml", originalClusterString);

            //RollBack state
            for (Node nodes : finalCluster.getNodes()){
                zkHandler.uploadAndUpdateFile("/config/nodes/"+nodes.getHost()+"/server.state", MetadataStore.VoldemortState.NORMAL_SERVER.toString());
            }
        } else {
            zkHandler.uploadAndUpdateFile("/config/cluster.xml",finalClusterString);
        }
    }

}


