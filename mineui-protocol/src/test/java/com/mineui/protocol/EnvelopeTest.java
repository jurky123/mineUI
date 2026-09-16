package com.mineui.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvelopeTest {

    private static byte[] json(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void roundTripPreservesAllFields() throws Exception {
        Envelope original = new Envelope(MessageType.SNAPSHOT, 7, 42, json("{\"a\":1}"));
        Envelope decoded = Envelope.decode(original.encode());

        assertEquals(original, decoded);
        assertEquals(MessageType.SNAPSHOT, decoded.type());
        assertEquals(7, decoded.session());
        assertEquals(42, decoded.revision());
        assertEquals(0, decoded.flags());
        assertArrayEquals(json("{\"a\":1}"), decoded.payload());
    }

    @Test
    void emptyPayloadRoundTrips() throws Exception {
        Envelope decoded = Envelope.decode(new Envelope(MessageType.PING, 0, 0, new byte[0]).encode());
        assertEquals(0, decoded.payloadSize());
    }

    @Test
    void encodedLengthIsExactlyHeaderPlusPayload() {
        byte[] bytes = new Envelope(MessageType.ACTION, 1, 2, json("hello")).encode();
        assertEquals(Envelope.HEADER_SIZE + 5, bytes.length);
    }

    @Test
    void rejectsWrongMagic() {
        byte[] bytes = new Envelope(MessageType.HELLO, 0, 0, new byte[0]).encode();
        bytes[0] = 0x00;
        assertThrows(ProtocolException.class, () -> Envelope.decode(bytes));
    }

    @Test
    void rejectsWrongProtocolVersion() {
        byte[] bytes = new Envelope(MessageType.HELLO, 0, 0, new byte[0]).encode();
        bytes[2] = 99;
        assertThrows(ProtocolException.class, () -> Envelope.decode(bytes));
    }

    @Test
    void rejectsUnknownMessageType() {
        byte[] bytes = new Envelope(MessageType.HELLO, 0, 0, new byte[0]).encode();
        bytes[3] = 0x7F;
        assertThrows(ProtocolException.class, () -> Envelope.decode(bytes));
    }

    @Test
    void rejectsUnsupportedFlags() {
        byte[] bytes = new Envelope(MessageType.HELLO, 0, 0, new byte[0]).encode();
        bytes[14] = (byte) Envelope.FLAG_COMPRESSED;
        assertThrows(ProtocolException.class, () -> Envelope.decode(bytes));
    }

    @Test
    void rejectsDeclaredLengthMismatch() {
        byte[] bytes = new Envelope(MessageType.HELLO, 0, 0, json("abc")).encode();
        ByteBuffer.wrap(bytes).putInt(13, 2);
        assertThrows(ProtocolException.class, () -> Envelope.decode(bytes));
    }

    @Test
    void rejectsOversizedPayloadOnDecode() {
        byte[] bytes = new Envelope(MessageType.HELLO, 0, 0, json("abc")).encode();
        ByteBuffer.wrap(bytes).putInt(13, Envelope.MAX_PAYLOAD_SIZE + 1);
        assertThrows(ProtocolException.class, () -> Envelope.decode(bytes));
    }

    @Test
    void rejectsOversizedPayloadOnConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new Envelope(MessageType.HELLO, 0, 0, new byte[Envelope.MAX_PAYLOAD_SIZE + 1]));
    }

    @Test
    void rejectsTruncatedData() {
        byte[] bytes = new Envelope(MessageType.HELLO, 0, 0, json("abc")).encode();
        byte[] truncated = new byte[bytes.length - 1];
        System.arraycopy(bytes, 0, truncated, 0, truncated.length);
        assertThrows(ProtocolException.class, () -> Envelope.decode(truncated));
    }

    @Test
    void rejectsNullAndEmpty() {
        assertThrows(ProtocolException.class, () -> Envelope.decode(null));
        assertThrows(ProtocolException.class, () -> Envelope.decode(new byte[0]));
    }

    @Test
    void maxPayloadExactlyAccepted() throws Exception {
        byte[] payload = new byte[Envelope.MAX_PAYLOAD_SIZE];
        Envelope decoded = Envelope.decode(new Envelope(MessageType.SNAPSHOT, 0, 0, payload).encode());
        assertEquals(Envelope.MAX_PAYLOAD_SIZE, decoded.payloadSize());
    }

    @Test
    void payloadIsDefensivelyCopied() {
        byte[] payload = json("abc");
        Envelope env = new Envelope(MessageType.HELLO, 0, 0, payload);
        payload[0] = 'z';
        assertArrayEquals(json("abc"), env.payload());
    }
}
