package list;

import java.util.concurrent.locks.ReentrantLock;

final class Node {

    volatile String value;
    volatile Node prev;
    volatile Node next;

    private final ReentrantLock stepLock = new ReentrantLock(true);
    private final ReentrantLock linkLock = new ReentrantLock(true);

    Node(String value) {
        this.value = value;
    }

    void lockStep() {
        stepLock.lock();
    }

    void unlockStep() {
        stepLock.unlock();
    }

    void lockLink() {
        linkLock.lock();
    }

    void unlockLink() {
        linkLock.unlock();
    }
}