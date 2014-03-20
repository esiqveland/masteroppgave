package voldemort.headmaster;

import org.apache.zookeeper.ZooKeeper;
import org.junit.Before;
import org.mockito.Mock;

public class HeadmasterTest {

    @Mock
    ZooKeeper zooKeeper;

    @Mock
    ActiveNodeZKListener activeNodeZKListener;

    @Before
    public void setUp() {

    }
}
