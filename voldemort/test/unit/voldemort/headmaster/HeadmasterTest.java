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
import voldemort.xml.MappingException;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HeadmasterTest {

    private String zkurl = "zkurl";

    private static String child = "myownheadmaster_00000000001";
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

        List<String> headmasters_children = new ArrayList<String>();
        headmasters_children.add(child);

        when(activeNodeZKListener.getChildrenList("/active")).thenReturn(Lists.newArrayList("head", "feet"));

        when(activeNodeZKListener.getStringFromZooKeeper("/config/cluster.xml", true)).thenReturn(EXAMPLE_CLUSTER);

        when(activeNodeZKListener.uploadAndUpdateFileWithMode(
                Headmaster.HEADMASTER_ROOT_PATH + Headmaster.HEADMASTER_ELECTION_PATH, "", CreateMode.EPHEMERAL_SEQUENTIAL
        )).thenReturn("/headmaster/" + child);

        when(activeNodeZKListener.getChildrenList(Headmaster.HEADMASTER_ROOT_PATH)).thenReturn(headmasters_children);


        headmaster = new Headmaster(zkurl, activeNodeZKListener);
    }



    @Test(expected = MappingException.class)
    public void whenDataChangedToInvalidClusterThrowException() {
        String path = "/config/cluster.xml";

        when(activeNodeZKListener.getStringFromZooKeeper(path, true)).thenReturn("<info>");

        headmaster.dataChanged(path);

    }

    @Test
    public void whenDataChangedTestIfWatchIsRest() {
        String path = "/config/cluster.xml";

        when(activeNodeZKListener.getStringFromZooKeeper(path, true)).thenReturn(EXAMPLE_CLUSTER);

        headmaster.dataChanged(path);

        verify(activeNodeZKListener, times(1)).getStringFromZooKeeper(path, true);

    }


}
