package Client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class ClientSocketChannel implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ClientSocketChannel.class);

    private final Socket socket;
    private final InputStream in;
    private final DataOutputStream out;

    public ClientSocketChannel(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.in = socket.getInputStream();
        this.out = new DataOutputStream(socket.getOutputStream());
        log.info("Connected to {}:{}", host, port);
    }

    public void send(byte[] data) throws IOException {
        out.write(data);
        out.flush();
        log.debug("Sent {} bytes", data.length);
    }

    public byte[] readUntilClosed() throws IOException {
        byte[] data = in.readAllBytes();
        log.debug("Read {} bytes until connection closed", data.length);
        return data;
    }

    @Override
    public void close() throws IOException {
        socket.close();
        log.debug("Connection closed");
    }
}