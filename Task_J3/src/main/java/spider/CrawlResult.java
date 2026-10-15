package spider;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record CrawlResult(List<String> sortedMessages, Map<String, Page> pages, Set<String> failedPaths) {
}
