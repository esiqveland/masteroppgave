package voldemort.cluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MutableCluster extends Cluster{

    private List<Node> mutableNodeList;
    private Collection tempNodeCol;

    public MutableCluster(String name, List<Node> nodes){
        super(name,nodes);
        Collection tempCol = this.getNodes();

        if(tempCol instanceof List){
            mutableNodeList = (List)tempCol;
        } else {
            mutableNodeList = new ArrayList(tempCol);
        }
    }

    public void addNodeToCluster(Node node){
        mutableNodeList.add(node);

    }

    public static MutableCluster MutableClusterFromCluster(Cluster cluster){
        return new MutableCluster(cluster.getName(),(List<Node>)cluster.getNodes());
    }

    public List<Node> getMutableNodeList(){
        return this.mutableNodeList;
    }
}

