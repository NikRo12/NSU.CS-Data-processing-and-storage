package Server;

import General.CertificateIssuer;
import Storage.KeyStore;
import Storage.WorkQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

public class KeyGenerationWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(KeyGenerationWorker.class);
    private static final String KEY_ALGORITHM = "RSA";

    private final WorkQueues workQueues;
    private final KeyStore keyStore;
    private final CertificateIssuer certificateIssuer;
    private final KeyPairGenerator keyPairGenerator;

    public KeyGenerationWorker(WorkQueues workQueues, KeyStore keyStore, CertificateIssuer certificateIssuer, int keySize) {
        this.workQueues = workQueues;
        this.keyStore = keyStore;
        this.certificateIssuer = certificateIssuer;

        try {
            this.keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            this.keyPairGenerator.initialize(keySize);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA algorithm not available", e);
        }
    }

    @Override
    public void run() {
        log.info("KeyGenerationWorker started on thread '{}'", Thread.currentThread().getName());

        while (!Thread.currentThread().isInterrupted()) {
            WorkQueues.GenerationTask task;
            try {
                task = workQueues.takeGenerationTask();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            processTask(task);
        }

        log.info("KeyGenerationWorker on thread '{}' stopped", Thread.currentThread().getName());
    }

    private void processTask(WorkQueues.GenerationTask task) {
        String name = task.name();
        try {
            log.info("Generating key pair for name='{}'", name);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            X509Certificate certificate = certificateIssuer.issueCertificate(name, keyPair.getPublic());

            KeyStore.KeyCertBundle bundle = new KeyStore.KeyCertBundle(keyPair.getPrivate(), certificate);
            task.future().complete(bundle);

            log.info("Completed key pair and certificate for name='{}'", name);
        } catch (Exception e) {
            log.error("Failed to generate key pair for name='{}'", name, e);
            task.future().completeExceptionally(e);
            keyStore.remove(name);
        }
    }
}