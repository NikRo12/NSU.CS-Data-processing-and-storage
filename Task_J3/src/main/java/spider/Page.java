package spider;

import java.util.List;

public record Page(String message, List<String> successors) {
}
