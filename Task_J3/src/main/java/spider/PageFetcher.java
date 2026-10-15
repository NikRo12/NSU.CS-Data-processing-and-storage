package spider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PageFetcher implements AutoCloseable {

    private final String baseUrl;
    private final PageParser parser;
    private final int maxAttempts;
    private final Duration requestTimeout;
    private final ExecutorService clientExecutor;
    private final HttpClient client;

    public PageFetcher(String baseUrl, PageParser parser, int maxAttempts, Duration requestTimeout) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.parser = parser;
        this.maxAttempts = maxAttempts;
        this.requestTimeout = requestTimeout;
        this.clientExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .executor(clientExecutor)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public Page fetch(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + normalize(path)))
                .GET()
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .build();
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return parser.parse(response.body());
                }
                lastFailure = new IOException("HTTP " + response.statusCode() + " for " + path);
            } catch (IOException e) {
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    public static String normalize(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Override
    public void close() {
        clientExecutor.close();
    }
}
