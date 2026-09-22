package General;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public class CertificateIssuer {

    private static final Logger log = LoggerFactory.getLogger(CertificateIssuer.class);
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final Duration VALIDITY_PERIOD = Duration.ofDays(365);

    private final PrivateKey caPrivateKey;
    private final X500Name issuerName;
    private final SecureRandom secureRandom = new SecureRandom();

    public CertificateIssuer(PrivateKey caPrivateKey, X500Name issuerName) {
        this.caPrivateKey = caPrivateKey;
        this.issuerName = issuerName;
        log.info("CertificateIssuer initialized with issuer '{}'", issuerName);
    }

    public X509Certificate issueCertificate(String subjectName, PublicKey subjectPublicKey) {
        try {
            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                    .build(caPrivateKey);

            BigInteger serialNumber = new BigInteger(64, secureRandom);
            Instant now = Instant.now();

            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuerName,
                    serialNumber,
                    Date.from(now),
                    Date.from(now.plus(VALIDITY_PERIOD)),
                    new X500Name("CN=" + subjectName),
                    subjectPublicKey
            );

            X509CertificateHolder holder = builder.build(signer);
            X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(holder);

            log.info("Issued certificate for subject='{}', serial={}", subjectName, serialNumber);
            return certificate;

        } catch (Exception e) {
            log.error("Failed to issue certificate for subject='{}'", subjectName, e);
            throw new RuntimeException("Certificate issuance failed for " + subjectName, e);
        }
    }

    public static PrivateKey loadCaPrivateKey(Path pemFilePath) {
        try (FileReader fileReader = new FileReader(pemFilePath.toFile());
             PemReader pemReader = new PemReader(fileReader)) {

            PemObject pemObject = pemReader.readPemObject();
            if (pemObject == null) {
                throw new IOException("No PEM object found in file: " + pemFilePath);
            }

            byte[] keyBytes = pemObject.getContent();
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
            log.info("Loaded CA private key from {}", pemFilePath);
            return privateKey;

        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.error("Failed to load CA private key from {}", pemFilePath, e);
            throw new RuntimeException("Cannot load CA private key from " + pemFilePath, e);
        }
    }
}