package bench;

import library.ArrayListSorter;
import list.SyncedList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public final class StepBenchmark {

    private static final int[] THREAD_COUNTS = {1, 2, 4, 8};
    private static final long SEED = 42;

    private record Result(long steps, long elapsedMs) {
    }

    public static void main(String[] args) throws InterruptedException {
        long intra = args.length > 0 ? Long.parseLong(args[0]) : 100;
        long inter = args.length > 1 ? Long.parseLong(args[1]) : 100;
        long seconds = args.length > 2 ? Long.parseLong(args[2]) : 10;
        int size = args.length > 3 ? Integer.parseInt(args[3]) : 100;

        if (intra <= 0 || inter < 0 || seconds <= 0 || size < 2) {
            throw new IllegalArgumentException("need intra > 0, inter >= 0, seconds > 0, size >= 2");
        }

        List<String> data = randomData(size);

        System.out.printf("intra=%d ms, inter=%d ms, T=%d s, n=%d%n", intra, inter, seconds, size);
        System.out.printf("%3s | %8s %8s %6s | %8s %8s %6s%n",
                "N", "own", "theory", "ratio", "lib", "theory", "ratio");

        for (int threads : THREAD_COUNTS) {
            Result own = runOwn(data, threads, intra, inter, seconds);
            Result lib = runLibrary(data, threads, intra, inter, seconds);

            double ownTheory = ownTheory(threads, intra, inter, size, own.elapsedMs());
            double libTheory = libTheory(threads, intra, inter, size, lib.elapsedMs());

            System.out.printf("%3d | %8d %8.0f %6.2f | %8d %8.0f %6.2f%n",
                    threads,
                    own.steps(), ownTheory, own.steps() / ownTheory,
                    lib.steps(), libTheory, lib.steps() / libTheory);
        }
    }

    private static Result runOwn(List<String> data, int threads, long intra, long inter, long seconds)
            throws InterruptedException {
        SyncedList list = new SyncedList();
        for (String s : data) {
            list.addFirst(s);
        }
        AtomicLong counter = new AtomicLong();
        List<Runnable> sorters = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            sorters.add(list.newSorter(intra, inter, counter));
        }
        return run(sorters, counter, seconds);
    }

    private static Result runLibrary(List<String> data, int threads, long intra, long inter, long seconds)
            throws InterruptedException {
        List<String> list = Collections.synchronizedList(new ArrayList<>());
        for (String s : data) {
            list.add(0, s);
        }
        AtomicLong counter = new AtomicLong();
        List<Runnable> sorters = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            sorters.add(new ArrayListSorter(list, intra, inter, counter));
        }
        return run(sorters, counter, seconds);
    }

    private static Result run(List<Runnable> sorters, AtomicLong counter, long seconds)
            throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        for (Runnable sorter : sorters) {
            Thread thread = new Thread(sorter);
            thread.setDaemon(true);
            threads.add(thread);
        }

        long start = System.nanoTime();
        for (Thread thread : threads) {
            thread.start();
        }
        Thread.sleep(seconds * 1000);
        for (Thread thread : threads) {
            thread.interrupt();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        for (Thread thread : threads) {
            thread.join();
        }

        return new Result(counter.get(), elapsedMs);
    }

    private static double singleThreadSteps(long intra, long inter, int size, long elapsedMs) {
        double stepsPerPass = size - 1;
        double passMs = stepsPerPass * (intra + inter) + inter;
        return elapsedMs * stepsPerPass / passMs;
    }

    private static double ownTheory(int threads, long intra, long inter, int size, long elapsedMs) {
        double unlimited = threads * singleThreadSteps(intra, inter, size, elapsedMs);
        double slots = (size + 2) / 4;
        double cap = slots * elapsedMs / intra;
        return Math.min(unlimited, cap);
    }

    private static double libTheory(int threads, long intra, long inter, int size, long elapsedMs) {
        double unlimited = threads * singleThreadSteps(intra, inter, size, elapsedMs);
        double cap = (double) elapsedMs / intra;
        return Math.min(unlimited, cap);
    }

    private static List<String> randomData(int size) {
        Random random = new Random(SEED);
        List<String> data = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            data.add("item-" + random.nextInt(1_000_000));
        }
        return data;
    }
}