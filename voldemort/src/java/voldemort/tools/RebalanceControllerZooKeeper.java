package voldemort.tools;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import voldemort.client.rebalance.RebalanceController;
import voldemort.client.rebalance.RebalancePlan;
import voldemort.cluster.Cluster;
import voldemort.store.StoreDefinition;
import voldemort.utils.RebalanceUtils;
import voldemort.xml.ClusterMapper;
import voldemort.xml.StoreDefinitionsMapper;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/**
 * Created by Knut on 20/02/14.
 */
public class RebalanceControllerZooKeeper {


    public static void main(String[] args) throws Exception {

        boolean debug = true;
        // Bootstrap & fetch current cluster/stores
        String bootstrapURL = "tcp://192.168.0.210:6667";
        String zkURL = "192.168.0.210:2181";

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

        String finalClusterString = zkHandler.getStringFromZooKeeper("/knuttest/cluster_final.xml");
        Cluster finalCluster = new ClusterMapper().readCluster(new StringReader(finalClusterString));

        List<StoreDefinition> finalStoreDefs;
        String storesXML = zkHandler.getStringFromZooKeeper("/knuttest/stores.xml");
            finalStoreDefs = new StoreDefinitionsMapper().readStoreList(new StringReader(storesXML));

        //Validate stores across clusters
        RebalanceUtils.validateClusterStores(finalCluster, finalStoreDefs);
        RebalanceUtils.validateCurrentFinalCluster(currentCluster, finalCluster);

        int batchSize = Integer.MAX_VALUE;
        String outputDir = "rebalance-out";

        System.out.println("Storename: " + currentStoreDefs.get(0).getName());
        System.out.println("Final clustername: " + finalCluster.getName());
        System.out.println("final store def: " + finalStoreDefs.get(0).getName());

//        // Plan & execute rebalancing.
        rebalanceController.rebalance(new RebalancePlan(currentCluster,
                currentStoreDefs,
                finalCluster,
                finalStoreDefs,
                batchSize,
                outputDir));
    }




}


class ZooKeeperHandler{

    private ZooKeeper zk;
    private String zkURL;

    public ZooKeeperHandler(String zkURL) {
        this.zkURL = zkURL;


    }

    public void setupZooKeeper(){
        zk = null;
        try {
            zk = new ZooKeeper(zkURL, 3000, null);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("zkurl failed to setup zookeeper");
        }
    }

    public String getStringFromZooKeeper(String path){
        Stat stat = new Stat();
        String s = null;
        try {
            byte[] data = zk.getData(path, false, stat);
            s = new String(data);
        } catch (KeeperException e) {
            throw new RuntimeException(String.format("Error getting key from ZooKeeper: %s", path), e);
        } catch (InterruptedException e) {
            throw new RuntimeException(String.format("Error getting key from ZooKeeper: %s", path), e);
        }
        return s;
    }
}