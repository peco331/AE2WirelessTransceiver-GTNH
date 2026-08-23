package cn.gtnh.ae2wtx.wireless;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import cn.gtnh.ae2wtx.network.LabelListResponsePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class LabelListResponsePacketTest {

    @Test
    public void preservesSelectedBandStatusAcrossTheWire() {
        LabelNetworkRegistry.PagedSnapshots page = new LabelNetworkRegistry.PagedSnapshots(
            Arrays.asList(
                new LabelNetworkRegistry.Snapshot("band_1", 101L),
                new LabelNetworkRegistry.Snapshot("band_2", 202L)),
            1,
            64,
            66,
            2);
        LabelListResponsePacket sent = new LabelListResponsePacket(
            180,
            5,
            64,
            4,
            12,
            37,
            page,
            "band_1",
            "peco331",
            "band_2",
            7,
            9,
            33,
            32,
            41);
        ByteBuf buffer = Unpooled.buffer();
        try {
            sent.toBytes(buffer);
            LabelListResponsePacket received = new LabelListResponsePacket();
            received.fromBytes(buffer);

            assertEquals(180, received.getDimension());
            assertEquals(5, received.getX());
            assertEquals(64, received.getY());
            assertEquals(4, received.getZ());
            assertEquals(12, received.getWindowId());
            assertEquals(37, received.getRequestId());
            assertEquals(1, received.getPage());
            assertEquals(64, received.getPageSize());
            assertEquals(66, received.getTotalEntries());
            assertEquals(2, received.getPageCount());
            assertEquals(2, received.getEntries().size());
            assertEquals("band_1", received.getEntries().get(0).label);
            assertEquals(101L, received.getEntries().get(0).channel);
            assertEquals("band_1", received.getCurrentLabel());
            assertEquals("peco331", received.getOwnerName());
            assertEquals("band_2", received.getInspectedLabel());
            assertEquals(7, received.getOnlineCount());
            assertEquals(9, received.getEndpointCount());
            assertEquals(33, received.getUsedChannels());
            assertEquals(32, received.getMaxChannels());
            assertEquals(41, received.getNetworkChannels());
        } finally {
            buffer.release();
        }
    }
}
