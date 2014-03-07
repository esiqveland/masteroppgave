package voldemort.headmaster;


import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import voldemort.tools.ActiveNodeZKListener;

public class ActiveNodeWrapper implements Watcher {

    private ActiveNodeZKListener anzkl;
    String zkURL = "voldemort1.idi.ntnu.no:2181";
    private String activePath = "/active";




    public ActiveNodeWrapper(){
        anzkl = new ActiveNodeZKListener(zkURL,activePath);
        anzkl.addWatcher(this);
    }

    @Override
    public void process(WatchedEvent event) {
        System.out.println("There is a new node in active!");

        event.getType().toString();

    }

    public static void main(String args[]){
        ActiveNodeWrapper awn = new ActiveNodeWrapper();

        while(1==1){

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                //Handle exception
            }

        }

    }
}


