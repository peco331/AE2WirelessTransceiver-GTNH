package cn.gtnh.ae2wtx.compat;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.content.wireless.WirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;
import cn.gtnh.ae2wtx.wireless.WirelessMasterRegistry;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;

/** Waila provider showing mode/frequency/label/owner/channels for both transceivers. */
public class WailaProvider implements IWailaDataProvider {

    public static void callback(mcp.mobius.waila.api.IWailaRegistrar registrar) {
        registrar.registerBodyProvider(new WailaProvider(), WirelessTransceiverBlockEntity.class);
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
        if (te instanceof WirelessTransceiverBlockEntity) {
            WirelessTransceiverBlockEntity wte = (WirelessTransceiverBlockEntity) te;
            currenttip.add((wte.isMasterMode() ? "Master" : "Slave") + "  Freq: " + wte.getFrequency());
            currenttip.add(wte.isLocked() ? EnumChatFormatting.YELLOW + "Locked" : "Unlocked");
            UUID owner = wte.getPlacerId();
            if (owner == null) {
                currenttip.add("Public");
            } else {
                String name = wte.getPlacerName();
                currenttip.add("Owner: " + (name != null ? name : owner.toString().substring(0, 8)));
            }
        } else if (te instanceof LabeledWirelessTransceiverBlockEntity) {
            LabeledWirelessTransceiverBlockEntity lte = (LabeledWirelessTransceiverBlockEntity) te;
            String label = lte.getLabelForDisplay();
            currenttip.add(label == null ? EnumChatFormatting.GRAY + "No label" : "Label: " + label);
            currenttip.add("Channel: " + lte.getFrequency());
            UUID owner = lte.getPlacerId();
            if (owner == null) {
                currenttip.add("Public");
            } else {
                String name = lte.getPlacerName();
                currenttip.add("Owner: " + (name != null ? name : owner.toString().substring(0, 8)));
            }
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
