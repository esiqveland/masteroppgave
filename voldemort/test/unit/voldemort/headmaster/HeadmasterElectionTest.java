package voldemort.headmaster;

import com.google.common.collect.Lists;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooKeeper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class HeadmasterElectionTest {
    private String zkurl = "zkurl";

    private static String myHeadmaster = "headmaster_0000000071";
    private static List<String> headmasters = Lists.newArrayList("headmaster_0000000060", "headmaster_0000000071",
            "headmaster_0000000058");
    private static String EXAMPLE_CLUSTER =
            "<cluster>\n" +
                    "        <name>mycluster</name>\n" +
                    "        <server>\n" +
                    "                <id>0</id>\n" +
                    "                <host>192.168.0.104</host>\n" +
                    "                <http-port>8081</http-port>\n" +
                    "                <socket-port>6666</socket-port>\n" +
                    "                <partitions>0, 1</partitions>\n" +
                    "        </server>\n" +
                    "</cluster>\n";



    @Mock
    ZooKeeper zooKeeper;

    @Mock
    ActiveNodeZKListener activeNodeZKListener;

    Headmaster headmaster;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(activeNodeZKListener.getChildrenList("/active")).thenReturn(Lists.newArrayList("head", "feet"));

        when(activeNodeZKListener.getStringFromZooKeeper("/config/cluster.xml", true)).thenReturn(EXAMPLE_CLUSTER);

    }
    @Test
    public void leaderElectionTest(){
        when(activeNodeZKListener.uploadAndUpdateFileWithMode(
                Headmaster.HEADMASTER_ROOT_PATH + Headmaster.HEADMASTER_ELECTION_PATH, "", CreateMode.EPHEMERAL_SEQUENTIAL
        )).thenReturn("/headmaster/" + myHeadmaster);

        when(activeNodeZKListener.getChildrenList(Headmaster.HEADMASTER_ROOT_PATH)).thenReturn(headmasters);


        headmaster = new Headmaster(zkurl, activeNodeZKListener);
        headmaster.init();

        headmaster.nodeDeleted("/headmaster/");
        //Try one where this headmaster wins election:

        when(activeNodeZKListener.getChildrenList(Headmaster.HEADMASTER_ROOT_PATH)).thenReturn();

        headmaster.leaderElection();

        Assert.assertEquals(headmaster.getCurrentHeadmaster(), "headmaster_0000000058");

        verify(activeNodeZKListener,never()).setWatch(anyString());


        //Try one where this headmaster doesn't win the election:
        headmaster.setMyHeadmaster("headmaster_0000000060");

        headmaster.leaderElection();
        Assert.assertEquals(headmaster.getCurrentHeadmaster(), "headmaster_0000000058");

        verify(activeNodeZKListener,times(1)).setWatch(anyString());
    }


}
