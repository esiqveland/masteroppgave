package voldemort.tools;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Created by Knut on 06/03/14.
 */
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

    void uploadAndUpdateFile(String target, String content) {

        try {
            Stat stat = zk.exists(target, false);
            if(stat == null) {
                zk.create(target, content.getBytes(), null, CreateMode.PERSISTENT);
            } else {
                zk.setData(target, content.getBytes(), stat.getVersion());
            }
        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
        }
    }
}
