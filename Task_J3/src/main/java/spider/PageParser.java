package spider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public final class PageParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public Page parse(String json) throws IOException {
        try {
            return mapper.readValue(json, Page.class);
        } catch (JsonProcessingException e) {
            throw new IOException("Malformed JSON: " + e.getOriginalMessage(), e);
        }
    }
}