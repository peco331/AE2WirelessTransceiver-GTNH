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
    /** Owner key used when no placer is set (GTNH has no FTB Teams layer). */
    public static final UUID PUBLIC_NETWORK_UUID = new UUID(0, 0);

    private final Map<Key, LabelNetwork> networks = new HashMap<>();
    private final Set<PendingEndpointClear> pendingClears = new HashSet<>();
    private long nextChannel = CHANNEL_START;

    public LabelNetworkRegistry() {
        super(SAVE_ID);
    }

    public LabelNetworkRegistry(String name) {
        super(name);
    }

    /* ===================== access ===================== */

    /**
     * Cached per-overworld instances: loadData() re-reads and re-parses NBT
     * from disk on every call, and band stats query this every 20 ticks per
     * transceiver. Weak keys let the cache drop with the world.
     */
    private static final java.util.Map<World, LabelNetworkRegistry> INSTANCE_CACHE = new java.util.WeakHashMap<>();

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
        LabelNetworkRegistry reg = INSTANCE_CACHE.get(overworld);
        if (reg == null) {
            reg = (LabelNetworkRegistry) overworld.mapStorage.loadData(LabelNetworkRegistry.class, SAVE_ID);
            if (reg == null) {
                reg = new LabelNetworkRegistry();
                overworld.mapStorage.setData(SAVE_ID, reg);
            }
            INSTANCE_CACHE.put(overworld, reg);
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
        UUID owner = placerId == null ? PUBLIC_NETWORK_UUID : placerId;
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
        // Endpoint refs always store the REAL dimension of the endpoint so
        // they can be located for stats even when the network key is global.
        network.endpoints.add(new EndpointRef(beWorld.provider.dimensionId, endpoint.getX(), endpoint.getY(), endpoint.getZ()));
        network.invalidateStats();
        markDirty();
        return network;
    }

    public synchronized void unregister(IWirelessEndpoint endpoint) {
        World world = endpoint.getWorld();
        if (world == null) {
            return;
        }
        int realDim = world.provider.dimensionId;
        for (LabelNetwork net : networks.values()) {
            if (net.endpoints.removeIf(ref -> ref.matches(realDim, endpoint.getX(), endpoint.getY(), endpoint.getZ()))) {
                net.invalidateStats();
            }
        }
        markDirty();
    }

    public synchronized LabelNetwork getNetwork(World world, String rawLabel, UUID placerId) {
        String label = normalizeLabel(rawLabel);
        if (label == null) {
            return null;
        }
        UUID owner = placerId == null ? PUBLIC_NETWORK_UUID : placerId;
        Integer dim = ModConfig.wirelessCrossDimEnable ? null : world.provider.dimensionId;
        return networks.get(new Key(dim, label, owner));
    }

    public synchronized boolean removeNetwork(World world, String rawLabel, UUID placerId) {
        String label = normalizeLabel(rawLabel);
        if (label == null) {
            return false;
        }
        UUID owner = placerId == null ? PUBLIC_NETWORK_UUID : placerId;
        Integer dim = ModConfig.wirelessCrossDimEnable ? null : world.provider.dimensionId;
        Key key = new Key(dim, label, owner);
        LabelNetwork net = networks.remove(key);
        if (net != null) {
            List<EndpointRef> snapshot = new ArrayList<>(net.endpoints);
            net.destroyVirtualNode();

            MinecraftServer server = MinecraftServer.getServer();
            for (EndpointRef ref : snapshot) {
                boolean cleared = false;
                if (server != null && ref.dim != null) {
                    World targetWorld = server.worldServerForDimension(ref.dim);
                    if (targetWorld != null && targetWorld.blockExists(ref.x, ref.y, ref.z)) {
                        TileEntity te = targetWorld.getTileEntity(ref.x, ref.y, ref.z);
                        if (te instanceof cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity) {
                            cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity lte =
                                (cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity) te;
                            if (Objects.equals(label, lte.getLabelForDisplay())
                                && Objects.equals(owner, lte.getPlacerId() == null ? PUBLIC_NETWORK_UUID : lte.getPlacerId())) {
                                lte.clearLabelAfterNetworkDeletion();
                                cleared = true;
                            }
                        }
                    }
                }
                if (!cleared && ref.dim != null) {
                    pendingClears.add(new PendingEndpointClear(ref.dim, ref.x, ref.y, ref.z, label, owner));
                }
            }
            markDirty();
            return true;
        }
        return false;
    }

    public synchronized boolean checkAndConsumePendingClear(World world, int x, int y, int z, String currentLabel, UUID placerId) {
        if (world == null || currentLabel == null || currentLabel.isEmpty() || pendingClears.isEmpty()) {
            return false;
        }
        int dim = world.provider.dimensionId;
        UUID owner = placerId == null ? PUBLIC_NETWORK_UUID : placerId;
        String normalized = normalizeLabel(currentLabel);
        if (normalized == null) {
            return false;
        }

        PendingEndpointClear match = null;
        for (PendingEndpointClear pc : pendingClears) {
            if (pc.dim == dim && pc.x == x && pc.y == y && pc.z == z) {
                if (Objects.equals(pc.label, normalized) && Objects.equals(pc.owner, owner)) {
                    match = pc;
                    break;
                }
            }
        }
        if (match != null) {
            pendingClears.remove(match);
            markDirty();
            return true;
        }
        return false;
    }

    public synchronized void cleanupStalePendingClear(World world, int x, int y, int z) {
        if (world == null || pendingClears.isEmpty()) {
            return;
        }
        int dim = world.provider.dimensionId;
        if (pendingClears.removeIf(pc -> pc.dim == dim && pc.x == x && pc.y == y && pc.z == z)) {
            markDirty();
        }
    }

    public synchronized List<Snapshot> listNetworks(World world, UUID placerId) {
        UUID owner = placerId == null ? PUBLIC_NETWORK_UUID : placerId;
        Integer dim = ModConfig.wirelessCrossDimEnable ? null : (world != null ? world.provider.dimensionId : 0);
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
        this.pendingClears.clear();
        if (tag.hasKey("pendingClears", 9)) {
            NBTTagList pList = tag.getTagList("pendingClears", 10);
            for (int i = 0; i < pList.tagCount(); i++) {
                pendingClears.add(PendingEndpointClear.load(pList.getCompoundTagAt(i)));
            }
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
        if (!pendingClears.isEmpty()) {
            NBTTagList pList = new NBTTagList();
            for (PendingEndpointClear pc : pendingClears) {
                pList.appendTag(pc.save());
            }
            tag.setTag("pendingClears", pList);
        }
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

        private int cachedTotalUsedChannels = 0;
        private int cachedOnlineEndpointCount = 0;
        private long lastStatsWorldTime = -1;

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

        private synchronized void updateStatsIfNeeded() {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || server.worldServers == null || server.worldServers.length == 0) {
                return;
            }
            long time = server.worldServers[0].getTotalWorldTime();
            if (lastStatsWorldTime != -1 && time >= lastStatsWorldTime && (time - lastStatsWorldTime) < 20) {
                return;
            }
            lastStatsWorldTime = time;

            int total = 0;
            int online = 0;
            for (EndpointRef ref : endpoints) {
                if (ref.dim == null) {
                    continue;
                }
                World w = server.worldServerForDimension(ref.dim);
                if (w == null) {
                    continue;
                }
                if (!w.blockExists(ref.x, ref.y, ref.z)) {
                    continue;
                }
                TileEntity te = w.getTileEntity(ref.x, ref.y, ref.z);
                if (te instanceof cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity) {
                    cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity lte =
                        (cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity) te;
                    if (lte.isOnline()) {
                        online++;
                    }
                    total += lte.getUsedChannelsForDisplay();
                }
            }
            this.cachedTotalUsedChannels = total;
            this.cachedOnlineEndpointCount = online;
        }

        /**
         * Number of transceivers in this band that are ACTUALLY online
         * (tile exists and its label link is connected). Stale endpoint refs
         * are skipped, unlike {@link #endpointCount()}.
         */
        public int onlineEndpointCount() {
            updateStatsIfNeeded();
            return cachedOnlineEndpointCount;
        }

        /**
         * Sum of the channels currently used by all endpoints of this label
         * network (server-side only). Endpoints whose tile is gone or not a
         * transceiver are skipped (stale refs). Gives players a view of the
         * whole label's channel usage so they know how many channels remain.
         */
        public int totalUsedChannels() {
            updateStatsIfNeeded();
            return cachedTotalUsedChannels;
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
            if (server == null) {
                return false;
            }
            World hostLevel;
            if (dim == null) {
                hostLevel = server.worldServerForDimension(0);
            } else {
                hostLevel = server.worldServerForDimension(dim);
            }
            if (hostLevel == null) {
                return false;
            }
            try {
                this.virtualHost = new VirtualLabelNodeHost(hostLevel);
                this.virtualNode = AEApi.instance().createGridNode(virtualHost);
                this.virtualHost.setNode(virtualNode);
                this.virtualNode.updateState();
                return true;
            } catch (Exception e) {
                AE2Wtx.LOG.warn("ae2wtx: failed to create label virtual node for '{}': {}", label, e.toString());
                this.virtualNode = null;
                this.virtualHost = null;
                return false;
            }
        }

        public synchronized void invalidateStats() {
            this.lastStatsWorldTime = -1;
        }

        public synchronized void destroyVirtualNode() {
            if (virtualNode != null) {
                try {
                    virtualNode.destroy();
                } catch (Exception ignored) {
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

    public static final class PendingEndpointClear {

        public final int dim;
        public final int x;
        public final int y;
        public final int z;
        public final String label;
        public final UUID owner;

        public PendingEndpointClear(int dim, int x, int y, int z, String label, UUID owner) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.label = label;
            this.owner = owner;
        }

        NBTTagCompound save() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("dim", dim);
            tag.setInteger("x", x);
            tag.setInteger("y", y);
            tag.setInteger("z", z);
            if (label != null) {
                tag.setString("label", label);
            }
            if (owner != null) {
                tag.setString("owner", owner.toString());
            }
            return tag;
        }

        static PendingEndpointClear load(NBTTagCompound tag) {
            int dim = tag.getInteger("dim");
            int x = tag.getInteger("x");
            int y = tag.getInteger("y");
            int z = tag.getInteger("z");
            String label = tag.getString("label");
            UUID owner = tag.hasKey("owner") ? UUID.fromString(tag.getString("owner")) : PUBLIC_NETWORK_UUID;
            return new PendingEndpointClear(dim, x, y, z, label, owner);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PendingEndpointClear)) {
                return false;
            }
            PendingEndpointClear that = (PendingEndpointClear) o;
            return dim == that.dim && x == that.x && y == that.y && z == that.z
                && Objects.equals(label, that.label) && Objects.equals(owner, that.owner);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dim, x, y, z, label, owner);
        }
    }

    public static final class EndpointRef {

        public final Integer dim;
        public final int x;
        public final int y;
        public final int z;

        EndpointRef(Integer dim, int x, int y, int z) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        boolean matches(int currentDim, int cx, int cy, int cz) {
            return dim != null && dim == currentDim && x == cx && y == cy && z == cz;
        }

        NBTTagCompound save() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("dim", dim != null ? dim : 0);
            tag.setInteger("x", x);
            tag.setInteger("y", y);
            tag.setInteger("z", z);
            return tag;
        }

        static EndpointRef load(NBTTagCompound tag) {
            // legacy data may lack "dim" (global refs) - assume overworld
            int d = tag.hasKey("dim") ? tag.getInteger("dim") : 0;
            return new EndpointRef(d, tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EndpointRef)) {
                return false;
            }
            EndpointRef that = (EndpointRef) o;
            return x == that.x && y == that.y && z == that.z && Objects.equals(dim, that.dim);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dim, x, y, z);
        }
    }

    /**
     * Virtual host for the label network's grid node. Not a world tile entity;
     * anchored at the overworld spawn point so the rv3 WorldGrid can place it.
     */
    public static class VirtualLabelNodeHost implements IGridHost, IGridBlock {

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
            return new ItemStack(ModBlocks.blockWirelessTransceiver);
        }
    }
}
