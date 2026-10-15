package spider;

import java.io.PrintStream;

public final class ResultPrinter {

    private final PrintStream out;

    public ResultPrinter(PrintStream out) {
        this.out = out;
    }

    public void print(CrawlResult result) {
        result.sortedMessages().forEach(out::println);
        out.flush();
    }
}
