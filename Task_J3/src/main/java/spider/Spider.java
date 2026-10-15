package spider;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class Spider {

    private static final String ROOT = "/";

    private final PageFetcher fetcher;

    public Spider(PageFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public CrawlResult crawl() throws InterruptedException {
        Map<String, Page> pages = new ConcurrentHashMap<>();
        Set<String> scheduled = ConcurrentHashMap.newKeySet();
        Set<String> failed = ConcurrentHashMap.newKeySet();
        AtomicInteger pending = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Visit visit = new Visit(executor, pages, scheduled, failed, pending, done);
            visit.submit(ROOT);
            done.await();
        }

        List<String> messages = pages.values().stream()
                .map(Page::message)
                .sorted()
                .toList();
        return new CrawlResult(messages, Map.copyOf(pages), Collections.unmodifiableSet(failed));
    }

    private final class Visit {

        private final ExecutorService executor;
        private final Map<String, Page> pages;
        private final Set<String> scheduled;
        private final Set<String> failed;
        private final AtomicInteger pending;
        private final CountDownLatch done;

        Visit(ExecutorService executor, Map<String, Page> pages, Set<String> scheduled,
              Set<String> failed, AtomicInteger pending, CountDownLatch done) {
            this.executor = executor;
            this.pages = pages;
            this.scheduled = scheduled;
            this.failed = failed;
            this.pending = pending;
            this.done = done;
        }

        void submit(String path) {
            String key = PageFetcher.normalize(path);
            if (!scheduled.add(key)) {
                return;
            }
            pending.incrementAndGet();
            executor.execute(() -> run(key));
        }

        private void run(String path) {
            try {
                Page page = fetcher.fetch(path);
                pages.put(path, page);
                for (String successor : page.successors()) {
                    submit(successor);
                }
            } catch (IOException e) {
                failed.add(path);
            } catch (InterruptedException e) {
                failed.add(path);
                Thread.currentThread().interrupt();
            } finally {
                if (pending.decrementAndGet() == 0) {
                    done.countDown();
                }
            }
        }
    }
}
