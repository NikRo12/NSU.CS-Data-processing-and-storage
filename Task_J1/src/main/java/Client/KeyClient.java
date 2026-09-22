package Client;

import General.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class KeyClient {

    private static final Logger log = LoggerFactory.getLogger(KeyClient.class);

    public void run(ClientOptions options) throws IOException {
        try (ClientSocketChannel channel = new ClientSocketChannel(options.host(), options.port())) {

            byte[] nameBytes = Protocol.encodeName(options.name());
            channel.send(nameBytes);
            log.info("Sent name '{}'", options.name());

            if (options.delaySeconds() > 0) {
                log.info("Sleeping {} seconds before reading response (slow client simulation)", options.delaySeconds());
                sleep(options.delaySeconds());
            }

            if (options.crash()) {
                log.info("Simulating crash: exiting before reading response");
                return;
            }

            byte[] raw = channel.readUntilClosed();
            Protocol.DecodedResponse response = Protocol.decodeResponse(raw);

            log.info("Received response: keyBytes={}, certBytes={}",
                    response.keyBytes().length, response.certBytes().length);

            Path keyPath = options.outputDir().resolve(options.name() + ".key");
            Path certPath = options.outputDir().resolve(options.name() + ".crt");

            PemFileWriter.write(keyPath, "PRIVATE KEY", response.keyBytes());
            PemFileWriter.write(certPath, "CERTIFICATE", response.certBytes());
        }
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}