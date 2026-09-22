package General;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {

    @Test
    void encodeAndDecodeName_roundTrip() {
        byte[] encoded = Protocol.encodeName("alice");

        int terminatorIndex = Protocol.findNameTerminator(encoded);
        assertEquals(5, terminatorIndex);

        String decoded = Protocol.decodeName(encoded, terminatorIndex);
        assertEquals("alice", decoded);
    }

    @Test
    void encodeName_appendsNullTerminator() {
        byte[] encoded = Protocol.encodeName("bob");

        assertEquals(4, encoded.length);
        assertEquals(0, encoded[3]);
    }

    @Test
    void findNameTerminator_returnsMinusOne_whenNoTerminatorYet() {
        byte[] partial = "partialNam".getBytes();

        int result = Protocol.findNameTerminator(partial);

        assertEquals(-1, result);
    }

    @Test
    void findNameTerminator_findsFirstOccurrence() {
        byte[] withExtraZero = {65, 66, 0, 67, 0};

        int result = Protocol.findNameTerminator(withExtraZero);

        assertEquals(2, result);
    }

    @Test
    void encodeAndDecodeResponse_roundTrip() {
        byte[] keyBytes = {1, 2, 3, 4, 5};
        byte[] certBytes = {10, 20, 30};

        byte[] encoded = Protocol.encodeResponse(keyBytes, certBytes);
        Protocol.DecodedResponse decoded = Protocol.decodeResponse(encoded);

        assertArrayEquals(keyBytes, decoded.keyBytes());
        assertArrayEquals(certBytes, decoded.certBytes());
    }

    @Test
    void encodeResponse_emptyArrays_stillDecodable() {
        byte[] encoded = Protocol.encodeResponse(new byte[0], new byte[0]);
        Protocol.DecodedResponse decoded = Protocol.decodeResponse(encoded);

        assertEquals(0, decoded.keyBytes().length);
        assertEquals(0, decoded.certBytes().length);
    }
}