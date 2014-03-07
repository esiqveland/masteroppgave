package voldemort.cluster;

import java.util.List;

public class MutableCluster extends Cluster{

    public MutableCluster(String name, List<Node> nodes){
        super(name,nodes);
    }

    public void addNodeToCluster(Node node){
        this.getNodes().add(node);
    }

    public static MutableCluster MutableClusterFromCluster(Cluster cluster){
        return new MutableCluster(cluster.getName(),(List<Node>)cluster.getNodes());
    }
}

