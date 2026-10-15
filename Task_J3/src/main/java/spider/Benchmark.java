package spider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Benchmark {

    private static final long TIME_LIMIT_MILLIS = 120_000;
    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java -cp <classes> spider.Benchmark <student_id> <server.jar>");
            System.exit(2);
        }
        String studentId = args[0];
        String jar = args[1];
        String baseUrl = "http://127.0.0.1:" + PORT;

        if (!Files.isRegularFile(Path.of(jar))) {
            System.err.println("Server jar not found: " + Path.of(jar).toAbsolutePath());
            System.exit(2);
        }
        String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process server = new ProcessBuilder(javaBinary, "-jar", jar, studentId)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start();
        try {
            awaitServer(baseUrl, server);
            run(baseUrl);
        } finally {
            server.destroy();
        }
    }

    private static void run(String baseUrl) throws Exception {
        long started = System.nanoTime();
        CrawlResult result;
        try (PageFetcher fetcher = new PageFetcher(baseUrl, new PageParser(), 3, Duration.ofSeconds(60))) {
            result = new Spider(fetcher).crawl();
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        List<String> problems = validate(result);
        if (elapsedMillis > TIME_LIMIT_MILLIS) {
            problems.add("time limit exceeded: " + elapsedMillis + " ms > " + TIME_LIMIT_MILLIS + " ms");
        }

        System.out.println("pages visited : " + result.pages().size());
        System.out.println("messages      : " + result.sortedMessages().size());
        System.out.println("failed paths  : " + result.failedPaths().size());
        System.out.printf("elapsed       : %.2f s (limit %d s)%n", elapsedMillis / 1000.0, TIME_LIMIT_MILLIS / 1000);
        if (problems.isEmpty()) {
            System.out.println("RESULT        : PASS");
        } else {
            problems.forEach(p -> System.out.println("PROBLEM       : " + p));
            System.out.println("RESULT        : FAIL");
            System.exit(1);
        }
    }

    private static List<String> validate(CrawlResult result) {
        List<String> problems = new ArrayList<>();
        Map<String, Page> pages = result.pages();

        if (!result.failedPaths().isEmpty()) {
            problems.add(result.failedPaths().size() + " paths could not be fetched");
        }
        if (!pages.containsKey("/")) {
            problems.add("root page was not collected");
        }

        Set<String> missing = new HashSet<>();
        for (Page page : pages.values()) {
            for (String successor : page.successors()) {
                if (!pages.containsKey(PageFetcher.normalize(successor))) {
                    missing.add(successor);
                }
            }
        }
        if (!missing.isEmpty()) {
            problems.add(missing.size() + " successors were never visited");
        }

        List<String> messages = result.sortedMessages();
        if (messages.size() != pages.size()) {
            problems.add("messages count " + messages.size() + " differs from pages count " + pages.size());
        }
        for (int i = 1; i < messages.size(); i++) {
            if (messages.get(i - 1).compareTo(messages.get(i)) > 0) {
                problems.add("messages are not sorted at index " + i);
                break;
            }
        }

        List<String> expected = pages.values().stream().map(Page::message).sorted().toList();
        if (!expected.equals(messages)) {
            problems.add("sorted messages do not match collected pages");
        }
        return problems;
    }

    private static void awaitServer(String baseUrl, Process server) throws InterruptedException, IOException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build();
        for (int i = 0; i < 60; i++) {
            if (!server.isAlive()) {
                throw new IOException("server exited with code " + server.exitValue());
            }
            try {
                client.send(request, HttpResponse.BodyHandlers.discarding());
                return;
            } catch (IOException e) {
                Thread.sleep(500);
            }
        }
        throw new IOException("server did not start");
    }
}