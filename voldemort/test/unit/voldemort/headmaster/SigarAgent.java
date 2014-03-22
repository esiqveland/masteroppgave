package voldemort.headmaster;

import org.apache.log4j.Logger;
import org.apache.zookeeper.WatchedEvent;
import voldemort.tools.ZKDataListener;

import java.net.*;
import java.util.List;

public class SigarAgent implements ZKDataListener, Runnable{

    private static final Logger logger = Logger.getLogger(SigarAgent.class);
    DatagramSocket ds;
    ActiveNodeZKListener anzkl;
    String currentHeadmaster;
    InetAddress currentHeadmasterAddress;


    public SigarAgent(){
        try {
            ds = new DatagramSocket();

        } catch (SocketException e) {
            e.printStackTrace();
        }
        anzkl = new ActiveNodeZKListener(Headmaster.defaultUrl);
        anzkl.addDataListener(this);
    }


    @Override
    public void childrenList(String path) {
        logger.debug("Got event of change in headmaster children.");
        //New headmaster arrived or just another node
        if(!path.equals(Headmaster.HEADMASTER_ROOT_PATH)){
            //This message is for someone else
            return;
        }
        synchronized (this){
            notifyAll();
        }
        findHeadmaster();
    }

    @Override
    public void dataChanged(String path) {

    }

    @Override
    public void nodeDeleted(String path) {


    }

    @Override
    public void reconnected() {
        synchronized (this){
            notifyAll();
        }


    }

    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Event.KeeperState.Expired) {

        }
    }


    private boolean hasHeadmaster(){
        if(currentHeadmaster != null){
            return true;
        }

        return false;
    }

    private void monitor(){

    }

    @Override
    public void run() {
        synchronized (this) {
            while (true) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (hasHeadmaster()) {
                    monitor();

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    findHeadmaster();
                }

            }
        }
    }

    private void waitForHeadmaster(){
        //setup watch for children changed
        anzkl.getChildrenList(Headmaster.HEADMASTER_ROOT_PATH,true);
    }

    private void findHeadmaster(){
        logger.debug("Finding a new headmaster");
        List<String> headMasters = anzkl.getChildrenList(Headmaster.HEADMASTER_ROOT_PATH,true);

        if(headMasters.isEmpty()){
            logger.debug("Found no headmasters... Going to wait...");
            currentHeadmaster = null;
            waitForHeadmaster();
            return;
        }

        currentHeadmaster = HeadmasterTools.findSmallestChild(headMasters);

        //Get hostname:
        String headmasterHostname = anzkl.getStringFromZooKeeper(Headmaster.HEADMASTER_ROOT_PATH+"/"+currentHeadmaster);

        try {
            currentHeadmasterAddress = InetAddress.getByName(headmasterHostname);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        logger.debug("found new headmaster: " + currentHeadmaster);
        logger.debug("my headmasterhostname: " + currentHeadmasterAddress);

//        anzkl.setWatch(Headmaster.HEADMASTER_ROOT_PATH+"/"+currentHeadmaster);

    }

    public static void main(String []args){

        Thread t = new Thread(new SigarAgent());
        t.start();

        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }

}

