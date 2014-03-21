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
import static org.mockito.Mockito.*;

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
    public void leaderElectionTest(){

        //Try one where this headmaster wins election:
        headmaster.setMyHeadmaster("headmaster_0000000058");

        when(activeNodeZKListener.getChildrenList(Headmaster.HEADMASTER_ROOT_PATH)).thenReturn(
                Lists.newArrayList("headmaster_0000000060","headmaster_0000000071",
                        "headmaster_0000000058"));

        headmaster.leaderElection();

        Assert.assertEquals(headmaster.getCurrentHeadmaster(), "headmaster_0000000058");

        //Verify that there is a watch set
        verify(activeNodeZKListener,never()).setWatch(anyString());


        //Try one where this headmaster doesn't win the election:
        headmaster.setMyHeadmaster("headmaster_0000000060");

        headmaster.leaderElection();
        Assert.assertEquals(headmaster.getCurrentHeadmaster(), "headmaster_0000000058");

        verify(activeNodeZKListener,times(1)).setWatch(anyString());
    }



    @Test
    public void whenDataChangedTestIfWatchIsReset() {
        String path = "/config/cluster.xml";

        when(activeNodeZKListener.getStringFromZooKeeper(path, true)).thenReturn("<info>");

        headmaster.dataChanged(path);

        verify(activeNodeZKListener, times(1)).getStringFromZooKeeper(path, true);
    }


}
