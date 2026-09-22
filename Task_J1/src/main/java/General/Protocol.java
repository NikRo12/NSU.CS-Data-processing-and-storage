package General;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Protocol {

    private static final Logger log = LoggerFactory.getLogger(Protocol.class);

    private Protocol() {
    }

    public static byte[] encodeName(String name) {
        byte[] raw = name.getBytes(StandardCharsets.US_ASCII);
        byte[] result = Arrays.copyOf(raw, raw.length + 1);
        log.debug("Encoded name '{}' into {} bytes", name, result.length);
        return result;
    }

    public static int findNameTerminator(byte[] raw) {
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    public static String decodeName(byte[] raw, int terminatorIndex) {
        String name = new String(raw, 0, terminatorIndex, StandardCharsets.US_ASCII);
        log.debug("Decoded name '{}' from {} bytes", name, terminatorIndex);
        return name;
    }

    public static byte[] encodeResponse(byte[] keyBytes, byte[] certBytes) {
        ByteBuffer buf = ByteBuffer.allocate(4 + keyBytes.length + 4 + certBytes.length);

        buf.putInt(keyBytes.length);
        buf.put(keyBytes);
        buf.putInt(certBytes.length);
        buf.put(certBytes);

        log.debug("Encoded response: keyBytes={}, certBytes={}, total={}",
                keyBytes.length, certBytes.length, buf.capacity());

        return buf.array();
    }

    public record DecodedResponse(byte[] keyBytes, byte[] certBytes) {}

    public static DecodedResponse decodeResponse(byte[] raw) {
        ByteBuffer buf = ByteBuffer.wrap(raw);

        int keyLen = buf.getInt();
        byte[] keyBytes = new byte[keyLen];
        buf.get(keyBytes);

        int certLen = buf.getInt();
        byte[] certBytes = new byte[certLen];
        buf.get(certBytes);

        log.debug("Decoded response: keyBytes={}, certBytes={}", keyLen, certLen);

        return new DecodedResponse(keyBytes, certBytes);
    }
}