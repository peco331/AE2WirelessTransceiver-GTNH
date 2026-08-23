package cn.gtnh.ae2wtx.network;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;

public final class NetworkBufferUtils {

    private NetworkBufferUtils() {}

    public static void writeUtf8(ByteBuf buf, String s) {
        if (s == null || s.isEmpty()) {
            buf.writeInt(0);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static String readUtf8(ByteBuf buf, int maxBytes) {
        if (buf == null || buf.readableBytes() < 4) {
            return "";
        }
        int len = buf.readInt();
        if (len <= 0) {
            return "";
        }
        if (len > maxBytes || len > buf.readableBytes()) {
            buf.skipBytes(Math.min(len, buf.readableBytes()));
            return "";
        }
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new String(data, StandardCharsets.UTF_8);
    }
}