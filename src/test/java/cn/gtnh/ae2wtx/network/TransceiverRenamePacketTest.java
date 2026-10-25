package cn.gtnh.ae2wtx.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import cn.gtnh.ae2wtx.wireless.TransceiverNames;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class TransceiverRenamePacketTest {

    @Test
    public void roundTripsACleanedName() {
        ByteBuf buf = Unpooled.buffer();
        new TransceiverRenamePacket(-1, 10, 64, -20, 3, "  §a主网收发器 ").toBytes(buf);
        TransceiverRenamePacket read = new TransceiverRenamePacket();
        read.fromBytes(buf);
        assertTrue(read.isValid());
        assertEquals("主网收发器", read.getName());
    }

    @Test
    public void anEmptyNameClearsIt() {
        ByteBuf buf = Unpooled.buffer();
        new TransceiverRenamePacket(0, 1, 2, 3, 4, null).toBytes(buf);
        TransceiverRenamePacket read = new TransceiverRenamePacket();
        read.fromBytes(buf);
        assertTrue(read.isValid());
        assertNull(read.getName());
    }

    @Test
    public void rejectsShortOrOversizedInput() {
        TransceiverRenamePacket shortPacket = new TransceiverRenamePacket();
        shortPacket.fromBytes(Unpooled.buffer().writeInt(0));
        assertFalse(shortPacket.isValid());

        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(0).writeInt(0).writeInt(0).writeInt(0).writeInt(0);
        byte[] tooLong = new byte[TransceiverNames.MAX_BYTES + 1];
        java.util.Arrays.fill(tooLong, (byte) 'a');
        buf.writeInt(tooLong.length).writeBytes(tooLong);
        TransceiverRenamePacket oversized = new TransceiverRenamePacket();
        oversized.fromBytes(buf);
        assertFalse(oversized.isValid());

        ByteBuf ok = Unpooled.buffer();
        ok.writeInt(0).writeInt(0).writeInt(0).writeInt(0).writeInt(0);
        byte[] name = "abc".getBytes(StandardCharsets.UTF_8);
        ok.writeInt(name.length).writeBytes(name);
        TransceiverRenamePacket valid = new TransceiverRenamePacket();
        valid.fromBytes(ok);
        assertEquals("abc", valid.getName());
    }
}
