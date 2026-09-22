package General;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ClientConnectionState {

    private static final Logger log = LoggerFactory.getLogger(ClientConnectionState.class);

    public enum Phase {
        READING_NAME,
        WAITING_FOR_KEY,
        WRITING_RESPONSE,
        DONE
    }

    private Phase phase = Phase.READING_NAME;
    private final ByteArrayOutputStream nameBuffer = new ByteArrayOutputStream();
    private ByteBuffer outboundBuffer;

    public Phase getPhase() {
        return phase;
    }

    public void appendNameBytes(byte[] chunk, int offset, int length) {
        nameBuffer.write(chunk, offset, length);
    }

    public boolean isNameComplete() {
        return Protocol.findNameTerminator(nameBuffer.toByteArray()) != -1;
    }

    public String extractName() {
        byte[] raw = nameBuffer.toByteArray();
        int terminatorIndex = Protocol.findNameTerminator(raw);

        if (terminatorIndex == -1) {
            throw new IllegalStateException("extractName() called before name terminator was found");
        }

        String name = Protocol.decodeName(raw, terminatorIndex);
        phase = Phase.WAITING_FOR_KEY;
        log.debug("Extracted name '{}', transitioning to WAITING_FOR_KEY", name);
        return name;
    }

    public void prepareResponse(byte[] keyBytes, byte[] certBytes) {
        byte[] encoded = Protocol.encodeResponse(keyBytes, certBytes);
        outboundBuffer = ByteBuffer.wrap(encoded);
        phase = Phase.WRITING_RESPONSE;
        log.debug("Prepared response ({} bytes), transitioning to WRITING_RESPONSE", encoded.length);
    }

    public ByteBuffer getOutboundBuffer() {
        return outboundBuffer;
    }

    public void markResponseFullySent() {
        if (outboundBuffer == null || outboundBuffer.hasRemaining()) {
            throw new IllegalStateException("markResponseFullySent() called but outboundBuffer still has remaining data");
        }
        phase = Phase.DONE;
        log.debug("Response fully sent, transitioning to DONE");
    }
}