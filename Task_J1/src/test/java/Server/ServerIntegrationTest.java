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

import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ServerIntegrationTest {

    private static final int TEST_KEY_SIZE = 2048;
    private static final int WORKER_COUNT = 2;

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

        KeyStore keyStore = new KeyStore();
        WorkQueues workQueues = new WorkQueues();

        for (int i = 0; i < WORKER_COUNT; i++) {
            KeyGenerationWorker worker = new KeyGenerationWorker(workQueues, keyStore, certificateIssuer, TEST_KEY_SIZE);
            Thread thread = new Thread(worker, "test-generation-worker-" + i);
            generationThreads.add(thread);
            thread.start();
        }

        networkWorker = new ClientNetworkWorker(0, keyStore, workQueues);
        networkThread = new Thread(networkWorker, "test-network-worker");
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

    private ClientOptions optionsFor(String name, Path outputDir) {
        return new ClientOptions("localhost", port, name, 0, false, outputDir);
    }

    private X509Certificate readCertificate(Path crtFile) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        try (var in = Files.newInputStream(crtFile)) {
            return (X509Certificate) factory.generateCertificate(in);
        }
    }

    @Test
    void basicRequestResponse_savesValidKeyAndCertificate(@TempDir Path tempDir) throws Exception {
        String name = "alice-" + UUID.randomUUID();

        new KeyClient().run(optionsFor(name, tempDir));

        Path keyFile = tempDir.resolve(name + ".key");
        Path crtFile = tempDir.resolve(name + ".crt");

        assertTrue(Files.exists(keyFile));
        assertTrue(Files.exists(crtFile));

        X509Certificate cert = readCertificate(crtFile);
        assertTrue(cert.getSubjectX500Principal().getName().contains("CN=" + name));
        assertDoesNotThrow(() -> cert.checkValidity());
    }

    @Test
    void repeatedRequest_returnsSameCertificate(@TempDir Path tempDir) throws Exception {
        String name = "bob-" + UUID.randomUUID();

        new KeyClient().run(optionsFor(name, tempDir));
        X509Certificate first = readCertificate(tempDir.resolve(name + ".crt"));

        Path secondDir = tempDir.resolve("second");
        Files.createDirectories(secondDir);
        new KeyClient().run(optionsFor(name, secondDir));
        X509Certificate second = readCertificate(secondDir.resolve(name + ".crt"));

        assertEquals(first.getSerialNumber(), second.getSerialNumber());
    }

    @Test
    void concurrentRequestsSameNewName_onlyOneGenerationHappens(@TempDir Path tempDir) throws Exception {
        String name = "carol-" + UUID.randomUUID();
        int clientCount = 5;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);
        List<Exception> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);

        for (int i = 0; i < clientCount; i++) {
            Path dir = tempDir.resolve("client-" + i);
            Files.createDirectories(dir);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    new KeyClient().run(optionsFor(name, dir));
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue(errors.isEmpty(), "Errors occurred: " + errors);

        List<BigInteger> serials = new ArrayList<>();
        for (int i = 0; i < clientCount; i++) {
            X509Certificate cert = readCertificate(tempDir.resolve("client-" + i).resolve(name + ".crt"));
            serials.add(cert.getSerialNumber());
        }

        BigInteger firstSerial = serials.get(0);
        for (BigInteger serial : serials) {
            assertEquals(firstSerial, serial, "All concurrent clients must receive the identical certificate");
        }
    }

    @Test
    void crashClient_doesNotCrashServer(@TempDir Path tempDir) throws Exception {
        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(General.Protocol.encodeName("crash-client-" + UUID.randomUUID()));
            out.flush();
        }

        Thread.sleep(200);

        String name = "after-crash-" + UUID.randomUUID();
        assertDoesNotThrow(() -> new KeyClient().run(optionsFor(name, tempDir)));

        assertTrue(Files.exists(tempDir.resolve(name + ".crt")));
    }

    @Test
    void delayClient_stillReceivesResponse(@TempDir Path tempDir) throws Exception {
        String name = "slow-" + UUID.randomUUID();
        ClientOptions options = new ClientOptions("localhost", port, name, 1, false, tempDir);

        assertDoesNotThrow(() -> new KeyClient().run(options));
        assertTrue(Files.exists(tempDir.resolve(name + ".crt")));
    }

    @Test
    void loadTest_120ConcurrentClientsWithUniqueNames(@TempDir Path tempDir) throws Exception {
        int clientCount = 120;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> errors = java.util.Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < clientCount; i++) {
            String name = "load-" + i + "-" + UUID.randomUUID();
            Path dir = tempDir.resolve("client-" + i);
            Files.createDirectories(dir);

            executor.submit(() -> {
                try {
                    new KeyClient().run(optionsFor(name, dir));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(doneLatch.await(120, TimeUnit.SECONDS), "Not all clients finished in time");
        executor.shutdown();

        assertTrue(errors.isEmpty(), "Errors occurred: " + errors);
        assertEquals(clientCount, successCount.get());
    }
}