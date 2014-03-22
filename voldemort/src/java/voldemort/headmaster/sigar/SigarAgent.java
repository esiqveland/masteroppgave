package voldemort.headmaster.sigar;

import org.apache.log4j.Logger;
import org.apache.zookeeper.WatchedEvent;
import voldemort.headmaster.ActiveNodeZKListener;
import voldemort.headmaster.Headmaster;
import voldemort.headmaster.HeadmasterTools;
import voldemort.tools.ZKDataListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.List;

public class SigarAgent implements ZKDataListener, Runnable{

    private static final Logger logger = Logger.getLogger(SigarAgent.class);
    DatagramSocket ds;
    ActiveNodeZKListener anzkl;
    String currentHeadmaster;
    InetAddress currentHeadmasterAddress;
    private int currentHeadmasterPort;
    private NodeStatus nodeStatus;
    private double monitor_cpu_usage, monitor_hdd_usage, monitor_ram_usage;
    private String myHostname;


    public SigarAgent(){
        nodeStatus = new NodeStatus();
        try {
            ds = new DatagramSocket();

        } catch (SocketException e) {
            e.printStackTrace();
        }
        anzkl = new ActiveNodeZKListener(Headmaster.defaultUrl);
        anzkl.addDataListener(this);

        try {
            myHostname = InetAddress.getLocalHost().getCanonicalHostName().toString();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
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
        logger.debug("Doing one monitor iteration:");

        //DO MONITOR STUFF

        monitorCPU();
        monitorRAM();
        monitorHDD();

        SigarMessageObject smo = new SigarMessageObject(monitor_cpu_usage,monitor_hdd_usage,monitor_ram_usage,myHostname);
        send(smo);

    }

    private void monitorCPU() {
        monitor_cpu_usage = nodeStatus.getCPUUsage();

    }

    private void monitorRAM() {
        monitor_ram_usage = nodeStatus.getMemoryUsage();

    }

    private void monitorHDD() {
        monitor_hdd_usage = nodeStatus.getDiskUsage();

    }

    private void send(SigarMessageObject smo){
        // Serialize to a byte array
        ByteArrayOutputStream bStream = new ByteArrayOutputStream();
        ObjectOutput oo = null;
        try {
            oo = new ObjectOutputStream(bStream);
            oo.writeObject(smo);
            oo.close();

            byte[] serializedMessage = bStream.toByteArray();

            ds.send(new DatagramPacket(serializedMessage, serializedMessage.length,currentHeadmasterAddress,currentHeadmasterPort));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void run() {
        synchronized (this) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        while (true) {
            if (hasHeadmaster()) {
                monitor();

                try {
                    Thread.sleep(5000);
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
        anzkl.getChildrenList(Headmaster.HEADMASTER_ROOT_PATH, true);
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
        String[] headmasterHostnameAndPort = anzkl.getStringFromZooKeeper(Headmaster.HEADMASTER_ROOT_PATH+"/"+currentHeadmaster).split(":");

        //Strip leading /
//        headmasterHostnameAndPort[0] = headmasterHostnameAndPort[0].substring(1);

        currentHeadmasterPort = Integer.parseInt(headmasterHostnameAndPort[1]);

        try {
            currentHeadmasterAddress = InetAddress.getByName(headmasterHostnameAndPort[0]);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        logger.debug("found new headmaster: " + currentHeadmaster);
        logger.debug("my headmasterhostname: " + currentHeadmasterAddress);
        logger.debug("my headmasterport: " + currentHeadmasterPort);

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

