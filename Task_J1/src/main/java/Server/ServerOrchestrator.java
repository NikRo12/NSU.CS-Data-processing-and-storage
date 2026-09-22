package Server;

import General.CertificateIssuer;
import Storage.KeyStore;
import Storage.WorkQueues;
import org.bouncycastle.asn1.x500.X500Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ServerOrchestrator.class);

    public static void main(String[] args) {
        Map<String, String> options = parseArgs(args);

        int port = Integer.parseInt(options.getOrDefault("port", "9000"));
        int threadCount = Integer.parseInt(options.getOrDefault("threads", "4"));
        int keySize = Integer.parseInt(options.getOrDefault("key-size", "8192"));
        String caKeyPath = options.get("ca-key");
        String issuer = options.get("issuer");

        if (caKeyPath == null || issuer == null) {
            System.err.println("Usage: --ca-key <path> --issuer <name> [--port <port>] [--threads <n>]");
            System.exit(1);
            return;
        }

        PrivateKey caPrivateKey = CertificateIssuer.loadCaPrivateKey(Path.of(caKeyPath));
        CertificateIssuer certificateIssuer = new CertificateIssuer(caPrivateKey, new X500Name(issuer));

        KeyStore keyStore = new KeyStore();
        WorkQueues workQueues = new WorkQueues();

        List<Thread> generationThreads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            KeyGenerationWorker worker = new KeyGenerationWorker(workQueues, keyStore, certificateIssuer, keySize);
            Thread thread = new Thread(worker, "generation-worker-" + i);
            generationThreads.add(thread);
            thread.start();
        }
        log.info("Started {} generation worker threads", threadCount);

        ClientNetworkWorker networkWorker;
        try {
            networkWorker = new ClientNetworkWorker(port, keyStore, workQueues);
        } catch (IOException e) {
            log.error("Failed to start network worker on port {}", port, e);
            generationThreads.forEach(Thread::interrupt);
            System.exit(1);
            return;
        }

        Thread networkThread = new Thread(networkWorker, "network-worker");
        networkThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, stopping server");

            networkWorker.shutdown();
            generationThreads.forEach(Thread::interrupt);

            joinQuietly(networkThread);
            generationThreads.forEach(ServerOrchestrator::joinQuietly);

            log.info("Server stopped");
        }, "shutdown-hook"));

        log.info("Server is running on port {}", port);
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].startsWith("--")) {
                options.put(args[i].substring(2), args[i + 1]);
            }
        }
        return options;
    }
}