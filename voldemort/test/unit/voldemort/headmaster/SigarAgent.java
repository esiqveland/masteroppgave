package voldemort.headmaster;


import org.apache.log4j.Logger;
import org.apache.zookeeper.WatchedEvent;
import voldemort.tools.ZKDataListener;

import java.io.IOException;
import java.net.*;
import java.util.List;

public class SigarAgent implements ZKDataListener, Runnable{


    private static final Logger logger = Logger.getLogger(SigarAgent.class);
    DatagramSocket ds;
    InetAddress headmasterAddress;
    ActiveNodeZKListener anzkl;
    String currentHeadmaster;
    InetAddress currentHeadmasterAddress;
    private boolean running;

    public void waitForNewHeadmaster(){
        while(currentHeadmaster == null){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }


    public SigarAgent(){
        running = true;
        try {
            ds = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        anzkl = new ActiveNodeZKListener(Headmaster.defaultUrl);

    }

    @Override
    public void childrenList(String path) {
        //New headmaster arrived or just another node
        if(!path.equals(Headmaster.HEADMASTER_ROOT_PATH)){
            //This message is for someone else
            return;
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

    }

    @Override
    public void process(WatchedEvent event) {

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
        while(running){
            if(hasHeadmaster()){
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

    private void waitForHeadmaster(){
        //setup watch for children changed
        anzkl.addDataListener(this);
        anzkl.getChildrenList(Headmaster.HEADMASTER_ROOT_PATH,true);

        while(currentHeadmaster == null){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void findHeadmaster(){
        List<String> headMasters = anzkl.getChildrenList(Headmaster.HEADMASTER_ROOT_PATH,true);

        if(headMasters.isEmpty()){
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

        anzkl.setWatch(Headmaster.HEADMASTER_ROOT_PATH+"/"+currentHeadmaster);

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

