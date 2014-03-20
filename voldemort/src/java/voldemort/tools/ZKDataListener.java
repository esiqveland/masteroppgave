package voldemort.tools;

import org.apache.zookeeper.Watcher;

import java.util.List;

public interface ZKDataListener extends Watcher {

    public void childrenList(String path);

    public void dataChanged(String path);

    public void nodeDeleted(String path);

    public void reconnected();
}
