package app;

public record SortConfig(int threadCount,
                         long intraStepDelayMs,
                         long interStepDelayMs,
                         int maxLineLength) {

    public static final int DEFAULT_THREAD_COUNT = 2;
    public static final long DEFAULT_DELAY_MS = 1000;
    public static final int DEFAULT_MAX_LINE_LENGTH = 80;

    public SortConfig {
        if (threadCount < 0) {
            throw new IllegalArgumentException("threadCount must be >= 0");
        }
        if (intraStepDelayMs < 0 || interStepDelayMs < 0) {
            throw new IllegalArgumentException("delays must be >= 0");
        }
        if (maxLineLength < 1) {
            throw new IllegalArgumentException("maxLineLength must be >= 1");
        }
    }

    public static SortConfig fromArgs(String[] args) {
        int threads = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_THREAD_COUNT;
        long intra = args.length > 1 ? Long.parseLong(args[1]) : DEFAULT_DELAY_MS;
        long inter = args.length > 2 ? Long.parseLong(args[2]) : DEFAULT_DELAY_MS;
        return new SortConfig(threads, intra, inter, DEFAULT_MAX_LINE_LENGTH);
    }
}