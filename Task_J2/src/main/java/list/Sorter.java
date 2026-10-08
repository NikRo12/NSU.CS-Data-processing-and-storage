package list;

import java.util.concurrent.atomic.AtomicLong;

public final class Sorter implements Runnable {

    private final RawList list;
    private final LockOrchestrator orchestrator;
    private final long intraStepDelayMs;
    private final long interStepDelayMs;
    private final AtomicLong stepCounter;

    Sorter(RawList list,
           LockOrchestrator orchestrator,
           long intraStepDelayMs,
           long interStepDelayMs,
           AtomicLong stepCounter) {
        this.list = list;
        this.orchestrator = orchestrator;
        this.intraStepDelayMs = intraStepDelayMs;
        this.interStepDelayMs = interStepDelayMs;
        this.stepCounter = stepCounter;
    }

    @Override
    public void run() {
        Node cursor = list.head();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                cursor = step(cursor);
                Thread.sleep(interStepDelayMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Node step(Node cursor) throws InterruptedException {
        try (Permit permit = orchestrator.acquireForSortStep(list, cursor)) {
            if (permit.isEndOfPass()) {
                return list.head();
            }

            Thread.sleep(intraStepDelayMs);
            stepCounter.incrementAndGet();

            Node p = permit.p();
            Node a = permit.a();
            Node b = permit.b();
            Node q = permit.q();

            if (list.value(a).compareTo(list.value(b)) > 0) {
                orchestrator.relink(permit, () -> {
                    if (list.isChain(p, a, b, q)) {
                        list.swapAdjacent(p, a, b, q);
                    }
                });
            }

            return list.next(p);
        }
    }
}