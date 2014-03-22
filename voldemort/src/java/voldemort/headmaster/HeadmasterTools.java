package voldemort.headmaster;

import org.apache.log4j.Logger;

import java.util.List;


public class HeadmasterTools {

    private static final Logger logger = Logger.getLogger(HeadmasterTools.class);

    public static String findSmallestChild(List<String> children ){

        long lowest_number = Long.MAX_VALUE;
        String winner = "";

        for (String child : children) {
            long sequenceNumber = new Long(child.split("_")[1]);
            if (sequenceNumber < lowest_number) {
                lowest_number = sequenceNumber;
                winner = child;
            }
        }
        return winner;
    }
}
