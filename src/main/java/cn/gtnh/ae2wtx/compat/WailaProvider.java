package cn.gtnh.ae2wtx.compat;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;

/** Waila provider for the wireless transceiver: frequency/owner/channels/lock/online. */
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
            currenttip.add(StatCollector.translateToLocalFormatted("extendedae_plus.jade.frequency",
                label == null || label.isEmpty() ? "-" : label));
            java.util.UUID owner = lte.getPlacerId();
            if (owner == null) {
                currenttip.add(StatCollector.translateToLocal("extendedae_plus.jade.owner.public"));
            } else {
                String name = lte.getPlacerName();
                currenttip.add(StatCollector.translateToLocalFormatted("extendedae_plus.jade.owner",
                    name != null ? name : owner.toString().substring(0, 8)));
            }
            // channels used by THIS transceiver (denominator = capacity the ME
            // network actually grants: 32 dense, infinite mode shows plain count)
            int used = lte.getUsedChannelsForDisplay();
            int max = lte.getMaxChannelsForDisplay();
            if (max <= 0 || max >= 1_000_000) {
                currenttip.add(StatCollector.translateToLocalFormatted("extendedae_plus.jade.channels", used));
            } else {
                currenttip.add(StatCollector.translateToLocalFormatted(
                    "extendedae_plus.jade.channels_of",
                    ChannelDisplayFormatter.colorizeUsed(used, max),
                    max));
            }
            // channels used by ALL endpoints of this frequency (label) - wording
            // matches the GUI "本频段频道 x/y"; over capacity -> red, full -> yellow
            int net = lte.getNetworkChannelsForDisplay();
            String netVal = ChannelDisplayFormatter.formatBand(net, max);
            currenttip.add(StatCollector.translateToLocal("extendedae_plus.jade.channels_network_label") + netVal);
            // transceivers online in this band
            currenttip.add(StatCollector.translateToLocalFormatted("extendedae_plus.jade.band_online",
                lte.getOnlineCountForDisplay()));
            currenttip.add(StatCollector.translateToLocal(
                lte.isLocked() ? "extendedae_plus.chat.wireless_transceiver.locked_status"
                    : "extendedae_plus.chat.wireless_transceiver.unlocked_status"));
            currenttip.add(StatCollector.translateToLocal(
                lte.isOnline() ? "extendedae_plus.jade.online" : "extendedae_plus.jade.offline"));
        }
        return currenttip;
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
