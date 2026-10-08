package list;

final class RawList {

    private final Node headSentinel = new Node(null);
    private final Node tailSentinel = new Node(null);

    RawList() {
        headSentinel.next = tailSentinel;
        tailSentinel.prev = headSentinel;
    }

    Node head() {
        return headSentinel;
    }

    Node tail() {
        return tailSentinel;
    }

    Node next(Node n) {
        return n.next;
    }

    Node prev(Node n) {
        return n.prev;
    }

    String value(Node n) {
        return n.value;
    }

    void setValue(Node n, String value) {
        n.value = value;
    }

    void insertAfter(Node anchor, String value) {
        Node newNode = new Node(value);
        Node after = anchor.next;

        newNode.prev = anchor;
        newNode.next = after;
        anchor.next = newNode;
        after.prev = newNode;

    }

    boolean isChain(Node p, Node a, Node b, Node q) {
        return p.next == a && a.next == b && b.next == q;
    }

    void swapAdjacent(Node p, Node a, Node b, Node q) {
        if (a.next != b || b.prev != a) {
            throw new IllegalStateException("swapAdjacent: a и b не являются соседями");
        }
        if (p.next != a || a.prev != p) {
            throw new IllegalStateException("swapAdjacent: p не является предыдущим для a");
        }
        if (b.next != q || q.prev != b) {
            throw new IllegalStateException("swapAdjacent: q не является следующим для b");
        }

        p.next = b;
        b.prev = p;

        b.next = a;
        a.prev = b;

        a.next = q;
        q.prev = a;
    }
}