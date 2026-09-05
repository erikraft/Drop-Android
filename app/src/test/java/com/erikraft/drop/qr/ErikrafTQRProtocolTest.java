package com.erikraft.drop.qr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class ErikrafTQRProtocolTest {

    @Test
    public void androidFrameUsesWebCompatibleSchema() {
        byte[] data = "hello from Android".getBytes(StandardCharsets.UTF_8);
        ErikrafTQRProtocol.Frame frame = new ErikrafTQRProtocol.Frame();
        frame.id = "TEST1234";
        frame.type = "text";
        frame.name = "text.txt";
        frame.mime = "text/plain";
        frame.size = data.length;
        frame.k = 1;
        frame.seq = 0;
        frame.compressed = 0;
        frame.data = ErikrafTQRProtocol.encodeBase64(data);
        frame.crc = ErikrafTQRProtocol.computeCRC32(frame.data);
        frame.sha256 = ErikrafTQRProtocol.computeSHA256(data);

        String encoded = ErikrafTQRProtocol.encodeFrame(frame);
        assertNotNull(encoded);
        assertEquals(ErikrafTQRProtocol.MAGIC, ErikrafTQRProtocol.decodeFrame(encoded).magic);

        ErikrafTQRProtocol.Frame decoded = ErikrafTQRProtocol.decodeFrame(encoded);
        assertNotNull(decoded);
        assertEquals("TEST1234", decoded.id);
        assertEquals("text.txt", decoded.name);
        assertArrayEquals(data, ErikrafTQRProtocol.decodeBase64(decoded.data));
    }

    @Test
    public void webFrameSchemaIsAccepted() {
        String payload = "SGVsbG8=";
        String sha = ErikrafTQRProtocol.computeSHA256("Hello".getBytes(StandardCharsets.UTF_8));
        String json = "{\"h\":\"EKQR\",\"v\":1,\"id\":\"WEB12345\",\"t\":\"text\","
                + "\"name\":\"text.txt\",\"mime\":\"text/plain\",\"sz\":5,\"i\":0,\"n\":1,"
                + "\"c\":0,\"crc\":" + ErikrafTQRProtocol.computeCRC32(payload)
                + ",\"sha\":\"" + sha + "\",\"d\":\"" + payload + "\"}";

        ErikrafTQRProtocol.Frame decoded = ErikrafTQRProtocol.decodeFrame(json);
        assertNotNull(decoded);
        assertEquals("WEB12345", decoded.id);
        assertEquals(0, decoded.compressed);
    }
}
