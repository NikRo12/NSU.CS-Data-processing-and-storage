package library;

import app.Main;
import app.SortConfig;
import app.StepStats;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ArrayListMain {

    private static final String EXIT_COMMAND = "exit";

    public static void main(String[] args) throws IOException, InterruptedException {
        SortConfig config = SortConfig.fromArgs(args);
        List<String> list = Collections.synchronizedList(new ArrayList<>());
        StepStats stats = new StepStats();

        List<Thread> sorters = startSorters(config, list, stats);
        System.out.println("Started (synchronizedList): " + config);
        System.out.println("Empty line - print list, '" + EXIT_COMMAND + "' or EOF - quit");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals(EXIT_COMMAND)) {
                    break;
                }
                if (line.isEmpty()) {
                    printList(list, stats);
                } else {
                    addLine(list, line, config.maxLineLength());
                }
            }
        }

        stopSorters(sorters);
        stats.stop();
        printList(list, stats);
        System.out.println("Final: " + stats.report());
    }

    private static List<Thread> startSorters(SortConfig config, List<String> list, StepStats stats) {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < config.threadCount(); i++) {
            ArrayListSorter sorter = new ArrayListSorter(
                    list,
                    config.intraStepDelayMs(),
                    config.interStepDelayMs(),
                    stats.counter());
            Thread thread = new Thread(sorter, "array-sorter-" + i);
            thread.setDaemon(true);
            threads.add(thread);
        }
        stats.start();
        for (Thread thread : threads) {
            thread.start();
        }
        return threads;
    }

    private static void stopSorters(List<Thread> threads) throws InterruptedException {
        for (Thread thread : threads) {
            thread.interrupt();
        }
        for (Thread thread : threads) {
            thread.join();
        }
    }

    private static void addLine(List<String> list, String line, int maxLength) {
        List<String> chunks = Main.split(line, maxLength);
        for (int i = chunks.size() - 1; i >= 0; i--) {
            list.add(0, chunks.get(i));
        }
    }

    private static void printList(List<String> list, StepStats stats) {
        long startNanos = System.nanoTime();
        StringBuilder out = new StringBuilder();
        int count = 0;
        synchronized (list) {
            for (String s : list) {
                count++;
                out.append(count).append(": ").append(s).append('\n');
            }
        }
        long waitedMs = (System.nanoTime() - startNanos) / 1_000_000;

        System.out.println("--- list, " + count + " entries, waited " + waitedMs + " ms ---");
        System.out.print(out);
        System.out.println("--- " + stats.report() + " ---");
    }
}