package voldemort.headmaster;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class SigarListener implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SigarListener.class);

    private int listenPort;
    private DatagramSocket socket;
    private boolean running;

    public SigarListener(int listenPort) {
        listenPort = listenPort;

        try {
            socket = new DatagramSocket(this.listenPort);

            this.setRunning(true);
        } catch (SocketException e) {
            logger.error("Error setting up socket on port: {}", this.listenPort, e);
        }
    }


    @Override
    public void run() {
        byte[] data = new byte[1024];
        while (isRunning()) {
            DatagramPacket packet = new DatagramPacket(data, data.length);
            try {
                socket.receive(packet);
                byte[] recv = packet.getData();
                ByteArrayInputStream in = new ByteArrayInputStream(recv);
                ObjectInputStream is = new ObjectInputStream(in);
                try {
                    SigarMessageObject message = (SigarMessageObject) is.readObject();
                    logger.debug("sigar message: {}", message);
                } catch (ClassCastException | ClassNotFoundException e) {
                    logger.error("error converting message data from {}", packet.getAddress().getCanonicalHostName(), e);
                }

            } catch (IOException e) {
                logger.error("Error receiving packet", e);
            }


        }

    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public boolean isRunning() {
        return running;
    }
}
