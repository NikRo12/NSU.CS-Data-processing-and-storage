package Server;

import General.CertificateIssuer;
import Storage.KeyStore;
import Storage.WorkQueues;
import Client.ClientOptions;
import Client.KeyClient;
import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServerRobustnessTest {

    private static final int TEST_KEY_SIZE = 2048;
    private static final int WORKER_COUNT = 2;

    private KeyStore keyStore;
    private WorkQueues workQueues;
    private ClientNetworkWorker networkWorker;
    private Thread networkThread;
    private final List<Thread> generationThreads = new ArrayList<>();
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator caGen = KeyPairGenerator.getInstance("RSA");
        caGen.initialize(TEST_KEY_SIZE);
        KeyPair caKeyPair = caGen.generateKeyPair();

        CertificateIssuer certificateIssuer = new CertificateIssuer(caKeyPair.getPrivate(), new X500Name("CN=Test-CA"));

        keyStore = new KeyStore();
        workQueues = new WorkQueues();

        for (int i = 0; i < WORKER_COUNT; i++) {
            KeyGenerationWorker worker = new KeyGenerationWorker(workQueues, keyStore, certificateIssuer, TEST_KEY_SIZE);
            Thread thread = new Thread(worker, "robustness-worker-" + i);
            generationThreads.add(thread);
            thread.start();
        }

        networkWorker = new ClientNetworkWorker(0, keyStore, workQueues);
        networkThread = new Thread(networkWorker, "robustness-network-worker");
        networkThread.start();

        Thread.sleep(50);
        port = networkWorker.getPort();
    }

    @AfterEach
    void tearDown() throws Exception {
        networkWorker.shutdown();
        generationThreads.forEach(Thread::interrupt);
        networkThread.join(5000);
        for (Thread t : generationThreads) {
            t.join(5000);
        }
    }

    @Test
    void emptyName_serverClosesConnectionWithoutResponse() throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(new byte[]{0});
            out.flush();

            InputStream in = socket.getInputStream();
            int firstByte = in.read();

            assertEquals(-1, firstByte, "Server must close the connection without sending a response for an empty name");
        }
    }

    @Test
    void emptyName_doesNotCrashServer_subsequentRequestWorks(@TempDir Path tempDir) throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            socket.getOutputStream().write(new byte[]{0});
            socket.getOutputStream().flush();
        }

        Thread.sleep(200);

        String name = "after-empty-" + UUID.randomUUID();
        ClientOptions options = new ClientOptions("localhost", port, name, 0, false, tempDir);

        assertDoesNotThrow(() -> new KeyClient().run(options));
        assertTrue(java.nio.file.Files.exists(tempDir.resolve(name + ".crt")));
    }

    @Test
    void garbageWithoutTerminator_thenClientDisconnects_doesNotCrashServer(@TempDir Path tempDir) throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
            out.flush();
        }

        Thread.sleep(200);

        String name = "after-garbage-" + UUID.randomUUID();
        ClientOptions options = new ClientOptions("localhost", port, name, 0, false, tempDir);

        assertDoesNotThrow(() -> new KeyClient().run(options));
        assertTrue(java.nio.file.Files.exists(tempDir.resolve(name + ".crt")));
    }

    @Test
    void nonAsciiBytesWithTerminator_doesNotCrashServer() throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            byte[] nonAscii = {(byte) 200, (byte) 201, (byte) 202, 0};
            out.write(nonAscii);
            out.flush();

            socket.setSoTimeout(15000);
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[16];
            int read = in.read(buffer);

            assertTrue(read == -1 || read > 0, "Server must either respond or gracefully close, not hang or crash");
        }

        Thread.sleep(100);

        try (Socket probe = new Socket("localhost", port)) {
            assertTrue(probe.isConnected(), "Server must still be accepting connections after non-ASCII input");
        }
    }

    @Test
    void clientDisconnectsImmediatelyAfterConnect_doesNotCrashServer(@TempDir Path tempDir) throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            // соединились и сразу закрылись, ничего не отправив
        }

        Thread.sleep(200);

        String name = "after-immediate-close-" + UUID.randomUUID();
        ClientOptions options = new ClientOptions("localhost", port, name, 0, false, tempDir);

        assertDoesNotThrow(() -> new KeyClient().run(options));
        assertTrue(java.nio.file.Files.exists(tempDir.resolve(name + ".crt")));
    }

    @Test
    void portAlreadyInUse_throwsIOException() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            int occupiedPort = occupied.getLocalPort();

            assertThrows(IOException.class, () ->
                    new ClientNetworkWorker(occupiedPort, keyStore, workQueues));
        }
    }
}