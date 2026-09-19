package cn.gtnh.ae2wtx.content.wireless;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.util.IReadOnlyCollection;

public class ChannelDemandCountingTest {

    @Test
    public void wirelessTransceiverAdvertisesDenseCapacityForEveryBand() {
        assertEquals(
            EnumSet.of(GridFlags.DENSE_CAPACITY),
            new LabeledWirelessTransceiverBlockEntity().getFlags());
    }

    @Test
    public void countsThirtyThreeDemandNodesWithoutConsultingActiveState() throws Exception {
        NodeHandler root = new NodeHandler(EnumSet.noneOf(GridFlags.class), basicGridBlock());
        for (int i = 0; i < 33; i++) {
            NodeHandler device = new NodeHandler(EnumSet.of(GridFlags.REQUIRE_CHANNEL), basicGridBlock());
            connect(root, device);
        }

        assertEquals(33, invokeCounter(root.proxy));
    }

    @Test
    public void traversesCableNodesAndCyclesWithoutDoubleCounting() throws Exception {
        NodeHandler root = new NodeHandler(EnumSet.noneOf(GridFlags.class), basicGridBlock());
        NodeHandler firstCable = new NodeHandler(EnumSet.noneOf(GridFlags.class), basicGridBlock());
        NodeHandler secondCable = new NodeHandler(EnumSet.noneOf(GridFlags.class), basicGridBlock());
        connect(root, firstCable);
        connect(firstCable, secondCable);
        List<NodeHandler> devices = new ArrayList<>();
        for (int i = 0; i < 33; i++) {
            NodeHandler device = new NodeHandler(EnumSet.of(GridFlags.REQUIRE_CHANNEL), basicGridBlock());
            devices.add(device);
            connect(secondCable, device);
        }
        for (int i = 0; i < devices.size(); i++) {
            connect(devices.get(i), devices.get((i + 1) % devices.size()));
        }

        assertEquals(33, invokeCounter(root.proxy));
    }

    @Test
    public void doesNotCrossAnotherWirelessTransceiver() throws Exception {
        NodeHandler root = new NodeHandler(EnumSet.noneOf(GridFlags.class), basicGridBlock());
        NodeHandler boundary = new NodeHandler(
            EnumSet.noneOf(GridFlags.class),
            basicGridBlock(),
            new LabeledWirelessTransceiverBlockEntity());
        NodeHandler hiddenDevice = new NodeHandler(EnumSet.of(GridFlags.REQUIRE_CHANNEL), basicGridBlock());
        connect(root, boundary);
        connect(boundary, hiddenDevice);

        assertEquals(0, invokeCounter(root.proxy));
    }

    @Test
    public void collapsesAe2MultiblockMembersToOneDemand() throws Exception {
        NodeHandler root = new NodeHandler(EnumSet.noneOf(GridFlags.class), basicGridBlock());
        List<IGridNode> members = new ArrayList<>();
        IGridBlock multiblock = multiblockGridBlock(members);
        EnumSet<GridFlags> flags = EnumSet.of(GridFlags.REQUIRE_CHANNEL, GridFlags.MULTIBLOCK);
        NodeHandler first = new NodeHandler(flags, multiblock);
        NodeHandler second = new NodeHandler(flags, multiblock);
        members.add(first.proxy);
        members.add(second.proxy);
        connect(root, first);
        connect(root, second);

        assertEquals(1, invokeCounter(root.proxy));
    }

    @Test
    public void countsTwoIndependentAe2MultiblocksAsTwoDemands() throws Exception {
        NodeHandler root = new NodeHandler(EnumSet.noneOf(GridFlags.class), basicGridBlock());
        for (int group = 0; group < 2; group++) {
            List<IGridNode> members = new ArrayList<>();
            IGridBlock multiblock = multiblockGridBlock(members);
            EnumSet<GridFlags> flags = EnumSet.of(GridFlags.REQUIRE_CHANNEL, GridFlags.MULTIBLOCK);
            NodeHandler first = new NodeHandler(flags, multiblock);
            NodeHandler second = new NodeHandler(flags, multiblock);
            members.add(first.proxy);
            members.add(second.proxy);
            connect(root, first);
            connect(root, second);
        }

        assertEquals(2, invokeCounter(root.proxy));
    }

    private static int invokeCounter(IGridNode root) throws Exception {
        LabeledWirelessTransceiverBlockEntity tile = new LabeledWirelessTransceiverBlockEntity();
        Field nodeField = LabeledWirelessTransceiverBlockEntity.class.getDeclaredField("node");
        nodeField.setAccessible(true);
        nodeField.set(tile, root);
        Method method = LabeledWirelessTransceiverBlockEntity.class.getDeclaredMethod("countDeviceConsumers");
        method.setAccessible(true);
        return (Integer) method.invoke(tile);
    }

    private static void connect(NodeHandler first, NodeHandler second) {
        IGridConnection connection = (IGridConnection) Proxy.newProxyInstance(
            ChannelDemandCountingTest.class.getClassLoader(),
            new Class<?>[] { IGridConnection.class },
            (proxy, method, args) -> {
                if ("getOtherSide".equals(method.getName())) {
                    return args[0] == first.proxy ? second.proxy : first.proxy;
                }
                if ("a".equals(method.getName())) {
                    return first.proxy;
                }
                if ("b".equals(method.getName())) {
                    return second.proxy;
                }
                return defaultValue(method.getReturnType());
            });
        first.connections.add(connection);
        second.connections.add(connection);
    }

    private static IGridBlock basicGridBlock() {
        return (IGridBlock) Proxy.newProxyInstance(
            ChannelDemandCountingTest.class.getClassLoader(),
            new Class<?>[] { IGridBlock.class },
            objectDefaults());
    }

    private static IGridBlock multiblockGridBlock(List<IGridNode> members) {
        return (IGridBlock) Proxy.newProxyInstance(
            ChannelDemandCountingTest.class.getClassLoader(),
            new Class<?>[] { IGridMultiblock.class },
            (proxy, method, args) -> {
                if ("getMultiblockNodes".equals(method.getName())) {
                    return members.iterator();
                }
                return defaultValue(method.getReturnType());
            });
    }

    private static InvocationHandler objectDefaults() {
        return (proxy, method, args) -> defaultValue(method.getReturnType());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        return null;
    }

    private static final class NodeHandler implements InvocationHandler {

        private final EnumSet<GridFlags> flags;
        private final IGridBlock gridBlock;
        private final Object machine;
        private final List<IGridConnection> connections = new ArrayList<>();
        private final IGridNode proxy;

        private NodeHandler(EnumSet<GridFlags> flags, IGridBlock gridBlock) {
            this(flags, gridBlock, null);
        }

        private NodeHandler(EnumSet<GridFlags> flags, IGridBlock gridBlock, Object machine) {
            this.flags = flags;
            this.gridBlock = gridBlock;
            this.machine = machine;
            this.proxy = (IGridNode) Proxy.newProxyInstance(
                ChannelDemandCountingTest.class.getClassLoader(),
                new Class<?>[] { IGridNode.class },
                this);
        }

        @Override
        public Object invoke(Object nodeProxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getConnections":
                    return readOnlyConnections(connections);
                case "getGridBlock":
                    return gridBlock;
                case "getMachine":
                    return machine;
                case "hasFlag":
                    return flags.contains(args[0]);
                case "isActive":
                    throw new AssertionError("demand counting must not consult isActive()");
                case "hashCode":
                    return System.identityHashCode(nodeProxy);
                case "equals":
                    return nodeProxy == args[0];
                case "toString":
                    return "TestGridNode@" + Integer.toHexString(System.identityHashCode(nodeProxy));
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static IReadOnlyCollection<IGridConnection> readOnlyConnections(List<IGridConnection> connections) {
        return (IReadOnlyCollection<IGridConnection>) Proxy.newProxyInstance(
            ChannelDemandCountingTest.class.getClassLoader(),
            new Class<?>[] { IReadOnlyCollection.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "iterator":
                        Iterator<IGridConnection> iterator = connections.iterator();
                        return iterator;
                    case "size":
                        return connections.size();
                    case "isEmpty":
                        return connections.isEmpty();
                    case "contains":
                        return connections.contains(args[0]);
                    default:
                        return defaultValue(method.getReturnType());
                }
            });
    }
}
