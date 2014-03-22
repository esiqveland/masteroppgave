package voldemort.headmaster;

import org.apache.log4j.Logger;
import org.hyperic.sigar.DirUsage;
import org.hyperic.sigar.Mem;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import voldemort.server.VoldemortConfig;

import java.text.DecimalFormat;

public class NodeStatus {
    private static final Logger logger = Logger.getLogger(NodeStatus.class);
    private static Sigar sigar;
    private static Long BYTES_TO_MB = 1024*1024L;
    private static double max_disk_space_used = 2000;

    public NodeStatus(){
        sigar = new Sigar();
    }

    public Double getMemoryUsage() {
        Double memUsed = 0.0;
        Mem mem;
        try {
            mem = sigar.getMem();

            memUsed = NodeStatus.doubleFormatted(mem.getUsedPercent());
        } catch (Exception e) {
            logger.error("Failed to get memory usage ", e);
        }
        return memUsed;
    }

    public Double getCPUUsage() {
        Double cpuUsed = 0.0;

        try {
            cpuUsed = NodeStatus.doubleFormatted(sigar.getCpuPerc().getCombined());
        } catch (SigarException e) {
            logger.error("Failed to retrieve CPU-usage ", e);
        }
        return cpuUsed;
    }

    public Double getDiskUsage() {
        Long spaceInBytes = 0L;

        try {
            DirUsage dirUsage;
            String dir = VoldemortConfig.VOLDEMORT_DATA_DIR;
            dirUsage = sigar.getDirUsage(dir);
            spaceInBytes = dirUsage.getDiskUsage();

        } catch (SigarException e) {
            logger.error("Failed to retrieve disk space used in megabytes ", e);
        }

        Double diskUsed = ((spaceInBytes / BYTES_TO_MB.doubleValue()) / max_disk_space_used) * 100;
        diskUsed = NodeStatus.doubleFormatted(diskUsed);
        return diskUsed;
    }

    public Long getDiskSpaceUsed() {
        Long space = 0L;
        Double diskSpace = 0.0;
        try {
            DirUsage dirUsage;
            String dir = VoldemortConfig.VOLDEMORT_DATA_DIR;
            dirUsage = sigar.getDirUsage(dir);
            space = (dirUsage.getDiskUsage() / BYTES_TO_MB);

            // For debug/logging purpose
            diskSpace = dirUsage.getDiskUsage() / BYTES_TO_MB.doubleValue();
            diskSpace = NodeStatus.doubleFormatted(diskSpace);

        } catch (SigarException e) {
            logger.error("Failed to retrieve disk space used in megabytes ", e);
        }
        return space;
    }

    private static Double doubleFormatted(Double val) {
        DecimalFormat twoDForm = new DecimalFormat("#.###");
        return Double.valueOf(twoDForm.format(val));
    }
}


