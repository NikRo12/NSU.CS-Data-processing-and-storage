package list;

import java.util.Objects;

final class Writer {

    private final RawList list;
    private final LockOrchestrator orchestrator;

    Writer(RawList list, LockOrchestrator orchestrator) {
        this.list = list;
        this.orchestrator = orchestrator;
    }

    void writeFirst(String value) {
        Objects.requireNonNull(value, "value");
        orchestrator.insertFirst(list, value);
    }
}