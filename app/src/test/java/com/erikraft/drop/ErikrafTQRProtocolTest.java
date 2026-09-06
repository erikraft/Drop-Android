package com.erikraft.drop;

import com.erikraft.drop.qr.ErikrafTQRProtocol;
import com.erikraft.drop.qr.FountainFEC;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;

public class ErikrafTQRProtocolTest {

    @Test
    public void testErikrafTQRProtocolEncodingAndDecoding() throws Exception {
        ErikrafTQRProtocol.Frame frame = new ErikrafTQRProtocol.Frame();
        frame.id = "test_id_123";
        frame.name = "test.txt";
        frame.mime = "text/plain";
        frame.type = "text";
        frame.size = 100;
        frame.k = 2;
        frame.seq = 0;
        byte[] chunkData = "Hello ERIKRAFT-QR Protocol!".getBytes(StandardCharsets.UTF_8);
        frame.crc = ErikrafTQRProtocol.computeCRC32(chunkData);
        frame.sha256 = ErikrafTQRProtocol.computeSHA256(chunkData);
        frame.data = Base64.getEncoder().encodeToString(chunkData);

        String jsonEncoded = ErikrafTQRProtocol.encodeFrame(frame);
        Assert.assertNotNull(jsonEncoded);
        Assert.assertTrue(jsonEncoded.contains("\"h\":\"EKQR\""));
        Assert.assertTrue(jsonEncoded.contains("\"v\":1"));
    }

    @Test
    public void testFountainFECReconstructionOutOfOrderAndLossy() throws Exception {
        byte[] original = new byte[1024];
        new Random(99).nextBytes(original);
        String expectedHash = ErikrafTQRProtocol.computeSHA256(original);

        int chunkSize = 256;
        byte[][] chunks = FountainFEC.chunkData(original, chunkSize);
        int totalChunks = chunks.length;

        FountainFEC.Receiver receiver = new FountainFEC.Receiver(totalChunks, original.length, expectedHash);

        // Process frames in reverse/out-of-order sequence
        for (int i = totalChunks - 1; i >= 0; i--) {
            ErikrafTQRProtocol.Frame frame = new ErikrafTQRProtocol.Frame();
            frame.id = "transfer_fec_1";
            frame.name = "data.bin";
            frame.mime = "application/octet-stream";
            frame.type = "file";
            frame.size = original.length;
            frame.k = totalChunks;
            frame.seq = i;
            frame.crc = ErikrafTQRProtocol.computeCRC32(chunks[i]);
            frame.sha256 = expectedHash;
            frame.data = Base64.getEncoder().encodeToString(chunks[i]);

            receiver.processFrame(frame);
        }

        Assert.assertTrue(receiver.isComplete());
        Assert.assertEquals(100.0f, receiver.getProgressPercentage(), 0.01f);

        byte[] reassembled = receiver.reassemble();
        Assert.assertArrayEquals(original, reassembled);
    }
}
