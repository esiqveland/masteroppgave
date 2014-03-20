package voldemort.tools;

import org.apache.zookeeper.Watcher;

import java.util.List;

public interface ZKDataListener extends Watcher {

    public void childrenList(List<String> children);

    public void dataChanged(String path, String content);

}
