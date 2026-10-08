package list;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

public final class SyncedList implements Iterable<String> {

    private final RawList list = new RawList();
    private final LockOrchestrator orchestrator = new LockOrchestrator();
    private final Writer writer = new Writer(list, orchestrator);

    public void addFirst(String value) {
        writer.writeFirst(value);
    }

    @Override
    public Iterator<String> iterator() {
        return Collections.unmodifiableList(orchestrator.snapshot(list)).iterator();
    }

    public Sorter newSorter(long intraStepDelayMs, long interStepDelayMs, AtomicLong stepCounter) {
        return new Sorter(list, orchestrator, intraStepDelayMs, interStepDelayMs, stepCounter);
    }
}