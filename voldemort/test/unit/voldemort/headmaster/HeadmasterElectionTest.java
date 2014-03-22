package voldemort.headmaster;

import com.google.common.collect.Lists;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooKeeper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import voldemort.xml.ClusterMapper;

import java.util.List;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class HeadmasterElectionTest {
    private String zkurl = "zkurl";

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

    private static String myHeadmaster = "headmaster_0000000060";
    private static String myHostname = "ahostname";

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

        when(activeNodeZKListener.uploadAndUpdateFileWithMode(eq(headmaster.HEADMASTER_ROOT_PATH + headmaster.HEADMASTER_ELECTION_PATH),
                anyString(), eq(CreateMode.EPHEMERAL_SEQUENTIAL))).thenReturn(headmaster.HEADMASTER_ROOT_PATH+"/"+myHeadmaster);



        List<String> headmasters = Lists.newArrayList("headmaster_0000000060", "headmaster_0000000071", "headmaster_0000000072");
        when(activeNodeZKListener.getChildrenList(Headmaster.HEADMASTER_ROOT_PATH)).thenReturn(headmasters);
    }
    @Test
    public void leaderElection_winning_Test(){

        List<String> headmasters = Lists.newArrayList("headmaster_0000000060", "headmaster_0000000071", "headmaster_0000000072");

        when(activeNodeZKListener.getChildrenList(Headmaster.HEADMASTER_ROOT_PATH)).thenReturn(headmasters);

        headmaster = new Headmaster(zkurl,activeNodeZKListener);

        headmaster.reconnected();

        Assert.assertEquals(myHeadmaster, headmaster.getCurrentHeadmaster());

        String winnerZkPath = headmaster.HEADMASTER_ROOT_PATH+"/"+myHeadmaster;

        verify(activeNodeZKListener,never()).setWatch(winnerZkPath);
        verify(activeNodeZKListener,times(1)).setWatch(headmaster.HEADMASTER_ROOT_PATH+headmaster.HEADMASTER_REBALANCE_TOKEN);
        verify(activeNodeZKListener,times(1)).getStringFromZooKeeper("/config/cluster.xml", true);
    }

    @Test
    public void leaderElection_losing_Test(){

        List<String> headmasters = Lists.newArrayList("headmaster_0000000060", "headmaster_0000000055", "headmaster_0000000072");

        when(activeNodeZKListener.getChildrenList(Headmaster.HEADMASTER_ROOT_PATH)).thenReturn(headmasters);

        headmaster = new Headmaster(zkurl,activeNodeZKListener);

        headmaster.reconnected();

        Assert.assertNotSame(myHeadmaster,headmaster.getCurrentHeadmaster());

        String winnerZkPath = headmaster.HEADMASTER_ROOT_PATH+"/"+headmaster.getCurrentHeadmaster();

        verify(activeNodeZKListener,times(1)).setWatch(winnerZkPath);
        verify(activeNodeZKListener,times(0)).setWatch(headmaster.HEADMASTER_ROOT_PATH+headmaster.HEADMASTER_REBALANCE_TOKEN);
        verify(activeNodeZKListener,times(0)).getStringFromZooKeeper("/config/cluster.xml", true);
    }

    @Test
    public void registerAsHeadmasterTest() {
        headmaster = new Headmaster(zkurl,activeNodeZKListener);
        headmaster.reconnected();

        Assert.assertEquals(myHeadmaster, headmaster.getMyHeadmaster());
    }

    @Test
    public void beHeadmasterTest(){
        headmaster = new Headmaster(zkurl,activeNodeZKListener);
        headmaster.reconnected();

        verify(activeNodeZKListener,times(1)).setWatch(headmaster.HEADMASTER_ROOT_PATH+headmaster.HEADMASTER_REBALANCE_TOKEN);
        verify(activeNodeZKListener,times(1)).getStringFromZooKeeper("/config/cluster.xml", true);

    }


}
