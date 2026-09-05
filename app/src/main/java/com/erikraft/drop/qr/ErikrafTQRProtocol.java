package com.erikraft.drop.qr;

import android.util.Log;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

/**
 * EKQR v1 wire protocol shared with the ErikrafT Drop web client.
 * The JSON field names intentionally match public/scripts/erikraft-qr.js.
 */
public class ErikrafTQRProtocol {

    public static final String MAGIC = "EKQR";
    public static final int VERSION = 1;

    public static class Frame {
        public String magic = MAGIC;
        public int version = VERSION;
        public String id;
        public String type; // "file" or "text"
        public String name;
        public String mime;
        public long size; // Original/uncompressed size
        public int k; // Number of base chunks
        public int seq; // Base chunk or parity-frame index
        public int compressed; // 1 when payload uses deflate-raw
        public long crc; // CRC32 of the Base64 payload string
        public String sha256; // SHA-256 of the original/uncompressed payload
        public String data; // Base64 chunk
        public int[] fec; // Optional two source indexes for XOR parity
    }

    public static byte[] decodeBase64(String str) {
        try {
            return java.util.Base64.getDecoder().decode(str);
        } catch (Throwable e) {
            return android.util.Base64.decode(str, android.util.Base64.DEFAULT);
        }
    }

    public static String encodeBase64(byte[] data) {
        try {
            return java.util.Base64.getEncoder().encodeToString(data);
        } catch (Throwable e) {
            return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
        }
    }

    /** Matches the web implementation: CRC32 over the Base64 string characters. */
    public static long computeCRC32(String base64Payload) {
        CRC32 crc32 = new CRC32();
        byte[] bytes = base64Payload.getBytes(StandardCharsets.US_ASCII);
        crc32.update(bytes, 0, bytes.length);
        return crc32.getValue();
    }

    public static long computeCRC32(byte[] data) {
        return computeCRC32(encodeBase64(data));
    }

    public static String computeSHA256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    public static String encodeFrame(Frame frame) {
        try {
            JSONObject json = new JSONObject();
            json.put("h", MAGIC);
            json.put("v", frame.version);
            json.put("id", frame.id != null ? frame.id : "");
            json.put("t", frame.type != null ? frame.type : "file");
            json.put("name", frame.name != null ? frame.name : "file.bin");
            json.put("mime", frame.mime != null ? frame.mime : "application/octet-stream");
            json.put("sz", frame.size);
            json.put("i", frame.seq);
            json.put("n", frame.k);
            json.put("c", frame.compressed);
            json.put("crc", frame.crc);
            json.put("sha", frame.sha256 != null ? frame.sha256 : "");
            json.put("d", frame.data != null ? frame.data : "");
            if (frame.fec != null && frame.fec.length == 2) {
                org.json.JSONArray fec = new org.json.JSONArray();
                fec.put(frame.fec[0]);
                fec.put(frame.fec[1]);
                json.put("fec", fec);
            }
            return json.toString();
        } catch (Exception e) {
            Log.e("ErikrafTQRProtocol", "Unable to encode EKQR frame", e);
            return "";
        }
    }

    public static Frame decodeFrame(String frameStr) {
        if (frameStr == null || frameStr.trim().isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(frameStr);
            if (!MAGIC.equals(json.optString("h"))) return null;
            if (json.optInt("v", -1) != VERSION) return null;

            Frame frame = new Frame();
            frame.magic = MAGIC;
            frame.version = VERSION;
            frame.id = json.optString("id", "");
            frame.type = json.optString("t", "file");
            frame.name = json.optString("name", "file.bin");
            frame.mime = json.optString("mime", "application/octet-stream");
            frame.size = json.optLong("sz", -1);
            frame.seq = json.optInt("i", -1);
            frame.k = json.optInt("n", -1);
            frame.compressed = json.optInt("c", 0);
            frame.crc = json.optLong("crc", -1);
            frame.sha256 = json.optString("sha", "");
            frame.data = json.optString("d", "");

            if (json.has("fec")) {
                org.json.JSONArray fec = json.optJSONArray("fec");
                if (fec != null && fec.length() == 2) {
                    frame.fec = new int[]{fec.optInt(0, -1), fec.optInt(1, -1)};
                }
            }

            if (frame.id.isEmpty() || frame.size < 0 || frame.k <= 0 || frame.k > 100000
                    || frame.seq < 0 || frame.data.isEmpty()) return null;
            if (frame.fec == null && frame.seq >= frame.k) return null;
            if (frame.fec != null && (frame.fec[0] < 0 || frame.fec[0] >= frame.k
                    || frame.fec[1] < 0 || frame.fec[1] >= frame.k)) return null;
            if (frame.compressed != 0 && frame.compressed != 1) return null;
            if (computeCRC32(frame.data) != frame.crc) return null;

            // Decode once here so malformed Base64 never reaches the FEC/reassembly layer.
            decodeBase64(frame.data);
            return frame;
        } catch (Exception e) {
            return null;
        }
    }
}
