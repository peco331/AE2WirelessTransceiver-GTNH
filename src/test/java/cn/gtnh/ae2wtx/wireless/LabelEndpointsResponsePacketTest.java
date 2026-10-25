package cn.gtnh.ae2wtx.wireless;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import cn.gtnh.ae2wtx.network.LabelEndpointsResponsePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class LabelEndpointsResponsePacketTest {

    @Test
    public void preservesEndpointsAndDimensionNamesAcrossTheWire() {
        Map<Integer, String> names = new HashMap<>();
        names.put(0, "Overworld");
        names.put(-1, "下界");
        LabelEndpointsResponsePacket sent = new LabelEndpointsResponsePacket(0, 5, 64, 4, 12, 3, "mining",
            Arrays.asList(
                new LabelNetworkRegistry.EndpointInfo(-1, -30, 70, 12, false),
                new LabelNetworkRegistry.EndpointInfo(0, 5, 64, 4, true)),
            600, names);
        ByteBuf buffer = Unpooled.buffer();
        try {
            sent.toBytes(buffer);
            LabelEndpointsResponsePacket received = new LabelEndpointsResponsePacket();
            received.fromBytes(buffer);

            assertEquals("mining", received.getLabel());
            assertEquals(600, received.getTotal());
            assertEquals(2, received.getEntries().size());
            LabelNetworkRegistry.EndpointInfo nether = received.getEntries().get(0);
            assertEquals(-1, nether.dim);
            assertEquals(-30, nether.x);
            assertEquals(70, nether.y);
            assertEquals(12, nether.z);
            assertFalse(nether.online);
            assertTrue(received.getEntries().get(1).online);
            assertEquals("下界", received.getDimensionNames().get(-1));
            assertEquals("Overworld", received.getDimensionNames().get(0));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void rejectsMoreEntriesThanTheDeclaredTotal() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            for (int i = 0; i < 6; i++) {
                buffer.writeInt(0);
            }
            buffer.writeInt(0); // empty label
            buffer.writeInt(1); // total
            buffer.writeInt(5); // count > total
            LabelEndpointsResponsePacket received = new LabelEndpointsResponsePacket();
            received.fromBytes(buffer);
            assertTrue(received.getEntries().isEmpty());
        } finally {
            buffer.release();
        }
    }
}
