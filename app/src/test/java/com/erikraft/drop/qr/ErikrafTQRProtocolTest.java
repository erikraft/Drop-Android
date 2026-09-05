package com.erikraft.drop.qr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
        ErikrafTQRProtocol.Frame decoded = ErikrafTQRProtocol.decodeFrame(encoded);

        assertNotNull(decoded);
        assertEquals(ErikrafTQRProtocol.MAGIC, decoded.magic);
        assertEquals(ErikrafTQRProtocol.VERSION, decoded.version);
        assertEquals("TEST1234", decoded.id);
        assertEquals("text", decoded.type);
        assertEquals("text.txt", decoded.name);
        assertEquals("text/plain", decoded.mime);
        assertEquals(data.length, decoded.size);
        assertEquals(1, decoded.k);
        assertEquals(0, decoded.seq);
        assertEquals(0, decoded.compressed);
        assertEquals(frame.crc, decoded.crc);
        assertEquals(frame.sha256, decoded.sha256);
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
        ErikrafTQRProtocol.Frame decoded = ErikrafTQRProtocol.decodeFrame(encoded);

        assertNotNull(decoded);
        assertEquals("TEST1234", decoded.id);
        assertEquals("text.txt", decoded.name);
        assertEquals("text/plain", decoded.mime);
        assertEquals(data.length, decoded.size);
        assertEquals(0, decoded.seq);
        assertEquals(1, decoded.k);
        assertEquals(0, decoded.compressed);
        assertEquals(frame.sha256, decoded.sha256);
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
        assertEquals("text", decoded.type);
        assertEquals("text.txt", decoded.name);
        assertEquals("text/plain", decoded.mime);
        assertEquals(5, decoded.size);
        assertEquals(0, decoded.seq);
        assertEquals(1, decoded.k);
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
        assertEquals("text", decoded.type);
        assertEquals("legacy.txt", decoded.name);
        assertEquals("text/plain", decoded.mime);
        assertEquals(6, decoded.size);
        assertEquals(0, decoded.seq);
        assertEquals(1, decoded.k);
        assertEquals(0, decoded.compressed);
        assertArrayEquals("Legacy".getBytes(StandardCharsets.UTF_8),
                ErikrafTQRProtocol.decodeBase64(decoded.data));
    }

    @Test
    public void malformedOrWrongProtocolFramesAreRejected() {
        assertNull(ErikrafTQRProtocol.decodeFrame("{\"h\":\"NOT_EKQR\",\"v\":1}"));
        assertNull(ErikrafTQRProtocol.decodeFrame("{\"h\":\"EKQR\",\"v\":2}"));
        assertNull(ErikrafTQRProtocol.decodeFrame("not json"));
    }
}
