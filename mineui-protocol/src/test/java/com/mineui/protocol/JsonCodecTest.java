package com.mineui.protocol;

import com.mineui.protocol.msg.Hello;
import com.mineui.protocol.msg.HelloAck;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCodecTest {

    @Test
    void helloRoundTrip() throws Exception {
        Hello hello = new Hello(1, "0.1.0", "26.2", List.of("screen", "hud"));
        Hello decoded = JsonCodec.decode(JsonCodec.encode(hello), Hello.class);

        assertEquals(1, decoded.protocol());
        assertEquals("0.1.0", decoded.modVersion());
        assertEquals("26.2", decoded.minecraft());
        assertEquals(List.of("screen", "hud"), decoded.capabilities());
    }

    @Test
    void helloAckRoundTrip() throws Exception {
        HelloAck ack = new HelloAck(1, "0.1.0", "0.1.0");
        HelloAck decoded = JsonCodec.decode(JsonCodec.encode(ack), HelloAck.class);
        assertEquals(ack, decoded);
    }

    @Test
    void missingCapabilitiesBecomesEmptyList() throws Exception {
        Hello decoded = JsonCodec.decode("{\"protocol\":1,\"modVersion\":\"0.1.0\",\"minecraft\":\"26.2\"}".getBytes(),
                Hello.class);
        assertTrue(decoded.capabilities().isEmpty());
    }

    @Test
    void malformedJsonThrowsProtocolException() {
        assertThrows(ProtocolException.class, () -> JsonCodec.decode("{not json".getBytes(), Hello.class));
    }

    @Test
    void literalNullIsRejected() {
        assertThrows(ProtocolException.class, () -> JsonCodec.decode("null".getBytes(), Hello.class));
    }
}
