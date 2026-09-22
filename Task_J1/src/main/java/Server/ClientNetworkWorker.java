package Server;

import General.ClientConnectionState;
import Storage.KeyStore;
import Storage.WorkQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.cert.CertificateEncodingException;
import java.util.Iterator;
import java.util.Set;

public class ClientNetworkWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientNetworkWorker.class);
    private static final int READ_BUFFER_SIZE = 4096;

    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final KeyStore keyStore;
    private final WorkQueues workQueues;
    private volatile boolean running = true;

    public ClientNetworkWorker(int port, KeyStore keyStore, WorkQueues workQueues) throws IOException {
        this.keyStore = keyStore;
        this.workQueues = workQueues;

        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.bind(new InetSocketAddress(port));
        this.serverChannel.configureBlocking(false);
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        log.info("ClientNetworkWorker listening on port {}", port);
    }

    public void shutdown() {
        running = false;
        selector.wakeup();
        log.debug("Shutdown requested, selector woken up");
    }

    @Override
    public void run() {
        log.info("ClientNetworkWorker started on thread '{}'", Thread.currentThread().getName());

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                selector.select();
            } catch (IOException e) {
                log.error("Selector.select() failed, stopping network worker", e);
                break;
            }

            if (!running) {
                break;
            }

            drainSendQueue();
            processSelectedKeys();
        }

        closeAllChannels();
        shutdownInternal();
        log.info("ClientNetworkWorker on thread '{}' stopped", Thread.currentThread().getName());
    }

    private void closeAllChannels() {
        for (SelectionKey key : selector.keys()) {
            if (key.channel() != serverChannel) {
                closeChannel(key);
            }
        }
    }

    private void drainSendQueue() {
        WorkQueues.SendTask task;
        while ((task = workQueues.pollSendTask()) != null) {
            handleSendTask(task);
        }
    }

    private void handleSendTask(WorkQueues.SendTask task) {
        SocketChannel channel = task.channel();
        SelectionKey key = channel.keyFor(selector);

        if (key == null || !key.isValid()) {
            log.debug("SendTask for a channel that is no longer registered, dropping");
            return;
        }

        if (task.error() != null) {
            log.warn("Generation failed, closing connection", task.error());
            closeChannel(key);
            return;
        }

        ClientConnectionState state = (ClientConnectionState) key.attachment();
        KeyStore.KeyCertBundle bundle = task.bundle();

        try {
            byte[] keyBytes = bundle.privateKey().getEncoded();
            byte[] certBytes = bundle.certificate().getEncoded();
            state.prepareResponse(keyBytes, certBytes);
            key.interestOps(SelectionKey.OP_WRITE);
        } catch (CertificateEncodingException e) {
            log.error("Failed to encode certificate for sending", e);
            closeChannel(key);
        }
    }

    private void processSelectedKeys() {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = selectedKeys.iterator();

        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();

            if (!key.isValid()) {
                continue;
            }

            try {
                if (key.isAcceptable()) {
                    handleAccept(key);
                } else if (key.isReadable()) {
                    handleRead(key);
                } else if (key.isWritable()) {
                    handleWrite(key);
                }
            } catch (IOException e) {
                log.debug("I/O error on client channel, closing", e);
                closeChannel(key);
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        SocketChannel client = serverChannel.accept();
        if (client == null) {
            return;
        }

        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, new ClientConnectionState());
        log.info("Accepted new connection from {}", client.getRemoteAddress());
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientConnectionState state = (ClientConnectionState) key.attachment();

        ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        int bytesRead = channel.read(buffer);

        if (bytesRead == -1) {
            log.debug("Client closed connection during read");
            closeChannel(key);
            return;
        }

        if (bytesRead == 0) {
            return;
        }

        if (state.getPhase() != ClientConnectionState.Phase.READING_NAME) {
            log.debug("Received unexpected data outside READING_NAME phase, ignoring");
            return;
        }

        buffer.flip();
        byte[] chunk = new byte[buffer.remaining()];
        buffer.get(chunk);
        state.appendNameBytes(chunk, 0, chunk.length);

        if (!state.isNameComplete()) {
            return;
        }

        String name = state.extractName();

        if (name.isEmpty()) {
            log.warn("Received empty name from client, closing connection without generating certificate");
            closeChannel(key);
            return;
        }

        log.info("Received name '{}' from client", name);

        KeyStore.LookupResult lookup = keyStore.getOrCreate(name);
        if (lookup.isNew()) {
            workQueues.submitGenerationTask(new WorkQueues.GenerationTask(name, lookup.future()));
        }

        lookup.future().whenComplete((bundle, ex) -> {
            workQueues.submitSendTask(new WorkQueues.SendTask(channel, bundle, ex));
            selector.wakeup();
        });

        key.interestOps(0);
    }

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientConnectionState state = (ClientConnectionState) key.attachment();

        channel.write(state.getOutboundBuffer());

        if (!state.getOutboundBuffer().hasRemaining()) {
            state.markResponseFullySent();
            log.debug("Response fully sent, closing connection");
            closeChannel(key);
        }
    }

    public int getPort() {
        return serverChannel.socket().getLocalPort();
    }

    private void closeChannel(SelectionKey key) {
        try {
            key.channel().close();
        } catch (IOException e) {
            log.debug("Error while closing channel", e);
        }
        key.cancel();
    }

    private void shutdownInternal() {
        try {
            selector.close();
        } catch (IOException e) {
            log.warn("Error while closing selector", e);
        }
        try {
            serverChannel.close();
        } catch (IOException e) {
            log.warn("Error while closing server channel", e);
        }
    }
}