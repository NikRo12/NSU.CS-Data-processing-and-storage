package Client;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public record ClientOptions(String host, int port, String name, int delaySeconds, boolean crash, Path outputDir) {

    public static ClientOptions parse(String[] args) {
        Map<String, String> raw = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    raw.put(key, args[i + 1]);
                    i++;
                } else {
                    raw.put(key, "");
                }
            }
        }

        String name = raw.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required --name argument. Usage: --name <name> [--host <host>] [--port <port>] [--delay <seconds>] [--crash] [--output-dir <dir>]");
        }

        String host = raw.getOrDefault("host", "localhost");
        int port = Integer.parseInt(raw.getOrDefault("port", "9000"));
        int delaySeconds = Integer.parseInt(raw.getOrDefault("delay", "0"));
        boolean crash = raw.containsKey("crash");
        Path outputDir = Path.of(raw.getOrDefault("output-dir", "."));

        return new ClientOptions(host, port, name, delaySeconds, crash, outputDir);
    }
}