package voldemort.headmaster;

import com.google.common.collect.Lists;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooKeeper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.matchers.Any;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HeadmasterTest {

    private String zkurl = "zkurl";

    @Mock
    ZooKeeper zooKeeper;

    @Mock
    ActiveNodeZKListener activeNodeZKListener;

    Headmaster headmaster;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(activeNodeZKListener.getChildrenList("/active")).thenReturn(Lists.newArrayList("head", "feet"));

        headmaster = new Headmaster(zkurl, activeNodeZKListener);
        //headmaster.init();

    }

    @Test
    public void registerAsHeadmaster() {
        String child = "myownheadmaster";

        when(activeNodeZKListener.uploadAndUpdateFileWithMode(
                Headmaster.HEADMASTER_ROOT_PATH + Headmaster.HEADMASTER_ELECTION_PATH, "", CreateMode.EPHEMERAL_SEQUENTIAL
        )).thenReturn("/headmaster/" + child);

        headmaster.registerAsHeadmaster();

        Assert.assertEquals(child, headmaster.getMyHeadmaster());
    }

    @Test
    public void whenDataChangedTestIfWatchIsReset() {
        String path = "/config/cluster.xml";

        when(activeNodeZKListener.getStringFromZooKeeper(path, true)).thenReturn("<info>");

        headmaster.dataChanged(path);

        verify(activeNodeZKListener, times(1)).getStringFromZooKeeper(path, true);
    }


}
