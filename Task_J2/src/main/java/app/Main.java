package app;

import list.Sorter;
import list.SyncedList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public final class Main {

    private static final String EXIT_COMMAND = "exit";

    public static void main(String[] args) throws IOException, InterruptedException {
        SortConfig config = SortConfig.fromArgs(args);
        SyncedList list = new SyncedList();
        StepStats stats = new StepStats();

        List<Thread> sorters = startSorters(config, list, stats);
        System.out.println("Started: " + config);
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

    private static List<Thread> startSorters(SortConfig config, SyncedList list, StepStats stats) {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < config.threadCount(); i++) {
            Sorter sorter = list.newSorter(
                    config.intraStepDelayMs(),
                    config.interStepDelayMs(),
                    stats.counter());
            Thread thread = new Thread(sorter, "sorter-" + i);
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

    private static void addLine(SyncedList list, String line, int maxLength) {
        List<String> chunks = split(line, maxLength);
        for (int i = chunks.size() - 1; i >= 0; i--) {
            list.addFirst(chunks.get(i));
        }
    }

    public static List<String> split(String line, int maxLength) {
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < line.length(); start += maxLength) {
            chunks.add(line.substring(start, Math.min(line.length(), start + maxLength)));
        }
        return chunks;
    }

    private static void printList(SyncedList list, StepStats stats) {
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (String s : list) {
            count++;
            out.append(count).append(": ").append(s).append('\n');
        }
        System.out.println("--- list, " + count + " entries ---");
        System.out.print(out);
        System.out.println("--- " + stats.report() + " ---");
    }
}