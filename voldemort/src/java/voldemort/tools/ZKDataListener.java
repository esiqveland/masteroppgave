package voldemort.tools;

import java.util.List;

public interface ZKDataListener {

    public void childrenList(List<String> children);

    public void dataChanged(String path, String content);
}
