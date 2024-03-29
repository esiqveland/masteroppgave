package voldemort.tools;

import voldemort.client.rebalance.RebalancePlan;
import voldemort.cluster.Cluster;
import voldemort.headmaster.ActiveNodeZKListener;
import voldemort.store.StoreDefinition;
import voldemort.utils.RebalanceUtils;
import voldemort.xml.ClusterMapper;
import voldemort.xml.StoreDefinitionsMapper;

import java.io.StringReader;
import java.util.List;

public class RebalancePlannerZK {
    private String zkUrl;
    private ActiveNodeZKListener anzkl;

    public RebalancePlannerZK(String zkUrl, ActiveNodeZKListener anzkl) {
        this.zkUrl = zkUrl;
        this.anzkl = anzkl;

    }

    public RebalancePlan createRebalancePlan() {
        String currentClusterXML = anzkl.getStringFromZooKeeper("/config/cluster.xml");
        Cluster currentCluster = new ClusterMapper().readCluster(new StringReader(currentClusterXML));

        String storesXML = anzkl.getStringFromZooKeeper("/config/stores.xml");
        List<StoreDefinition> currentStoreDefs = new StoreDefinitionsMapper().readStoreList(new StringReader(storesXML));

        String interimClusterXML = new String(currentClusterXML);
        Cluster interimCluster = new ClusterMapper().readCluster(new StringReader(interimClusterXML));

        String finalStoresXML = new String(storesXML);
        List<StoreDefinition> finalStoreDefs = new StoreDefinitionsMapper().readStoreList(new StringReader(finalStoresXML));

        RebalanceUtils.validateClusterStores(currentCluster, currentStoreDefs);
        RebalanceUtils.validateClusterStores(interimCluster, finalStoreDefs);
        RebalanceUtils.validateCurrentInterimCluster(currentCluster, interimCluster);

        // Optional administrivia args
        int attempts = Repartitioner.DEFAULT_REPARTITION_ATTEMPTS;
        String outputDir = null;
        outputDir = "config/maccluster/expansion/" + System.currentTimeMillis();


        // Optional repartitioning args
        boolean enableRandomSwaps = true;
        int randomSwapAttempts = Repartitioner.DEFAULT_RANDOM_SWAP_ATTEMPTS;
        int randomSwapSuccesses = Repartitioner.DEFAULT_RANDOM_SWAP_SUCCESSES;

        List<Integer> randomSwapZoneIds = Repartitioner.DEFAULT_RANDOM_SWAP_ZONE_IDS;
        boolean enableGreedySwaps = false;
        int greedySwapAttempts = Repartitioner.DEFAULT_GREEDY_SWAP_ATTEMPTS;
        int greedyMaxPartitionsPerNode = Repartitioner.DEFAULT_GREEDY_MAX_PARTITIONS_PER_NODE;
        int greedyMaxPartitionsPerZone = Repartitioner.DEFAULT_GREEDY_MAX_PARTITIONS_PER_ZONE;
        List<Integer> greedySwapZoneIds = Repartitioner.DEFAULT_GREEDY_SWAP_ZONE_IDS;
        int maxContiguousPartitionsPerZone = Repartitioner.DEFAULT_MAX_CONTIGUOUS_PARTITIONS;

        boolean disableNodeBalancing = false;
        boolean disableZoneBalancing = false;

        Cluster final_cluster;

        final_cluster = Repartitioner.repartition(currentCluster,
                currentStoreDefs,
                interimCluster,
                finalStoreDefs,
                outputDir,
                attempts,
                disableNodeBalancing,
                disableZoneBalancing,
                enableRandomSwaps,
                randomSwapAttempts,
                randomSwapSuccesses,
                randomSwapZoneIds,
                enableGreedySwaps,
                greedySwapAttempts,
                greedyMaxPartitionsPerNode,
                greedyMaxPartitionsPerZone,
                greedySwapZoneIds,
                maxContiguousPartitionsPerZone);


        int batch_size = Integer.MAX_VALUE;
        RebalancePlan plan = new RebalancePlan(currentCluster,currentStoreDefs,final_cluster,finalStoreDefs,batch_size,outputDir+"/planner");

        anzkl.uploadAndUpdateFile("/config/cluster_final.xml", new ClusterMapper().writeCluster(plan.getFinalCluster()));

        return plan;

    }




}
