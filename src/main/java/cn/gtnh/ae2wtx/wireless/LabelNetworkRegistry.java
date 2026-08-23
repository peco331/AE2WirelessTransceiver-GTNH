package cn.gtnh.ae2wtx.wireless;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
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
    private static final int DATA_VERSION = 3;
    public static final int MAX_PAGE_SIZE = 128;
    /** Owner key used when no placer is set (GTNH has no FTB Teams layer). */
    public static final UUID PUBLIC_NETWORK_UUID = new UUID(0, 0);

    private final Map<Key, LabelNetwork> networks = new HashMap<>();
    private final Map<EndpointRef, Key> endpointIndex = new HashMap<>();
    private final Set<PendingEndpointClear> pendingClears = new HashSet<>();
    private long nextChannel = CHANNEL_START;
    private boolean readRepairNeeded;

    public LabelNetworkRegistry() {
        super(SAVE_ID);
    }

    public LabelNetworkRegistry(String name) {
        super(name);
    }

    /* ===================== access ===================== */

    public static LabelNetworkRegistry get(World world) {
        World overworld = findOverworld(world);
        if (overworld == null) {
            return null;
        }
        LabelNetworkRegistry reg =
            (LabelNetworkRegistry) overworld.mapStorage.loadData(LabelNetworkRegistry.class, SAVE_ID);
        if (reg == null) {
            reg = new LabelNetworkRegistry();
            overworld.mapStorage.setData(SAVE_ID, reg);
        }
        return reg;
    }

    public static LabelNetworkRegistry getExisting(World world) {
        World overworld = findOverworld(world);
        return overworld == null
            ? null
            : (LabelNetworkRegistry) overworld.mapStorage.loadData(LabelNetworkRegistry.class, SAVE_ID);
    }

    private static World findOverworld(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        World overworld = world;
        if (world.provider.dimensionId != 0) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || server.worldServers == null || server.worldServers.length == 0
                || server.worldServers[0] == null) {
                return null;
            }
            overworld = server.worldServers[0];
        }
        return overworld;
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

    /**
     * Recovery/loader entry point. Existing saved endpoints must never be rejected by a newly lowered creation quota.
     */
    public synchronized LabelNetwork register(World beWorld, String rawLabel, UUID placerId, IWirelessEndpoint endpoint) {
        return registerInternal(beWorld, rawLabel, placerId, endpoint, false).network;
    }

    /** Player-requested entry point. Only creating a brand-new band consumes quota. */
    public synchronized RegistrationResult registerForPlayer(
        World beWorld,
        String rawLabel,
        UUID placerId,
        IWirelessEndpoint endpoint) {
        return registerInternal(beWorld, rawLabel, placerId, endpoint, true);
    }

    private RegistrationResult registerInternal(
        World beWorld,
        String rawLabel,
        UUID placerId,
        IWirelessEndpoint endpoint,
        boolean enforceCreationLimits) {
        if (beWorld == null || beWorld.isRemote || endpoint == null) {
            return RegistrationResult.failure(RegistrationStatus.CREATE_FAILED);
        }
        World endpointWorld = endpoint.getWorld();
        if (endpointWorld == null || endpointWorld.isRemote || endpointWorld != beWorld) {
            return RegistrationResult.failure(RegistrationStatus.CREATE_FAILED);
        }
        EndpointRef ref = EndpointRef.of(endpoint);
        String label = normalizeLabel(rawLabel);
        if (label == null) {
            return RegistrationResult.failure(RegistrationStatus.INVALID_LABEL);
        }
        UUID owner = placerId == null ? PUBLIC_NETWORK_UUID : placerId;
        Integer dim = ModConfig.wirelessCrossDimEnable ? null : beWorld.provider.dimensionId;
        Key key = new Key(dim, label, owner);
        LabelNetwork network = networks.get(key);
        boolean created = false;

        if (network == null) {
            if (enforceCreationLimits) {
                int ownerCount = countOwnerBands(key);
                int ownerLimit = ModConfig.wirelessMaxBandsPerOwner;
                if (ownerLimit == 0 || ownerCount >= ownerLimit) {
                    return RegistrationResult.limit(RegistrationStatus.OWNER_LIMIT_REACHED, ownerCount, ownerLimit);
                }
                int worldCount = networks.size();
                int worldLimit = ModConfig.wirelessMaxBandsPerWorld;
                if (worldLimit == 0 || worldCount >= worldLimit) {
                    return RegistrationResult.limit(RegistrationStatus.WORLD_LIMIT_REACHED, worldCount, worldLimit);
                }
            }

            long channel = nextAvailableChannel();
            network = new LabelNetwork(dim, label, owner, channel, beWorld.provider.dimensionId);
            if (!network.ensureVirtualNode(beWorld) && enforceCreationLimits) {
                return RegistrationResult.failure(RegistrationStatus.CREATE_FAILED);
            }
            networks.put(key, network);
            advanceNextChannel(channel);
            created = true;
        } else if (!network.ensureVirtualNode(beWorld) && enforceCreationLimits) {
            return RegistrationResult.failure(RegistrationStatus.CREATE_FAILED);
        }

        Key previousKey = endpointIndex.get(ref);
        boolean changed = created;
        if (previousKey != null && !previousKey.equals(key)) {
            changed |= removeEndpoint(previousKey, ref);
        }
        if (network.endpoints.add(ref)) {
            changed = true;
        }
        network.loadedEndpoints.add(ref);
        endpointIndex.put(ref, key);
        network.invalidateStats();
        if (changed) {
            markDirty();
        }
        return new RegistrationResult(
            created ? RegistrationStatus.CREATED : RegistrationStatus.ATTACHED_EXISTING,
            network,
            -1,
            -1);
    }

    public synchronized void reconcileEndpoint(
        World world,
        String rawLabel,
        UUID placerId,
        IWirelessEndpoint endpoint) {
        if (normalizeLabel(rawLabel) == null) {
            unregister(endpoint);
        } else {
            registerInternal(world, rawLabel, placerId, endpoint, false);
        }
    }

    public synchronized void unregister(IWirelessEndpoint endpoint) {
        if (endpoint == null || endpoint.getWorld() == null) {
            return;
        }
        EndpointRef ref = EndpointRef.of(endpoint);
        Key key = endpointIndex.remove(ref);
        boolean changed = key != null && removeEndpoint(key, ref);
        if (changed) {
            markDirty();
        }
    }

    public synchronized void invalidateStatsFor(IWirelessEndpoint endpoint) {
        if (endpoint == null || endpoint.getWorld() == null) {
            return;
        }
        Key key = endpointIndex.get(EndpointRef.of(endpoint));
        LabelNetwork network = key == null ? null : networks.get(key);
        if (network != null) {
            network.invalidateStats();
        }
    }

    /**
     * A chunk unload keeps the persistent endpoint reference but releases its
     * runtime lease. The virtual node is torn down once no loaded endpoint is
     * using the band, avoiding a World strong-reference leak through dormant
     * chunks.
     */
    public synchronized void suspend(IWirelessEndpoint endpoint) {
        if (endpoint == null || endpoint.getWorld() == null) {
            return;
        }
        EndpointRef ref = EndpointRef.of(endpoint);
        Key key = endpointIndex.get(ref);
        LabelNetwork network = key == null ? null : networks.get(key);
        if (network != null) {
            network.suspend(ref);
        }
    }

    private boolean removeEndpoint(Key key, EndpointRef ref) {
        LabelNetwork old = networks.get(key);
        if (old == null || !old.endpoints.remove(ref)) {
            return false;
        }
        old.loadedEndpoints.remove(ref);
        old.invalidateStats();
        if (old.loadedEndpoints.isEmpty()) {
            old.destroyVirtualNode();
        }
        return true;
    }

    private int countOwnerBands(Key target) {
        int count = 0;
        for (Key key : networks.keySet()) {
            if (Objects.equals(key.owner, target.owner) && Objects.equals(key.dim, target.dim)) {
                count++;
            }
        }
        return count;
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

    /**
     * Resolve the saved band assignment for a block position. This is used to
     * repair the tile-side label after an interrupted save: an explicit
     * disconnect removes the reverse-index entry first, so it cannot be
     * accidentally undone by this lookup.
     */
    public synchronized LabelNetwork getNetworkForEndpoint(int dimension, int x, int y, int z, UUID placerId) {
        Key key = endpointIndex.get(new EndpointRef(dimension, x, y, z));
        UUID owner = placerId == null ? PUBLIC_NETWORK_UUID : placerId;
        if (key == null || !Objects.equals(key.owner, owner)) {
            return null;
        }
        return networks.get(key);
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

            for (EndpointRef ref : snapshot) {
                endpointIndex.remove(ref);
                boolean cleared = false;
                if (ref.dim != null) {
                    World targetWorld = DimensionManager.getWorld(ref.dim);
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
            if (!Objects.equals(key.owner, owner) || !Objects.equals(key.dim, dim)) {
                continue;
            }
            list.add(new Snapshot(key.label, entry.getValue().channel));
        }
        list.sort(Comparator.comparingLong(s -> s.channel));
        return list;
    }

    public synchronized PagedSnapshots listNetworks(
        World world,
        UUID placerId,
        String rawQuery,
        int requestedPage,
        int requestedPageSize) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        List<Snapshot> all = listNetworks(world, placerId);
        if (!query.isEmpty()) {
            all.removeIf(snapshot -> !snapshot.label.contains(query));
        }
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedPageSize));
        int total = all.size();
        int pageCount = Math.max(1, (total + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int from = Math.min(total, page * pageSize);
        int to = Math.min(total, from + pageSize);
        return new PagedSnapshots(new ArrayList<>(all.subList(from, to)), page, pageSize, total, pageCount);
    }

    public synchronized int countBands(World world, UUID placerId) {
        return listNetworks(world, placerId).size();
    }

    public synchronized void unloadRuntimeNodes(int unloadingDimension) {
        for (LabelNetwork network : networks.values()) {
            network.suspendDimension(unloadingDimension);
            if (unloadingDimension == 0 || Objects.equals(network.dim, unloadingDimension)
                || network.loadedEndpoints.isEmpty()) {
                network.destroyVirtualNode();
            }
        }
    }

    public synchronized void unloadAllRuntimeNodes() {
        for (LabelNetwork network : networks.values()) {
            network.loadedEndpoints.clear();
            network.destroyVirtualNode();
        }
    }

    private long nextAvailableChannel() {
        return nextAvailableChannel(Collections.emptySet());
    }

    private long nextAvailableChannel(Set<Long> reserved) {
        long candidate = nextChannel;
        if (candidate < CHANNEL_START || candidate == Long.MAX_VALUE) {
            candidate = CHANNEL_START;
        }
        long start = candidate;
        while (isChannelInUse(candidate) || reserved.contains(candidate)) {
            candidate = candidate == Long.MAX_VALUE - 1L ? CHANNEL_START : candidate + 1L;
            if (candidate == start) {
                throw new IllegalStateException("No wireless channel id is available");
            }
        }
        return candidate;
    }

    private boolean isChannelInUse(long channel) {
        for (LabelNetwork network : networks.values()) {
            if (network.channel == channel) {
                return true;
            }
        }
        return false;
    }

    private void advanceNextChannel(long allocated) {
        nextChannel = allocated >= Long.MAX_VALUE - 1L ? CHANNEL_START : allocated + 1L;
    }

    /* ===================== serialization ===================== */

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        this.readRepairNeeded = false;
        int loadedVersion = tag.hasKey("dataVersion") ? tag.getInteger("dataVersion") : 1;
        String loadedScope = tag.hasKey("scopeMode") ? tag.getString("scopeMode") : "legacy";
        long savedNextChannel = tag.getLong("nextChannel");
        this.nextChannel = savedNextChannel >= CHANNEL_START && savedNextChannel < Long.MAX_VALUE
            ? savedNextChannel
            : CHANNEL_START;
        if (savedNextChannel != 0L && savedNextChannel != this.nextChannel) {
            this.readRepairNeeded = true;
        }
        List<LabelNetwork> loadedNetworks = new ArrayList<>();
        NBTTagList list = tag.getTagList("networks", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound nbt = list.getCompoundTagAt(i);
            try {
                String label = normalizeLabel(nbt.getString("label"));
                UUID owner = parseUuid(nbt.getString("owner"), null);
                if (label == null || owner == null) {
                    AE2Wtx.LOG.warn("ae2wtx: skipping invalid saved band at index {}", i);
                    this.readRepairNeeded = true;
                    continue;
                }
                Integer dim = nbt.hasKey("dim", 99) ? nbt.getInteger("dim") : null;
                long channel = nbt.getLong("channel");
                int originDim = nbt.hasKey("originDim", 99)
                    ? nbt.getInteger("originDim")
                    : dim == null ? 0 : dim;
                LabelNetwork net = new LabelNetwork(dim, label, owner, channel, originDim);
                if (net.loadEndpoints(nbt.getTagList("endpoints", 10))) {
                    this.readRepairNeeded = true;
                }
                if (!nbt.hasKey("originDim", 99) && dim == null && !net.endpoints.isEmpty()) {
                    net.originDim = net.endpoints.stream()
                        .filter(ref -> ref.dim != null)
                        .mapToInt(ref -> ref.dim)
                        .min()
                        .orElse(0);
                    this.readRepairNeeded = true;
                }
                loadedNetworks.add(net);
                if (channel >= CHANNEL_START && channel < Long.MAX_VALUE) {
                    nextChannel = Math.max(nextChannel, channel + 1L);
                }
            } catch (RuntimeException badRecord) {
                AE2Wtx.LOG.warn("ae2wtx: skipping corrupt saved band at index {}: {}", i, badRecord.toString());
                this.readRepairNeeded = true;
            }
        }
        if (nextChannel == Long.MAX_VALUE) {
            nextChannel = CHANNEL_START;
            this.readRepairNeeded = true;
        }
        loadedNetworks = repairLoadedChannels(loadedNetworks);
        normalizeLoadedNetworks(loadedNetworks, ModConfig.wirelessCrossDimEnable);
        rebuildEndpointIndex();

        this.pendingClears.clear();
        if (tag.hasKey("pendingClears", 9)) {
            NBTTagList pList = tag.getTagList("pendingClears", 10);
            for (int i = 0; i < pList.tagCount(); i++) {
                PendingEndpointClear clear = PendingEndpointClear.tryLoad(pList.getCompoundTagAt(i));
                if (clear != null) {
                    pendingClears.add(clear);
                } else {
                    AE2Wtx.LOG.warn("ae2wtx: skipping corrupt pending-clear record at index {}", i);
                    this.readRepairNeeded = true;
                }
            }
        }
        String desiredScope = ModConfig.wirelessCrossDimEnable ? "global" : "dimension";
        if (this.readRepairNeeded || loadedVersion != DATA_VERSION || !desiredScope.equals(loadedScope)) {
            markDirty();
            AE2Wtx.LOG.info(
                "ae2wtx: migrated band registry data v{} ({}) to v{} ({})",
                loadedVersion,
                loadedScope,
                DATA_VERSION,
                desiredScope);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("dataVersion", DATA_VERSION);
        tag.setString("scopeMode", ModConfig.wirelessCrossDimEnable ? "global" : "dimension");
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
            nbt.setInteger("originDim", e.getValue().originDim);
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

    private static UUID parseUuid(String raw, UUID fallback) {
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private void normalizeLoadedNetworks(List<LabelNetwork> loaded, boolean crossDimensional) {
        networks.clear();
        Set<Long> reservedChannels = new HashSet<>();
        for (LabelNetwork source : loaded) {
            reservedChannels.add(source.channel);
        }
        if (crossDimensional) {
            for (LabelNetwork source : loaded) {
                mergeLoadedNetwork(
                    new Key(null, source.label, source.owner),
                    source.channel,
                    source.endpoints,
                    source.originDim);
            }
            return;
        }

        for (LabelNetwork source : loaded) {
            Map<Integer, Set<EndpointRef>> byDimension = new LinkedHashMap<>();
            List<Integer> dimensions = new ArrayList<>();
            for (EndpointRef ref : source.endpoints) {
                int realDim = ref.dim == null ? 0 : ref.dim;
                if (!byDimension.containsKey(realDim)) {
                    byDimension.put(realDim, new HashSet<>());
                    dimensions.add(realDim);
                }
                byDimension.get(realDim).add(ref);
            }
            if (dimensions.isEmpty()) {
                int emptyDim = source.dim == null ? source.originDim : source.dim;
                dimensions.add(emptyDim);
                byDimension.put(emptyDim, Collections.emptySet());
            }
            Collections.sort(dimensions);
            boolean keptOriginalChannel = false;
            for (Integer dimension : dimensions) {
                long channel;
                if (!keptOriginalChannel) {
                    channel = source.channel;
                    keptOriginalChannel = true;
                } else {
                    channel = nextAvailableChannel(reservedChannels);
                    advanceNextChannel(channel);
                    reservedChannels.add(channel);
                }
                mergeLoadedNetwork(
                    new Key(dimension, source.label, source.owner),
                    channel,
                    byDimension.get(dimension),
                    dimension);
            }
        }
    }

    private List<LabelNetwork> repairLoadedChannels(List<LabelNetwork> loaded) {
        List<LabelNetwork> repaired = new ArrayList<>(loaded.size());
        Set<Long> usedChannels = new HashSet<>();
        Set<Long> reservedChannels = new HashSet<>();
        for (LabelNetwork source : loaded) {
            if (source.channel >= CHANNEL_START && source.channel < Long.MAX_VALUE) {
                reservedChannels.add(source.channel);
            }
        }
        for (LabelNetwork source : loaded) {
            long channel = source.channel;
            if (channel < CHANNEL_START || channel == Long.MAX_VALUE || !usedChannels.add(channel)) {
                do {
                    channel = nextAvailableChannel(reservedChannels);
                    advanceNextChannel(channel);
                } while (!usedChannels.add(channel));
                reservedChannels.add(channel);
                AE2Wtx.LOG.warn("ae2wtx: reassigned invalid or duplicate channel for band '{}' to {}", source.label, channel);
                this.readRepairNeeded = true;
            }
            LabelNetwork replacement =
                new LabelNetwork(source.dim, source.label, source.owner, channel, source.originDim);
            replacement.endpoints.addAll(source.endpoints);
            repaired.add(replacement);
        }
        return repaired;
    }

    private void mergeLoadedNetwork(Key key, long channel, Set<EndpointRef> endpoints, int originDim) {
        LabelNetwork current = networks.get(key);
        if (current == null) {
            current = new LabelNetwork(key.dim, key.label, key.owner, channel, originDim);
            current.endpoints.addAll(endpoints);
            networks.put(key, current);
            return;
        }
        this.readRepairNeeded = true;
        if (channel < current.channel) {
            LabelNetwork replacement = new LabelNetwork(key.dim, key.label, key.owner, channel, originDim);
            replacement.endpoints.addAll(current.endpoints);
            replacement.endpoints.addAll(endpoints);
            networks.put(key, replacement);
        } else {
            current.endpoints.addAll(endpoints);
        }
    }

    private void rebuildEndpointIndex() {
        endpointIndex.clear();
        List<Map.Entry<Key, LabelNetwork>> ordered = new ArrayList<>(networks.entrySet());
        ordered.sort(Comparator.comparingLong(entry -> entry.getValue().channel));
        for (Map.Entry<Key, LabelNetwork> entry : ordered) {
            Iterator<EndpointRef> iterator = entry.getValue().endpoints.iterator();
            while (iterator.hasNext()) {
                EndpointRef ref = iterator.next();
                if (endpointIndex.putIfAbsent(ref, entry.getKey()) != null) {
                    iterator.remove();
                    this.readRepairNeeded = true;
                    AE2Wtx.LOG.warn("ae2wtx: removed duplicate saved endpoint {}", ref);
                }
            }
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

    public static final class PagedSnapshots {

        public final List<Snapshot> entries;
        public final int page;
        public final int pageSize;
        public final int totalEntries;
        public final int pageCount;

        PagedSnapshots(List<Snapshot> entries, int page, int pageSize, int totalEntries, int pageCount) {
            this.entries = entries;
            this.page = page;
            this.pageSize = pageSize;
            this.totalEntries = totalEntries;
            this.pageCount = pageCount;
        }
    }

    public enum RegistrationStatus {
        ATTACHED_EXISTING,
        CREATED,
        OWNER_LIMIT_REACHED,
        WORLD_LIMIT_REACHED,
        INVALID_LABEL,
        CREATE_FAILED
    }

    public static final class RegistrationResult {

        public final RegistrationStatus status;
        public final LabelNetwork network;
        public final int currentCount;
        public final int limit;

        RegistrationResult(RegistrationStatus status, LabelNetwork network, int currentCount, int limit) {
            this.status = status;
            this.network = network;
            this.currentCount = currentCount;
            this.limit = limit;
        }

        static RegistrationResult failure(RegistrationStatus status) {
            return new RegistrationResult(status, null, -1, -1);
        }

        public static RegistrationResult createFailed() {
            return failure(RegistrationStatus.CREATE_FAILED);
        }

        static RegistrationResult limit(RegistrationStatus status, int currentCount, int limit) {
            return new RegistrationResult(status, null, currentCount, limit);
        }

        public boolean succeeded() {
            return network != null;
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
        int originDim;
        final Set<EndpointRef> endpoints = new HashSet<>();
        final Set<EndpointRef> loadedEndpoints = new HashSet<>();

        private IGridNode virtualNode;
        private VirtualLabelNodeHost virtualHost;

        private int cachedTotalUsedChannels = 0;
        private int cachedOnlineEndpointCount = 0;
        private long lastStatsWorldTime = -1;
        private long nextVirtualNodeRetryTick = 0L;

        LabelNetwork(Integer dim, String label, UUID owner, long channel, int originDim) {
            this.dim = dim;
            this.label = label;
            this.owner = owner;
            this.channel = channel;
            this.originDim = originDim;
        }

        public long channel() {
            return channel;
        }

        public String label() {
            return label;
        }

        public Integer dim() {
            return dim;
        }

        public UUID owner() {
            return owner;
        }

        public IGridNode node() {
            return virtualNode;
        }

        public int endpointCount() {
            return endpoints.size();
        }

        private synchronized void suspend(EndpointRef ref) {
            loadedEndpoints.remove(ref);
            invalidateStats();
            if (loadedEndpoints.isEmpty()) {
                destroyVirtualNode();
            }
        }

        private synchronized void suspendDimension(int unloadingDimension) {
            loadedEndpoints.removeIf(ref -> ref.dim != null && ref.dim == unloadingDimension);
            invalidateStats();
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
                World w = DimensionManager.getWorld(ref.dim);
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
                    UUID endpointOwner = lte.getPlacerId() == null ? PUBLIC_NETWORK_UUID : lte.getPlacerId();
                    if (!Objects.equals(label, lte.getLabelForDisplay()) || !Objects.equals(owner, endpointOwner)) {
                        continue;
                    }
                    if (lte.isOnline()) {
                        online++;
                    }
                    total = (int) Math.min(Integer.MAX_VALUE, (long) total + lte.getUsedChannelsForDisplay());
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
            if (virtualNode != null) {
                return true;
            }
            if (anyWorld == null || anyWorld.isRemote || MinecraftServer.getServer() == null) {
                return false;
            }
            long now = anyWorld.getTotalWorldTime();
            if (now < nextVirtualNodeRetryTick) {
                return false;
            }
            int hostDimension = dim == null ? 0 : dim;
            World hostLevel = anyWorld.provider.dimensionId == hostDimension ? anyWorld : DimensionManager.getWorld(hostDimension);
            if (hostLevel == null) {
                nextVirtualNodeRetryTick = now + 100L;
                return false;
            }
            IGridNode createdNode = null;
            try {
                this.virtualHost = new VirtualLabelNodeHost(hostLevel, this);
                createdNode = AEApi.instance().createGridNode(virtualHost);
                this.virtualNode = createdNode;
                this.virtualHost.setNode(createdNode);
                if (!PUBLIC_NETWORK_UUID.equals(owner)) {
                    int playerId = AEApi.instance().registries().players().getID(new GameProfile(owner, ""));
                    if (playerId >= 0) {
                        createdNode.setPlayerID(playerId);
                    }
                }
                createdNode.updateState();
                // AE2 may synchronously call securityBreak while updateState
                // joins a protected grid. In that case the callback cleared
                // our fields and the attempted node must not be reported live.
                if (this.virtualNode != createdNode) {
                    return false;
                }
                this.nextVirtualNodeRetryTick = 0L;
                return true;
            } catch (Exception e) {
                AE2Wtx.LOG.warn("ae2wtx: failed to create label virtual node for '{}': {}", label, e.toString());
                if (createdNode != null) {
                    try {
                        createdNode.destroy();
                    } catch (RuntimeException ignored) {
                        // partially-created node was already torn down
                    }
                }
                this.virtualNode = null;
                this.virtualHost = null;
                this.nextVirtualNodeRetryTick = now + 100L;
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

        private synchronized void securityBreak(long worldTime) {
            AE2Wtx.LOG.warn("ae2wtx: security rejected virtual node for band '{}' owned by {}", label, owner);
            destroyVirtualNode();
            nextVirtualNodeRetryTick = Math.max(nextVirtualNodeRetryTick, worldTime + 100L);
        }

        private NBTTagList saveEndpoints() {
            NBTTagList list = new NBTTagList();
            for (EndpointRef ref : endpoints) {
                list.appendTag(ref.save());
            }
            return list;
        }

        private boolean loadEndpoints(NBTTagList list) {
            endpoints.clear();
            boolean repaired = false;
            for (int i = 0; i < list.tagCount(); i++) {
                try {
                    if (!endpoints.add(EndpointRef.load(list.getCompoundTagAt(i)))) {
                        repaired = true;
                    }
                } catch (RuntimeException badEndpoint) {
                    repaired = true;
                    AE2Wtx.LOG.warn(
                        "ae2wtx: skipping corrupt endpoint {} for band '{}': {}",
                        i,
                        label,
                        badEndpoint.toString());
                }
            }
            return repaired;
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

        static PendingEndpointClear tryLoad(NBTTagCompound tag) {
            if (tag == null || !tag.hasKey("dim", 99) || !tag.hasKey("x", 99) || !tag.hasKey("y", 99)
                || !tag.hasKey("z", 99)) {
                return null;
            }
            int dim = tag.getInteger("dim");
            int x = tag.getInteger("x");
            int y = tag.getInteger("y");
            int z = tag.getInteger("z");
            String label = normalizeLabel(tag.getString("label"));
            UUID owner = tag.hasKey("owner") ? parseUuid(tag.getString("owner"), null) : PUBLIC_NETWORK_UUID;
            if (label == null || owner == null || y < 0 || y > 255) {
                return null;
            }
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

        static EndpointRef of(IWirelessEndpoint endpoint) {
            World world = endpoint.getWorld();
            return new EndpointRef(world.provider.dimensionId, endpoint.getX(), endpoint.getY(), endpoint.getZ());
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
            if (tag == null || !tag.hasKey("x", 99) || !tag.hasKey("y", 99) || !tag.hasKey("z", 99)) {
                throw new IllegalArgumentException("endpoint coordinates missing or not numeric");
            }
            // legacy data may lack "dim" (global refs) - assume overworld
            int d = tag.hasKey("dim", 99) ? tag.getInteger("dim") : 0;
            int y = tag.getInteger("y");
            if (y < 0 || y > 255) {
                throw new IllegalArgumentException("endpoint y out of range: " + y);
            }
            return new EndpointRef(d, tag.getInteger("x"), y, tag.getInteger("z"));
        }

        @Override
        public String toString() {
            return "EndpointRef{" + dim + ":" + x + "," + y + "," + z + "}";
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
        private final LabelNetwork network;
        private IGridNode node;

        VirtualLabelNodeHost(World anchorWorld, LabelNetwork network) {
            this.anchorWorld = anchorWorld;
            this.network = network;
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
        public void securityBreak() {
            network.securityBreak(anchorWorld.getTotalWorldTime());
        }

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
