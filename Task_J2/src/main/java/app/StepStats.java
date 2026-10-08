package app;

import java.util.concurrent.atomic.AtomicLong;

public final class StepStats {

    private final AtomicLong steps = new AtomicLong();
    private volatile long startNanos;
    private volatile long stopNanos;
    private volatile boolean stopped;

    public AtomicLong counter() {
        return steps;
    }

    public void start() {
        steps.set(0);
        stopped = false;
        startNanos = System.nanoTime();
    }

    public void stop() {
        stopNanos = System.nanoTime();
        stopped = true;
    }

    public long steps() {
        return steps.get();
    }

    public long elapsedMillis() {
        long end = stopped ? stopNanos : System.nanoTime();
        return (end - startNanos) / 1_000_000;
    }

    public double stepsPerSecond() {
        long ms = elapsedMillis();
        return ms == 0 ? 0.0 : steps() * 1000.0 / ms;
    }

    public String report() {
        return String.format("steps=%d, elapsed=%d ms, rate=%.2f steps/s",
                steps(), elapsedMillis(), stepsPerSecond());
    }
}