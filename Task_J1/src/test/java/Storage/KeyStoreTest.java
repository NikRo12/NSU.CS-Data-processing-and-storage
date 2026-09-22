package Storage;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class KeyStoreTest {

    @Test
    void firstCall_returnsNewFuture() {
        KeyStore store = new KeyStore();

        KeyStore.LookupResult result = store.getOrCreate("alice");

        assertTrue(result.isNew());
        assertNotNull(result.future());
        assertFalse(result.future().isDone());
    }

    @Test
    void secondCall_returnsSameFutureAndNotNew() {
        KeyStore store = new KeyStore();

        KeyStore.LookupResult first = store.getOrCreate("alice");
        KeyStore.LookupResult second = store.getOrCreate("alice");

        assertTrue(first.isNew());
        assertFalse(second.isNew());
        assertSame(first.future(), second.future());
    }

    @Test
    void differentNames_getDifferentFutures() {
        KeyStore store = new KeyStore();

        KeyStore.LookupResult alice = store.getOrCreate("alice");
        KeyStore.LookupResult bob = store.getOrCreate("bob");

        assertTrue(alice.isNew());
        assertTrue(bob.isNew());
        assertNotSame(alice.future(), bob.future());
    }

    @Test
    void completedFuture_isVisibleToLaterLookup() {
        KeyStore store = new KeyStore();

        KeyStore.LookupResult first = store.getOrCreate("alice");
        KeyStore.KeyCertBundle bundle = new KeyStore.KeyCertBundle(null, null);
        first.future().complete(bundle);

        KeyStore.LookupResult second = store.getOrCreate("alice");

        assertFalse(second.isNew());
        assertTrue(second.future().isDone());
        assertSame(bundle, second.future().getNow(null));
    }

    @Test
    void remove_allowsRegenerationOnNextLookup() {
        KeyStore store = new KeyStore();

        KeyStore.LookupResult first = store.getOrCreate("alice");
        store.remove("alice");
        KeyStore.LookupResult second = store.getOrCreate("alice");

        assertTrue(second.isNew());
        assertNotSame(first.future(), second.future());
    }

    @Test
    void concurrentLookups_onlyOneIsNew() throws InterruptedException {
        KeyStore store = new KeyStore();
        int threadCount = 50;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger newCount = new AtomicInteger(0);
        List<CompletableFuture<KeyStore.KeyCertBundle>> futures =
                Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    KeyStore.LookupResult result = store.getOrCreate("contended-name");
                    if (result.isNew()) {
                        newCount.incrementAndGet();
                    }
                    futures.add(result.future());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Threads did not finish in time");
        executor.shutdownNow();

        assertEquals(1, newCount.get(), "Exactly one thread must receive isNew=true");
        assertEquals(threadCount, futures.size());

        CompletableFuture<KeyStore.KeyCertBundle> firstFuture = futures.getFirst();
        for (CompletableFuture<KeyStore.KeyCertBundle> future : futures) {
            assertSame(firstFuture, future, "All threads must receive the same future instance");
        }
    }

    @Test
    void concurrentLookups_allSubscribersReceiveResult() throws InterruptedException {
        KeyStore store = new KeyStore();
        int subscriberCount = 20;

        CountDownLatch receivedLatch = new CountDownLatch(subscriberCount);
        KeyStore.KeyCertBundle bundle = new KeyStore.KeyCertBundle(null, null);

        CompletableFuture<KeyStore.KeyCertBundle> sharedFuture = null;
        for (int i = 0; i < subscriberCount; i++) {
            KeyStore.LookupResult result = store.getOrCreate("shared");
            if (sharedFuture == null) {
                sharedFuture = result.future();
            }
            result.future().whenComplete((b, ex) -> receivedLatch.countDown());
        }

        sharedFuture.complete(bundle);

        assertTrue(receivedLatch.await(5, TimeUnit.SECONDS),
                "All subscribers must be notified when the future completes");
    }
}