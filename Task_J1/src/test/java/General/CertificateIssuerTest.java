package General;

import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class CertificateIssuerTest {

    private static final int TEST_KEY_SIZE = 2048;
    private static final String TEST_ISSUER = "CN=Test-CA";

    private static KeyPair caKeyPair;
    private static CertificateIssuer issuer;

    @BeforeAll
    static void setUp() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(TEST_KEY_SIZE);
        caKeyPair = generator.generateKeyPair();

        issuer = new CertificateIssuer(caKeyPair.getPrivate(), new X500Name(TEST_ISSUER));
    }

    private KeyPair generateSubjectKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(TEST_KEY_SIZE);
        return generator.generateKeyPair();
    }

    @Test
    void issuedCertificate_hasCorrectSubjectName() throws Exception {
        KeyPair subjectKeyPair = generateSubjectKeyPair();

        X509Certificate cert = issuer.issueCertificate("alice", subjectKeyPair.getPublic());

        assertTrue(cert.getSubjectX500Principal().getName().contains("CN=alice"));
    }

    @Test
    void issuedCertificate_hasCorrectIssuerName() throws Exception {
        KeyPair subjectKeyPair = generateSubjectKeyPair();

        X509Certificate cert = issuer.issueCertificate("alice", subjectKeyPair.getPublic());

        assertTrue(cert.getIssuerX500Principal().getName().contains("CN=Test-CA"));
    }

    @Test
    void issuedCertificate_containsSubjectPublicKey() throws Exception {
        KeyPair subjectKeyPair = generateSubjectKeyPair();

        X509Certificate cert = issuer.issueCertificate("alice", subjectKeyPair.getPublic());

        assertArrayEquals(subjectKeyPair.getPublic().getEncoded(), cert.getPublicKey().getEncoded());
    }

    @Test
    void issuedCertificate_isSignedByCaKey() throws Exception {
        KeyPair subjectKeyPair = generateSubjectKeyPair();

        X509Certificate cert = issuer.issueCertificate("alice", subjectKeyPair.getPublic());

        assertDoesNotThrow(() -> cert.verify(caKeyPair.getPublic()));
    }

    @Test
    void issuedCertificate_isNotSignedByOtherKey() throws Exception {
        KeyPair subjectKeyPair = generateSubjectKeyPair();
        KeyPair otherKeyPair = generateSubjectKeyPair();

        X509Certificate cert = issuer.issueCertificate("alice", subjectKeyPair.getPublic());

        assertThrows(Exception.class, () -> cert.verify(otherKeyPair.getPublic()));
    }

    @Test
    void issuedCertificate_isCurrentlyValid() throws Exception {
        KeyPair subjectKeyPair = generateSubjectKeyPair();

        X509Certificate cert = issuer.issueCertificate("alice", subjectKeyPair.getPublic());

        assertDoesNotThrow(() -> cert.checkValidity(new Date()));
    }

    @Test
    void differentCertificates_haveDifferentSerialNumbers() throws Exception {
        KeyPair first = generateSubjectKeyPair();
        KeyPair second = generateSubjectKeyPair();

        X509Certificate certA = issuer.issueCertificate("alice", first.getPublic());
        X509Certificate certB = issuer.issueCertificate("bob", second.getPublic());

        assertNotEquals(certA.getSerialNumber(), certB.getSerialNumber());
    }

    @Test
    void issuedCertificate_isEncodableToDer() throws Exception {
        KeyPair subjectKeyPair = generateSubjectKeyPair();

        X509Certificate cert = issuer.issueCertificate("alice", subjectKeyPair.getPublic());
        byte[] der = cert.getEncoded();

        assertNotNull(der);
        assertTrue(der.length > 0);
    }

    @Test
    void loadCaPrivateKey_readsPemFile() throws Exception {
        Path pemFile = Files.createTempFile("test-ca-key", ".pem");
        try {
            byte[] der = caKeyPair.getPrivate().getEncoded();
            String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
            String pem = "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
            Files.writeString(pemFile, pem);

            PrivateKey loaded = CertificateIssuer.loadCaPrivateKey(pemFile);

            assertArrayEquals(caKeyPair.getPrivate().getEncoded(), loaded.getEncoded());
        } finally {
            Files.deleteIfExists(pemFile);
        }
    }

    @Test
    void loadCaPrivateKey_throwsOnMissingFile() {
        Path missing = Path.of("definitely-does-not-exist-12345.pem");

        assertThrows(RuntimeException.class, () -> CertificateIssuer.loadCaPrivateKey(missing));
    }

    @Test
    void loadCaPrivateKey_throwsOnInvalidPem() throws IOException {
        Path badFile = Files.createTempFile("bad-pem", ".pem");
        try {
            Files.writeString(badFile, "this is not a PEM file at all");

            assertThrows(RuntimeException.class, () -> CertificateIssuer.loadCaPrivateKey(badFile));
        } finally {
            Files.deleteIfExists(badFile);
        }
    }
}