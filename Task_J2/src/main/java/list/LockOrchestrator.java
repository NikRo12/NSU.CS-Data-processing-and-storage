package list;

import java.util.ArrayList;
import java.util.List;

final class LockOrchestrator {

    Permit acquireForSortStep(RawList list, Node startFrom) {
        Node p = startFrom;
        p.lockStep();

        Node a = list.next(p);
        a.lockStep();

        if (a == list.tail()) {
            return Permit.endOfPass(p, a);
        }

        Node b = list.next(a);
        b.lockStep();

        if (b == list.tail()) {
            return Permit.endOfPass(p, a, b);
        }

        Node q = list.next(b);
        q.lockStep();

        return Permit.forSortStep(p, a, b, q);
    }

    void relink(Permit permit, Runnable change) {
        Node[] nodes = permit.nodes();
        for (Node node : nodes) {
            node.lockLink();
        }
        try {
            change.run();
        } finally {
            for (int i = nodes.length - 1; i >= 0; i--) {
                nodes[i].unlockLink();
            }
        }
    }

    void insertFirst(RawList list, String value) {
        Node anchor = list.head();
        anchor.lockLink();
        try {
            Node after = list.next(anchor);
            after.lockLink();
            try {
                list.insertAfter(anchor, value);
            } finally {
                after.unlockLink();
            }
        } finally {
            anchor.unlockLink();
        }
    }

    List<String> snapshot(RawList list) {
        List<String> result = new ArrayList<>();
        Node current = list.head();
        current.lockLink();
        try {
            Node next = list.next(current);
            while (next != list.tail()) {
                next.lockLink();
                current.unlockLink();
                current = next;
                result.add(list.value(current));
                next = list.next(current);
            }
        } finally {
            current.unlockLink();
        }
        return result;
    }
}