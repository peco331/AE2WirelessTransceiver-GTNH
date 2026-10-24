package cn.gtnh.ae2wtx.content.wireless;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.UUID;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Test;

import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridNode;
import appeng.api.util.AEColor;

public class ManualConnectionRulesTest {

    @Test
    public void refusesASideTheNeighborDoesNotExpose() {
        // A GT ME hatch exposes only its front face.
        IGridNode hatch = node(EnumSet.of(ForgeDirection.NORTH), AEColor.Transparent);

        assertTrue(LabeledWirelessTransceiverBlockEntity
            .acceptsConnection(hatch, ForgeDirection.NORTH, AEColor.Transparent));
        assertFalse(LabeledWirelessTransceiverBlockEntity
            .acceptsConnection(hatch, ForgeDirection.EAST, AEColor.Transparent));
    }

    @Test
    public void followsAe2ColorMatching() {
        IGridNode redCable = node(EnumSet.allOf(ForgeDirection.class), AEColor.Red);

        assertTrue(LabeledWirelessTransceiverBlockEntity
            .acceptsConnection(redCable, ForgeDirection.UP, AEColor.Transparent));
        assertFalse(LabeledWirelessTransceiverBlockEntity
            .acceptsConnection(redCable, ForgeDirection.UP, AEColor.Blue));
    }

    @Test
    public void corruptOwnerIdDoesNotThrow() {
        UUID id = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

        assertEquals(id, LabeledWirelessTransceiverBlockEntity.parsePlacerId(id.toString()));
        assertNull(LabeledWirelessTransceiverBlockEntity.parsePlacerId("not-a-uuid"));
        assertNull(LabeledWirelessTransceiverBlockEntity.parsePlacerId(""));
    }

    private static IGridNode node(EnumSet<ForgeDirection> sides, AEColor color) {
        IGridBlock block = (IGridBlock) Proxy.newProxyInstance(
            ManualConnectionRulesTest.class.getClassLoader(),
            new Class<?>[] { IGridBlock.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getConnectableSides":
                        return sides;
                    case "getGridColor":
                        return color;
                    default:
                        return null;
                }
            });
        return (IGridNode) Proxy.newProxyInstance(
            ManualConnectionRulesTest.class.getClassLoader(),
            new Class<?>[] { IGridNode.class },
            (proxy, method, args) -> "getGridBlock".equals(method.getName()) ? block : null);
    }
}
