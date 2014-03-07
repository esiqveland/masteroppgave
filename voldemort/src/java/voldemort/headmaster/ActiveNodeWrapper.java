package voldemort.headmaster;


import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import voldemort.tools.ActiveNodeZKListener;

import java.util.logging.Logger;

public class ActiveNodeWrapper implements Watcher {

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(ActiveNodeZKListener.class);

    private ActiveNodeZKListener anzkl;
    String zkURL = "voldemort1.idi.ntnu.no:2181";
    private String activePath = "/active";




    public ActiveNodeWrapper(){
        anzkl = new ActiveNodeZKListener(zkURL,activePath);
        anzkl.addWatcher(this);
    }

    @Override
    public void process(WatchedEvent event) {
        logger.debug("Event: " + event.getType() + " path: " + event.getPath());

    }

    public static void main(String args[]){
        ActiveNodeWrapper awn = new ActiveNodeWrapper();

        while(1==1){


        }

    }
}


