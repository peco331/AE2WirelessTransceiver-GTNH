package cn.gtnh.ae2wtx.compat;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.content.wireless.TransceiverStatus;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;

/** Waila provider for the wireless transceiver: frequency/owner/channels/lock/status. */
public class WailaProvider implements IWailaDataProvider {

    public static void callback(mcp.mobius.waila.api.IWailaRegistrar registrar) {
        registrar.registerBodyProvider(new WailaProvider(), LabeledWirelessTransceiverBlockEntity.class);
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip;
    }

    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        TileEntity te = accessor.getTileEntity();
        if (te instanceof LabeledWirelessTransceiverBlockEntity) {
            LabeledWirelessTransceiverBlockEntity lte = (LabeledWirelessTransceiverBlockEntity) te;
            String label = lte.getLabelForDisplay();
            currenttip.add(StatCollector.translateToLocalFormatted("ae2wtx.waila.frequency",
                label == null || label.isEmpty() ? "-" : label));
            java.util.UUID owner = lte.getPlacerId();
            if (owner == null) {
                currenttip.add(StatCollector.translateToLocal("ae2wtx.waila.owner.public"));
            } else {
                String name = lte.getPlacerName();
                currenttip.add(StatCollector.translateToLocalFormatted("ae2wtx.waila.owner",
                    name != null ? name : owner.toString().substring(0, 8)));
            }
            // channels used by THIS transceiver (denominator = capacity the ME
            // network actually grants: 32 dense, infinite mode shows plain count)
            int used = lte.getUsedChannelsForDisplay();
            int max = lte.getMaxChannelsForDisplay();
            if (max <= 0 || max >= 1_000_000) {
                currenttip.add(StatCollector.translateToLocalFormatted("ae2wtx.waila.channels", used));
            } else {
                currenttip.add(StatCollector.translateToLocalFormatted(
                    "ae2wtx.waila.channels_of",
                    ChannelDisplayFormatter.colorizeUsed(used, max),
                    max));
            }
            // channels used by ALL endpoints of this frequency (label) - wording
            // matches the GUI "本频率频道 x/y"; over capacity -> red, full -> yellow
            int net = lte.getNetworkChannelsForDisplay();
            String netVal = ChannelDisplayFormatter.formatBand(net, max);
            currenttip.add(StatCollector.translateToLocal("ae2wtx.waila.channels_network_label") + netVal);
            // transceivers online in this band
            currenttip.add(StatCollector.translateToLocalFormatted("ae2wtx.waila.band_online",
                lte.getOnlineCountForDisplay()));
            currenttip.add(StatCollector.translateToLocal(
                lte.isLocked() ? "ae2wtx.chat.transceiver.locked_status"
                    : "ae2wtx.chat.transceiver.unlocked_status"));
            TransceiverStatus status = lte.getStatusForDisplay();
            currenttip.add(StatCollector.translateToLocal("ae2wtx.status.label")
                + statusColor(status) + StatCollector.translateToLocal(status.langKey()));
        }
        return currenttip;
    }

    private static String statusColor(TransceiverStatus status) {
        switch (status) {
            case OK:
                return "§a";
            case NO_BAND:
                return "§7";
            case LINKING:
            case BOOTING:
                return "§6";
            default:
                return "§c";
        }
    }

    @Override
    public List<String> getWailaTail(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return currenttip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x, int y,
        int z) {
        return tag;
    }
}
