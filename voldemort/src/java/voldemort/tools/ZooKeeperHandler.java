package voldemort.tools;

import com.google.common.collect.Lists;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import voldemort.headmaster.ActiveNodeZKListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class ZooKeeperHandler implements Watcher{

    private static final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(ActiveNodeZKListener.class);

    private ZooKeeper zk;
    private String zkURL;

    public ZooKeeperHandler(String zkURL) {
        this.zkURL = zkURL;
        setupZooKeeper();
    }

    public ZooKeeperHandler(String zkURL, ZooKeeper zooKeeper) {
        this(zkURL);
        this.zk = zooKeeper;
    }

    private void setupZooKeeper(){
        if(isAlive()) {
            return;
        }

        ZooKeeper zooKeeper = null;
        try {
            zooKeeper = new ZooKeeper(zkURL, 4000, this);
            this.zk = zooKeeper;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("zkurl failed to setup zookeeper");
        }
    }

    public String getStringFromZooKeeper(String path){
        Stat stat = new Stat();
        String s;
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

    public byte[] getFileData(String path) {
        try {
            File file = new File(path);
            if(file.isFile() && file.canRead()) {
                byte[] fileData = Files.readAllBytes(file.toPath());
                return fileData;
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("error reading file data");
        }
        return null;
    }

    public List<String> getChildren(String znode) {
        List<String> children = Lists.newArrayList();

        try {
            children = zk.getChildren(znode,null);
        } catch (InterruptedException | KeeperException e) {
            logger.error("Failed getting children on znode: "+znode, e);
        }
        return children;
    }


    public void uploadAndUpdateFile(String target, String content) {

        try {
            Stat stat = zk.exists(target, false);
            if(stat == null) {
                zk.create(target, content.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            } else {
                zk.setData(target, content.getBytes(), stat.getVersion());
            }
        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
        }
    }

    public String uploadAndUpdateFileWithMode(String target, String content, CreateMode mode){
        try {
            Stat stat = zk.exists(target, false);
            if(stat == null) {
                String zknodePath = zk.create(target, content.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, mode);
                return zknodePath;
            } else {
                zk.setData(target, content.getBytes(), stat.getVersion());
                return target;
            }
        } catch (InterruptedException | KeeperException e) {
            logger.error("invalid path", e);
            throw new RuntimeException(e);
        }

    }

    @Override
    public void process(WatchedEvent event) {

    }

    public boolean isAlive() {
        if(this.zk == null || !this.zk.getState().isAlive()) {
            return false;
        }
        return true;
    }


    public void setWatch(String target, Watcher watcher) {
        try {
            zk.exists(target,watcher);
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
