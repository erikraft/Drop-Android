package com.erikraft.drop.qr;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

/**
 * EKQR v1 wire protocol shared with the ErikrafT Drop web client.
 * This class intentionally has no dependency on Android JSON APIs so the same
 * implementation can be executed by Gradle JVM unit tests and on Android.
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
        } catch (Throwable ignored) {
            return android.util.Base64.decode(str, android.util.Base64.DEFAULT);
        }
    }

    public static String encodeBase64(byte[] data) {
        try {
            return java.util.Base64.getEncoder().encodeToString(data);
        } catch (Throwable ignored) {
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
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    /** Encodes only the canonical v1 Web-compatible schema. */
    public static String encodeFrame(Frame frame) {
        JsonObject json = new JsonObject();
        json.addProperty("h", MAGIC);
        json.addProperty("v", frame.version);
        json.addProperty("id", frame.id != null ? frame.id : "");
        json.addProperty("t", frame.type != null ? frame.type : "file");
        json.addProperty("name", frame.name != null ? frame.name : "file.bin");
        json.addProperty("mime", frame.mime != null ? frame.mime : "application/octet-stream");
        json.addProperty("sz", frame.size);
        json.addProperty("i", frame.seq);
        json.addProperty("n", frame.k);
        json.addProperty("c", frame.compressed);
        json.addProperty("crc", frame.crc);
        json.addProperty("sha", frame.sha256 != null ? frame.sha256 : "");
        json.addProperty("d", frame.data != null ? frame.data : "");
        if (frame.fec != null && frame.fec.length == 2) {
            JsonArray fec = new JsonArray();
            fec.add(frame.fec[0]);
            fec.add(frame.fec[1]);
            json.add("fec", fec);
        }
        return json.toString();
    }

    /**
     * Decodes canonical Web v1 frames and the pre-canonical Android schema used by
     * older app builds. Legacy input is normalized into the current Frame model.
     */
    public static Frame decodeFrame(String frameStr) {
        if (frameStr == null || frameStr.trim().isEmpty()) return null;
        try {
            JsonElement root = JsonParser.parseString(frameStr);
            if (!root.isJsonObject()) return null;
            JsonObject json = root.getAsJsonObject();

            boolean canonical = hasString(json, "h", MAGIC);
            boolean legacy = !canonical && hasString(json, "m", MAGIC);
            if (!canonical && !legacy) return null;

            int version = intValue(json, "v", -1);
            if (version != VERSION) return null;

            Frame frame = new Frame();
            frame.magic = MAGIC;
            frame.version = VERSION;

            if (canonical) {
                frame.id = stringValue(json, "id", "");
                frame.type = stringValue(json, "t", "file");
                frame.name = stringValue(json, "name", "file.bin");
                frame.mime = stringValue(json, "mime", "application/octet-stream");
                frame.size = longValue(json, "sz", -1);
                frame.seq = intValue(json, "i", -1);
                frame.k = intValue(json, "n", -1);
                frame.compressed = intValue(json, "c", 0);
                frame.crc = longValue(json, "crc", -1);
                frame.sha256 = stringValue(json, "sha", "");
                frame.data = stringValue(json, "d", "");

                JsonElement fecValue = json.get("fec");
                if (fecValue != null && fecValue.isJsonArray()) {
                    JsonArray fec = fecValue.getAsJsonArray();
                    if (fec.size() == 2) {
                        frame.fec = new int[]{fec.get(0).getAsInt(), fec.get(1).getAsInt()};
                    }
                }
            } else {
                // Legacy Android v1 field mapping: m/id/t/n/mime/s/k/seq/hash/d/crc.
                frame.id = stringValue(json, "id", "");
                frame.type = stringValue(json, "t", "file");
                frame.name = stringValue(json, "n", "file.bin");
                frame.mime = stringValue(json, "mime", "application/octet-stream");
                frame.size = longValue(json, "s", -1);
                frame.k = intValue(json, "k", -1);
                frame.seq = intValue(json, "seq", -1);
                frame.compressed = intValue(json, "c", 0);
                frame.crc = longValue(json, "crc", -1);
                frame.sha256 = stringValue(json, "hash", "");
                frame.data = stringValue(json, "d", "");
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
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean hasString(JsonObject json, String key, String expected) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && expected.equals(value.getAsString());
    }

    private static String stringValue(JsonObject json, String key, String fallback) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : fallback;
    }

    private static int intValue(JsonObject json, String key, int fallback) {
        JsonElement value = json.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject json, String key, long fallback) {
        JsonElement value = json.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsLong() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
