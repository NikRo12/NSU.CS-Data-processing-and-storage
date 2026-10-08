package list;

final class Permit implements AutoCloseable {

    private final Node[] nodes;
    private final boolean endOfPass;
    private boolean closed;

    private Permit(boolean endOfPass, Node... nodes) {
        this.endOfPass = endOfPass;
        this.nodes = nodes;
    }

    static Permit forSortStep(Node p, Node a, Node b, Node q) {
        return new Permit(false, p, a, b, q);
    }

    static Permit endOfPass(Node... locked) {
        return new Permit(true, locked);
    }

    Node[] nodes() {
        return nodes;
    }

    Node p() {
        return nodes[0];
    }

    Node a() {
        return nodes[1];
    }

    Node b() {
        return nodes[2];
    }

    Node q() {
        return nodes[3];
    }

    boolean isEndOfPass() {
        return endOfPass;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (int i = nodes.length - 1; i >= 0; i--) {
            nodes[i].unlockStep();
        }
    }
}