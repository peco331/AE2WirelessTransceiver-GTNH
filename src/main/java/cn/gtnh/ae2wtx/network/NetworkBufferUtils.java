package cn.gtnh.ae2wtx.network;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;

public final class NetworkBufferUtils {

    private NetworkBufferUtils() {}

    public static void writeUtf8(ByteBuf buf, String s) {
        writeUtf8(buf, s, Integer.MAX_VALUE);
    }

    public static void writeUtf8(ByteBuf buf, String s, int maxBytes) {
        if (s == null || s.isEmpty()) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        int len = Math.min(bytes.length, maxBytes);
        buf.writeInt(len);
        if (len > 0) {
            buf.writeBytes(bytes, 0, len);
        }
    }

    /**
     * Reads a UTF-8 string with length prefix.
     * @return non-null String (may be empty "") if valid; null if the buffer/length is malformed.
     */
    public static String tryReadUtf8(ByteBuf buf, int maxBytes) {
        if (buf == null || buf.readableBytes() < 4) {
            return null;
        }
        int len = buf.readInt();
        if (len < 0 || len > maxBytes || len > buf.readableBytes()) {
            return null;
        }
        if (len == 0) {
            return "";
        }
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new String(data, StandardCharsets.UTF_8);
    }
}