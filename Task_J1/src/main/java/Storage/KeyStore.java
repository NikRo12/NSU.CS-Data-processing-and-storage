package Storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class KeyStore {

    private static final Logger log = LoggerFactory.getLogger(KeyStore.class);

    private final ConcurrentHashMap<String, CompletableFuture<KeyCertBundle>> store = new ConcurrentHashMap<>();

    public record KeyCertBundle(PrivateKey privateKey, X509Certificate certificate) {}

    public record LookupResult(CompletableFuture<KeyCertBundle> future, boolean isNew) {}

    public LookupResult getOrCreate(String name) {
        CompletableFuture<KeyCertBundle> newFuture = new CompletableFuture<>();
        CompletableFuture<KeyCertBundle> existing = store.putIfAbsent(name, newFuture);

        if (existing != null) {
            log.debug("Name '{}' already known, reusing existing future", name);
            return new LookupResult(existing, false);
        }

        log.debug("Name '{}' is new, registered new future", name);
        return new LookupResult(newFuture, true);
    }

    public void remove(String name) {
        store.remove(name);
        log.debug("Removed name '{}' from store", name);
    }
}