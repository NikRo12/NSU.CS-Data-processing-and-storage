package Storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

public class WorkQueues {

    private static final Logger log = LoggerFactory.getLogger(WorkQueues.class);

    private final BlockingQueue<GenerationTask> generationQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<SendTask> sendQueue = new LinkedBlockingQueue<>();

    public record GenerationTask(String name, CompletableFuture<KeyStore.KeyCertBundle> future) {}

    public record SendTask(SocketChannel channel, KeyStore.KeyCertBundle bundle, Throwable error) {}

    public void submitGenerationTask(GenerationTask task) {
        generationQueue.offer(task);
        log.debug("Submitted generation task for name='{}', queue size={}", task.name(), generationQueue.size());
    }

    public GenerationTask takeGenerationTask() throws InterruptedException {
        GenerationTask task = generationQueue.take();
        log.debug("Took generation task for name='{}'", task.name());
        return task;
    }

    public void submitSendTask(SendTask task) {
        sendQueue.offer(task);
        log.debug("Submitted send task, queue size={}", sendQueue.size());
    }

    public SendTask pollSendTask() {
        SendTask task = sendQueue.poll();
        if (task != null) {
            log.debug("Polled send task from queue");
        }
        return task;
    }
}