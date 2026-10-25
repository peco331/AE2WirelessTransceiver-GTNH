package cn.gtnh.ae2wtx.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import cn.gtnh.ae2wtx.content.wireless.TransceiverTheme;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class TransceiverThemePacketTest {

    @Test
    public void roundTripsEveryTheme() {
        for (TransceiverTheme theme : TransceiverTheme.values()) {
            ByteBuf buf = Unpooled.buffer();
            new TransceiverThemePacket(-1, 10, 64, -20, 3, theme).toBytes(buf);
            TransceiverThemePacket read = new TransceiverThemePacket();
            read.fromBytes(buf);
            assertEquals(theme, read.getTheme());
        }
    }

    @Test
    public void rejectsShortOrOutOfRangeInput() {
        TransceiverThemePacket shortPacket = new TransceiverThemePacket();
        shortPacket.fromBytes(Unpooled.buffer().writeInt(0));
        assertNull(shortPacket.getTheme());

        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(0).writeInt(0).writeInt(0).writeInt(0).writeInt(0).writeByte(9);
        TransceiverThemePacket bad = new TransceiverThemePacket();
        bad.fromBytes(buf);
        assertNull(bad.getTheme());
    }
}
