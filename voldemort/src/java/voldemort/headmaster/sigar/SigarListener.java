package voldemort.headmaster.sigar;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import voldemort.headmaster.Headmaster;

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
        this.listenPort = listenPort;
        setupSocket();
    }

    private void setupSocket() {
        try {
            socket = new DatagramSocket(this.listenPort);

            this.setRunning(true);
            logger.debug("running ok");
        } catch (SocketException e) {
            logger.error("Error setting up socket on port: {}", this.listenPort, e);
        }
    }


    @Override
    public void run() {
        byte[] data = new byte[1024];
        while (isRunning()) {
            DatagramPacket packet = new DatagramPacket(data, data.length);
            logger.debug("Listening for packet");
            try {
                socket.receive(packet);
                byte[] recv = packet.getData();
                ByteArrayInputStream in = new ByteArrayInputStream(recv);
                ObjectInputStream is = new ObjectInputStream(in);
                try {
                    SigarMessageObject message = (SigarMessageObject) is.readObject();
                    logger.debug("Sigar message: {}", message);
                } catch (ClassCastException | ClassNotFoundException e) {
                    logger.error("error converting message data from {}", packet.getAddress().getCanonicalHostName(), e);
                }

            } catch (IOException e) {
                logger.error("Error receiving packet", e);
                setRunning(false);
                if(socket.isClosed()) {

                }
            }
        }
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public boolean isRunning() {
        return running;
    }

    public static void main(String[] args) {
        SigarListener sigarListener = new SigarListener(Headmaster.HEADMASTER_SIGAR_LISTENER_PORT);

        Thread t = new Thread(sigarListener);
        t.start();

    }
}
