package cn.gtnh.ae2wtx.wireless;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.init.ModItems;
import cn.gtnh.ae2wtx.item.ChannelCardItem;
import appeng.api.networking.IGridNode;

/**
 * Manages wireless links for AE2 devices that carry a channel card in their
 * upgrade inventory (ME Interface, import/export/storage bus). Each device is
 * ticked from the device's own grid tick; the card's channel+owner configure a
 * {@link WirelessSlaveLink} that wirelessly joins the device's grid node to the
 * matching master transceiver's node (cross-dimension), mirroring
 * ExtendedAE_Plus' channel-card auto-connect.
 * <p>
 * Links are keyed by device identity in a WeakHashMap; when the device (and its
 * node) is removed, the grid connection dies with the node and the entry is
 * collected.
 */
public final class DeviceWirelessLinkManager {

    private DeviceWirelessLinkManager() {}

    private static final Map<Object, ManagedLink> LINKS = new WeakHashMap<>();

    /** Called from the device's per-tick hook. */
    public static void tick(Object device, IInventory upgrades, World world, int x, int y, int z,
        Supplier<IGridNode> nodeSupplier, BooleanSupplier removedSupplier) {
        ManagedLink link = LINKS.get(device);
        if (link == null) {
            link = new ManagedLink(new DeviceEndpoint(() -> world, nodeSupplier, removedSupplier, x, y, z));
            LINKS.put(device, link);
        }
        link.tick(upgrades);
    }

    private static final class ManagedLink {

        private final WirelessSlaveLink slave;
        private long lastChannel = 0L;
        private UUID lastOwner;

        ManagedLink(IWirelessEndpoint endpoint) {
            this.slave = new WirelessSlaveLink(endpoint);
        }

        void tick(IInventory upgrades) {
            if (upgrades == null || slave.hostRemoved()) {
                teardown();
                return;
            }
            long channel = 0L;
            UUID owner = null;
            boolean found = false;
            for (int i = 0; i < upgrades.getSizeInventory(); i++) {
                ItemStack s = upgrades.getStackInSlot(i);
                if (s != null && s.getItem() == ModItems.itemChannelCard) {
                    channel = ChannelCardItem.getChannel(s);
                    owner = ChannelCardItem.getOwnerUUID(s);
                    found = true;
                    break;
                }
            }
            if (!found) {
                teardown();
                return;
            }
            if (channel != lastChannel || !Objects.equals(owner, lastOwner)) {
                lastChannel = channel;
                lastOwner = owner;
                slave.setPlacerId(owner);
                slave.setFrequency(channel);
            } else {
                slave.updateStatus();
            }
        }

        private void teardown() {
            if (lastChannel != 0L || slave.isConnected()) {
                lastChannel = 0L;
                lastOwner = null;
                slave.setPlacerId(null);
                slave.setFrequency(0L); // destroys any live connection
            }
        }
    }
}
