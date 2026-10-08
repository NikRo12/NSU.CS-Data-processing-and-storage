package library;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class ArrayListSorter implements Runnable {

    private final List<String> list;
    private final long intraStepDelayMs;
    private final long interStepDelayMs;
    private final AtomicLong stepCounter;

    public ArrayListSorter(List<String> list,
                           long intraStepDelayMs,
                           long interStepDelayMs,
                           AtomicLong stepCounter) {
        this.list = list;
        this.intraStepDelayMs = intraStepDelayMs;
        this.interStepDelayMs = interStepDelayMs;
        this.stepCounter = stepCounter;
    }

    @Override
    public void run() {
        int cursor = 0;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                cursor = step(cursor);
                Thread.sleep(interStepDelayMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int step(int cursor) throws InterruptedException {
        synchronized (list) {
            if (cursor + 1 >= list.size()) {
                return 0;
            }

            Thread.sleep(intraStepDelayMs);
            stepCounter.incrementAndGet();

            if (list.get(cursor).compareTo(list.get(cursor + 1)) > 0) {
                Collections.swap(list, cursor, cursor + 1);
            }

            return cursor + 1;
        }
    }
}