package General;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ClientConnectionStateTest {

    @Test
    void initialPhase_isReadingName() {
        ClientConnectionState state = new ClientConnectionState();

        assertEquals(ClientConnectionState.Phase.READING_NAME, state.getPhase());
    }

    @Test
    void nameNotComplete_untilTerminatorArrives() {
        ClientConnectionState state = new ClientConnectionState();
        byte[] partial = "ali".getBytes(StandardCharsets.US_ASCII);

        state.appendNameBytes(partial, 0, partial.length);

        assertFalse(state.isNameComplete());
    }

    @Test
    void nameComplete_afterTerminatorArrives() {
        ClientConnectionState state = new ClientConnectionState();
        byte[] withTerminator = Protocol.encodeName("alice");

        state.appendNameBytes(withTerminator, 0, withTerminator.length);

        assertTrue(state.isNameComplete());
    }

    @Test
    void nameAssembledFromMultipleChunks() {
        ClientConnectionState state = new ClientConnectionState();

        byte[] chunk1 = "ali".getBytes(StandardCharsets.US_ASCII);
        byte[] chunk2 = "ce".getBytes(StandardCharsets.US_ASCII);
        byte[] chunk3 = {0};

        state.appendNameBytes(chunk1, 0, chunk1.length);
        assertFalse(state.isNameComplete());

        state.appendNameBytes(chunk2, 0, chunk2.length);
        assertFalse(state.isNameComplete());

        state.appendNameBytes(chunk3, 0, chunk3.length);
        assertTrue(state.isNameComplete());

        assertEquals("alice", state.extractName());
    }

    @Test
    void extractName_transitionsToWaitingForKey() {
        ClientConnectionState state = new ClientConnectionState();
        byte[] encoded = Protocol.encodeName("alice");
        state.appendNameBytes(encoded, 0, encoded.length);

        state.extractName();

        assertEquals(ClientConnectionState.Phase.WAITING_FOR_KEY, state.getPhase());
    }

    @Test
    void extractName_throwsIfTerminatorNotFound() {
        ClientConnectionState state = new ClientConnectionState();
        byte[] partial = "ali".getBytes(StandardCharsets.US_ASCII);
        state.appendNameBytes(partial, 0, partial.length);

        assertThrows(IllegalStateException.class, state::extractName);
    }

    @Test
    void extractName_ignoresBytesAfterTerminator() {
        ClientConnectionState state = new ClientConnectionState();
        byte[] nameWithGarbage = {'a', 'l', 'i', 'c', 'e', 0, 'x', 'y', 'z'};

        state.appendNameBytes(nameWithGarbage, 0, nameWithGarbage.length);

        assertEquals("alice", state.extractName());
    }

    @Test
    void prepareResponse_transitionsToWritingResponse() {
        ClientConnectionState state = new ClientConnectionState();
        byte[] encoded = Protocol.encodeName("alice");
        state.appendNameBytes(encoded, 0, encoded.length);
        state.extractName();

        state.prepareResponse(new byte[]{1, 2, 3}, new byte[]{4, 5});

        assertEquals(ClientConnectionState.Phase.WRITING_RESPONSE, state.getPhase());
    }

    @Test
    void prepareResponse_fillsOutboundBufferWithEncodedResponse() {
        ClientConnectionState state = new ClientConnectionState();
        byte[] keyBytes = {1, 2, 3};
        byte[] certBytes = {4, 5};

        state.prepareResponse(keyBytes, certBytes);
        ByteBuffer buffer = state.getOutboundBuffer();

        assertNotNull(buffer);
        assertEquals(4 + keyBytes.length + 4 + certBytes.length, buffer.remaining());

        byte[] raw = new byte[buffer.remaining()];
        buffer.get(raw);
        Protocol.DecodedResponse decoded = Protocol.decodeResponse(raw);

        assertArrayEquals(keyBytes, decoded.keyBytes());
        assertArrayEquals(certBytes, decoded.certBytes());
    }

    @Test
    void markResponseFullySent_transitionsToDone() {
        ClientConnectionState state = new ClientConnectionState();
        state.prepareResponse(new byte[]{1, 2, 3}, new byte[]{4, 5});

        ByteBuffer buffer = state.getOutboundBuffer();
        buffer.position(buffer.limit());

        state.markResponseFullySent();

        assertEquals(ClientConnectionState.Phase.DONE, state.getPhase());
    }

    @Test
    void markResponseFullySent_throwsIfBufferStillHasData() {
        ClientConnectionState state = new ClientConnectionState();
        state.prepareResponse(new byte[]{1, 2, 3}, new byte[]{4, 5});

        assertThrows(IllegalStateException.class, state::markResponseFullySent);
    }

    @Test
    void markResponseFullySent_throwsIfResponseNeverPrepared() {
        ClientConnectionState state = new ClientConnectionState();

        assertThrows(IllegalStateException.class, state::markResponseFullySent);
    }
}