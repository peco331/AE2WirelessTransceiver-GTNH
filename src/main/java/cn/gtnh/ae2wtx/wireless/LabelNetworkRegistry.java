package cn.gtnh.ae2wtx.wireless;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.ForgeDirection;

import cn.gtnh.ae2wtx.AE2Wtx;
import cn.gtnh.ae2wtx.config.ModConfig;
import cn.gtnh.ae2wtx.init.ModBlocks;
import appeng.api.AEApi;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;

/**
 * Labeled wireless network registry (WorldSavedData, stored in the overworld).
 * Maps (dimension, label, owner) to a LabelNetwork that owns a virtual AE2 grid
 * node; all labeled transceivers with the same label connect to that virtual node,
 * forming a shared network. Channels are auto-allocated starting at 1,000,000.
 */
public class LabelNetworkRegistry extends WorldSavedData {

    public static final String SAVE_ID = "ae2wtx_label_networks";
    private static final long CHANNEL_START = 1_000_000L;

    private final Map<Key, LabelNetwork> networks = new HashMap<>();
    private long nextChannel = CHANNEL_START;

    public LabelNetworkRegistry() {
        super(SAVE_ID);
    }

    public LabelNetworkRegistry(String name) {
        super(name);
    }

    /* ===================== access ===================== */

    public static LabelNetworkRegistry get(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        World overworld = world;
        if (world.provider.dimensionId != 0) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || server.worldServers == null || server.worldServers.length == 0) {
                return null;
            }
            overworld = server.worldServers[0];
        }
        LabelNetworkRegistry reg = (LabelNetworkRegistry) overworld.mapStorage.loadData(LabelNetworkRegistry.class, SAVE_ID);
        if (reg == null) {
            reg = new LabelNetworkRegistry();
            overworld.mapStorage.setData(SAVE_ID, reg);
        }
        return reg;
    }

    /* ===================== label normalization ===================== */

    public static String normalizeLabel(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > 64) {
            t = t.substring(0, 64);
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) {
                return null;
            }
        }
        return t;
    }

    /* ===================== API ===================== */

    public synchronized LabelNetwork register(World beWorld, String rawLabel, UUID placerId, IWirelessEndpoint endpoint) {
        String label = normalizeLabel(rawLabel);
        if (label == null) {
            return null;
        }
        UUID owner = placerId == null ? WirelessMasterRegistry.PUBLIC_NETWORK_UUID : placerId;
        Integer dim = ModConfig.wirelessCrossDimEnable ? null : beWorld.provider.dimensionId;
        Key key = new Key(dim, label, owner);

        LabelNetwork network = networks.get(key);
        if (network == null) {
            long channel = allocateChannel();
            network = new LabelNetwork(dim, label, owner, channel);
            if (!network.ensureVirtualNode(beWorld)) {
                return null;
            }
            networks.put(key, network);
            markDirty();
        } else {
            network.ensureVirtualNode(beWorld);
        }
        network.endpoints.add(new EndpointRef(dim, endpoint.getX(), endpoint.getY(), endpoint.getZ()));
        markDirty();
        return network;
    }

    public synchronized void unregister(IWirelessEndpoint endpoint) {
        World world = endpoint.getWorld();
        if (world == null) {
            return;
        }
        Integer dim = ModConfig.wirelessCrossDimEnable ? null : world.provider.dimensionId;
        for (LabelNetwork net : networks.values()) {
            net.endpoints.removeIf(ref -> ref.matches(dim, endpoint.getX(), endpoint.getY(), endpoint.getZ()));
        }
        markDirty();
    }

    public synchronized LabelNetwork getNetwork(World world, String rawLabel, UUID placerId) {
        String label = normalizeLabel(rawLabel);
        if (label == null) {
            return null;
        }
        UUID owner = placerId == null ? WirelessMasterRegistry.PUBLIC_NETWORK_UUID : placerId;
        Integer dim = ModConfig.wirelessCrossDimEnable ? null : world.provider.dimensionId;
        return networks.get(new Key(dim, label, owner));
    }

    public synchronized boolean removeNetwork(World world, String rawLabel, UUID placerId) {
        String label = normalizeLabel(rawLabel);
        if (label == null) {
            return false;
        }
        UUID owner = placerId == null ? WirelessMasterRegistry.PUBLIC_NETWORK_UUID : placerId;
        Integer dim = ModConfig.wirelessCrossDimEnable ? null : world.provider.dimensionId;
        LabelNetwork net = networks.remove(new Key(dim, label, owner));
        if (net != null) {
            net.destroyVirtualNode();
            markDirty();
            return true;
        }
        return false;
    }

    public synchronized List<Snapshot> listNetworks(UUID placerId) {
        UUID owner = placerId == null ? WirelessMasterRegistry.PUBLIC_NETWORK_UUID : placerId;
        Integer dim = ModConfig.wirelessCrossDimEnable ? null : 0; // list is global under cross-dim; else overworld scope
        List<Snapshot> list = new ArrayList<>();
        for (Map.Entry<Key, LabelNetwork> entry : networks.entrySet()) {
            Key key = entry.getKey();
            if (!Objects.equals(key.owner, owner)) {
                continue;
            }
            if (!ModConfig.wirelessCrossDimEnable && !Objects.equals(key.dim, dim)) {
                continue;
            }
            list.add(new Snapshot(key.label, entry.getValue().channel));
        }
        list.sort(Comparator.comparingLong(s -> s.channel));
        return list;
    }

    private long allocateChannel() {
        if (nextChannel < CHANNEL_START) {
            nextChannel = CHANNEL_START;
        }
        return nextChannel++;
    }

    /* ===================== serialization ===================== */

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        this.nextChannel = tag.getLong("nextChannel");
        this.networks.clear();
        NBTTagList list = tag.getTagList("networks", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound nbt = list.getCompoundTagAt(i);
            String label = nbt.getString("label");
            Integer dim = nbt.hasKey("dim") ? nbt.getInteger("dim") : null;
            UUID owner = UUID.fromString(nbt.getString("owner"));
            long channel = nbt.getLong("channel");
            LabelNetwork net = new LabelNetwork(dim, label, owner, channel);
            net.loadEndpoints(nbt.getTagList("endpoints", 10));
            networks.put(new Key(dim, label, owner), net);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setLong("nextChannel", nextChannel);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Key, LabelNetwork> e : networks.entrySet()) {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString("label", e.getKey().label);
            if (e.getKey().dim != null) {
                nbt.setInteger("dim", e.getKey().dim);
            }
            nbt.setString("owner", e.getKey().owner.toString());
            nbt.setLong("channel", e.getValue().channel);
            nbt.setTag("endpoints", e.getValue().saveEndpoints());
            list.appendTag(nbt);
        }
        tag.setTag("networks", list);
    }

    /* ===================== types ===================== */

    public static class Snapshot {

        public final String label;
        public final long channel;

        public Snapshot(String label, long channel) {
            this.label = label;
            this.channel = channel;
        }
    }

    private static final class Key {

        final Integer dim; // null = global (cross-dimension)
        final String label;
        final UUID owner;

        Key(Integer dim, String label, UUID owner) {
            this.dim = dim;
            this.label = label;
            this.owner = owner;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            Key k = (Key) o;
            return Objects.equals(dim, k.dim) && Objects.equals(label, k.label) && Objects.equals(owner, k.owner);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dim, label, owner);
        }
    }

    /** A label network: owns a virtual grid node, endpoints connect to it. */
    public static class LabelNetwork {

        final Integer dim;
        final String label;
        final UUID owner;
        final long channel;
        final Set<EndpointRef> endpoints = new HashSet<>();

        private IGridNode virtualNode;
        private VirtualLabelNodeHost virtualHost;

        LabelNetwork(Integer dim, String label, UUID owner, long channel) {
            this.dim = dim;
            this.label = label;
            this.owner = owner;
            this.channel = channel;
        }

        public long channel() {
            return channel;
        }

        public Integer dim() {
            return dim;
        }

        public IGridNode node() {
            return virtualNode;
        }

        public int endpointCount() {
            return endpoints.size();
        }

        /** Ensure the virtual node exists (recreate after world load). */
        public synchronized boolean ensureVirtualNode(World anyWorld) {
            if (virtualNode != null && !virtualNode.isActive()) {
                destroyVirtualNode();
            }
            if (virtualNode != null) {
                return true;
            }
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || server.worldServers == null || server.worldServers.length == 0) {
                return false;
            }
            World hostLevel = dim == null ? server.worldServers[0] : (dim == 0 ? server.worldServers[0] : server.worldServers[dim]);
            if (hostLevel == null) {
                return false;
            }
            try {
                this.virtualHost = new VirtualLabelNodeHost(hostLevel);
                this.virtualNode = AEApi.instance().createGridNode(virtualHost);
                this.virtualHost.setNode(virtualNode);
                this.virtualNode.updateState();
                return true;
            } catch (Throwable t) {
                AE2Wtx.LOG.warn("ae2wtx: failed to create label virtual node for '{}': {}", label, t.toString());
                this.virtualNode = null;
                this.virtualHost = null;
                return false;
            }
        }

        public synchronized void destroyVirtualNode() {
            if (virtualNode != null) {
                try {
                    virtualNode.destroy();
                } catch (Throwable ignored) {
                    // already gone
                }
            }
            virtualNode = null;
            virtualHost = null;
        }

        private NBTTagList saveEndpoints() {
            NBTTagList list = new NBTTagList();
            for (EndpointRef ref : endpoints) {
                list.appendTag(ref.save());
            }
            return list;
        }

        private void loadEndpoints(NBTTagList list) {
            endpoints.clear();
            for (int i = 0; i < list.tagCount(); i++) {
                endpoints.add(EndpointRef.load(list.getCompoundTagAt(i)));
            }
        }
    }

    private static final class EndpointRef {

        final Integer dim;
        final int x;
        final int y;
        final int z;

        EndpointRef(Integer dim, int x, int y, int z) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        boolean matches(Integer currentDim, int cx, int cy, int cz) {
            return Objects.equals(dim, currentDim) && x == cx && y == cy && z == cz;
        }

        NBTTagCompound save() {
            NBTTagCompound tag = new NBTTagCompound();
            if (dim != null) {
                tag.setInteger("dim", dim);
            }
            tag.setInteger("x", x);
            tag.setInteger("y", y);
            tag.setInteger("z", z);
            return tag;
        }

        static EndpointRef load(NBTTagCompound tag) {
            Integer dim = tag.hasKey("dim") ? tag.getInteger("dim") : null;
            return new EndpointRef(dim, tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"));
        }
    }

    /**
     * Virtual host for the label network's grid node. Not a world tile entity;
     * anchored at the overworld spawn point so the rv3 WorldGrid can place it.
     */
    static class VirtualLabelNodeHost implements IGridHost, IGridBlock {

        private final World anchorWorld;
        private final DimensionalCoord location;
        private IGridNode node;

        VirtualLabelNodeHost(World anchorWorld) {
            this.anchorWorld = anchorWorld;
            int sx = anchorWorld.getSpawnPoint().posX;
            int sy = anchorWorld.getSpawnPoint().posY;
            int sz = anchorWorld.getSpawnPoint().posZ;
            this.location = new DimensionalCoord(anchorWorld, sx, sy, sz);
        }

        void setNode(IGridNode node) {
            this.node = node;
        }

        /* IGridHost */
        @Override
        public IGridNode getGridNode(ForgeDirection dir) {
            return node;
        }

        @Override
        public AECableType getCableConnectionType(ForgeDirection dir) {
            return AECableType.GLASS;
        }

        @Override
        public void securityBreak() {}

        /* IGridBlock */
        @Override
        public double getIdlePowerUsage() {
            return 0.0D;
        }

        @Override
        public EnumSet<GridFlags> getFlags() {
            return EnumSet.of(GridFlags.DENSE_CAPACITY);
        }

        @Override
        public boolean isWorldAccessible() {
            return false;
        }

        @Override
        public DimensionalCoord getLocation() {
            return location;
        }

        @Override
        public AEColor getGridColor() {
            return AEColor.Transparent;
        }

        @Override
        public void onGridNotification(appeng.api.networking.GridNotification notification) {}

        @Override
        public void setNetworkStatus(appeng.api.networking.IGrid grid, int channelsInUse) {}

        @Override
        public EnumSet<ForgeDirection> getConnectableSides() {
            return EnumSet.allOf(ForgeDirection.class);
        }

        @Override
        public IGridHost getMachine() {
            return this;
        }

        @Override
        public void gridChanged() {}

        @Override
        public ItemStack getMachineRepresentation() {
            return new ItemStack(ModBlocks.blockLabeledWirelessTransceiver);
        }
    }
}
