package voldemort.examples;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class ZooKeeperFileWriter implements Watcher {

    private ZooKeeper zooKeeper;
    private String zkurl;
    private String targetfile;
    private String targetnode;
    private byte[] fileData;

    public ZooKeeperFileWriter(String zkurl, String targetnode, String targetfile) {
        this.zkurl = zkurl;
        this.targetnode = targetnode;
        this.targetfile = targetfile;

        setupZooKeeper();

        uploadAndUpdateFile();
    }

    private void uploadAndUpdateFile() {
        byte[] data = getFileData();
        try {
            Stat stat = zooKeeper.exists(targetnode, false);
            zooKeeper.setData(targetnode, data, stat.getVersion());
        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
        }
    }

    private void setupZooKeeper() {
        ZooKeeper zooKeeper = null;

        try {
            zooKeeper = new ZooKeeper(zkurl, 3000, this);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("zkurl failed to setup zookeeper");
        }
        this.zooKeeper = zooKeeper;
    }

    public static void main(String args[]) {
        String zkurl = null;
        String targetfile = null;
        String node = null;

        if (args.length >= 3) {
            zkurl = args[0];
            node = args[1];
            targetfile = args[2];
        } else {
            System.out.println("usage: " + args[0] + " [zookeeper url] [target znode] [file to upload]");
            System.exit(-1);
        }

        ZooKeeperFileWriter zkw = new ZooKeeperFileWriter(zkurl, node, targetfile);

    }

    @Override
    public void process(WatchedEvent event) {

    }

    public byte[] getFileData() {
        try {
            File file = new File(this.targetfile);
            if(file.isFile() && file.canRead()) {
                fileData = Files.readAllBytes(file.toPath());
                return fileData;
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("error reading file data");
        }
        return null;
    }
}
