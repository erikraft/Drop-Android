package com.erikraft.drop.qr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class ErikrafTQRProtocolTest {

    @Test
    public void testErikrafTQRProtocolEncodingAndDecoding() {
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
        assertTrue(encoded.contains("\"h\":\"EKQR\""));
        assertTrue(encoded.contains("\"sz\":" + data.length));

        ErikrafTQRProtocol.Frame decoded = ErikrafTQRProtocol.decodeFrame(encoded);
        assertNotNull(decoded);
        assertEquals("TEST1234", decoded.id);
        assertEquals("text.txt", decoded.name);
        assertEquals(ErikrafTQRProtocol.computeSHA256(data), decoded.sha256);
        assertArrayEquals(data, ErikrafTQRProtocol.decodeBase64(decoded.data));
    }

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
        assertNotNull(ErikrafTQRProtocol.decodeFrame(encoded));
        assertTrue(encoded.contains("\"name\":\"text.txt\""));
        assertTrue(encoded.contains("\"i\":0"));
        assertTrue(encoded.contains("\"n\":1"));
        assertTrue(encoded.contains("\"sha\":\""));
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
        assertEquals(sha, decoded.sha256);
        assertArrayEquals("Hello".getBytes(StandardCharsets.UTF_8),
                ErikrafTQRProtocol.decodeBase64(decoded.data));
    }

    @Test
    public void legacyAndroidFrameSchemaIsAccepted() {
        String payload = ErikrafTQRProtocol.encodeBase64("Legacy".getBytes(StandardCharsets.UTF_8));
        String json = "{\"m\":\"EKQR\",\"v\":1,\"id\":\"LEGACY1\",\"t\":\"text\","
                + "\"n\":\"legacy.txt\",\"mime\":\"text/plain\",\"s\":6,\"k\":1,\"seq\":0,"
                + "\"crc\":" + ErikrafTQRProtocol.computeCRC32(payload)
                + ",\"hash\":\"" + ErikrafTQRProtocol.computeSHA256("Legacy".getBytes(StandardCharsets.UTF_8))
                + "\",\"d\":\"" + payload + "\"}";

        ErikrafTQRProtocol.Frame decoded = ErikrafTQRProtocol.decodeFrame(json);
        assertNotNull(decoded);
        assertEquals("LEGACY1", decoded.id);
        assertEquals("legacy.txt", decoded.name);
        assertArrayEquals("Legacy".getBytes(StandardCharsets.UTF_8),
                ErikrafTQRProtocol.decodeBase64(decoded.data));
    }
}
