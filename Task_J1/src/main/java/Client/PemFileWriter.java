package Client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public final class PemFileWriter {

    private static final Logger log = LoggerFactory.getLogger(PemFileWriter.class);
    private static final int LINE_LENGTH = 64;

    private PemFileWriter() {
    }

    public static void write(Path path, String label, byte[] derBytes) throws IOException {
        String base64 = Base64.getMimeEncoder(LINE_LENGTH, "\n".getBytes()).encodeToString(derBytes);
        String pem = "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";

        Files.createDirectories(path.getParent() != null ? path.getParent() : path.toAbsolutePath().getParent());
        Files.writeString(path, pem);

        log.info("Wrote {} ({} bytes DER) to {}", label, derBytes.length, path.toAbsolutePath());
    }
}