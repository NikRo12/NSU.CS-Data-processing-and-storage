package spider;

import java.time.Duration;

public final class Main {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : DEFAULT_BASE_URL;
        CrawlResult result;
        try (PageFetcher fetcher = new PageFetcher(baseUrl, new PageParser(), 3, Duration.ofSeconds(60))) {
            result = new Spider(fetcher).crawl();
        }
        new ResultPrinter(System.out).print(result);
        if (!result.failedPaths().isEmpty()) {
            System.err.println("Failed paths: " + result.failedPaths().size());
            System.exit(1);
        }
    }
}
