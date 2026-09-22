package Client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Client {

    private static final Logger log = LoggerFactory.getLogger(Client.class);

    public static void main(String[] args) {
        ClientOptions options;
        try {
            options = ClientOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        log.info("Connecting to {}:{} as '{}'", options.host(), options.port(), options.name());

        try {
            new KeyClient().run(options);
        } catch (IOException e) {
            log.error("Client failed", e);
            System.exit(1);
        }
    }
}