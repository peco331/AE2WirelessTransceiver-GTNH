package cn.gtnh.ae2wtx.item;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cn.gtnh.ae2wtx.AE2Wtx;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Channel card: stores a frequency (channel) and an owner UUID.
 * <ul>
 * <li>right click air: channel +1 (sneak: -1, floor 0)</li>
 * <li>sneak + left click air/block: bind/unbind the current player's UUID</li>
 * <li>sneak + left click a transceiver: write the card's owner into it</li>
 * </ul>
 */
public class ChannelCardItem extends Item {

    public static final String TAG_CHANNEL = "channel";
    public static final String TAG_OWNER_UUID = "ownerUUID";

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    public ChannelCardItem() {
        setUnlocalizedName("ae2wtx.channel_card");
        setCreativeTab(AE2Wtx.CREATIVE_TAB);
        setMaxStackSize(64);
    }

    public static void setChannel(ItemStack stack, long channel) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setLong(TAG_CHANNEL, channel);
    }

    public static long getChannel(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TAG_CHANNEL) ? tag.getLong(TAG_CHANNEL) : 0L;
    }

    public static void setOwnerUUID(ItemStack stack, UUID ownerUUID) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setString(TAG_OWNER_UUID, ownerUUID.toString());
    }

    public static UUID getOwnerUUID(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(TAG_OWNER_UUID)) {
            try {
                return UUID.fromString(tag.getString(TAG_OWNER_UUID));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    public static void clearOwner(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            tag.removeTag(TAG_OWNER_UUID);
        }
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            long ch = getChannel(stack);
            long next = player.isSneaking() ? Math.max(0L, ch - 1L) : ch + 1L;
            if (next != ch) {
                setChannel(stack, next);
                player.addChatMessage(new ChatComponentTranslation("item.extendedae_plus.channel_card.set", next));
            }
        }
        return stack;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, int x, int y, int z, EntityPlayer player) {
        if (!player.isSneaking()) {
            return false; // don't intercept
        }
        World world = player.worldObj;
        if (world.isRemote) {
            return true; // client intercept, prevents breaking
        }
        // If the target is a transceiver, let the block class handle it.
        if (world.getBlock(x, y, z) instanceof cn.gtnh.ae2wtx.content.wireless.WirelessTransceiverBlock) {
            return false;
        }
        // bind/unbind owner
        UUID current = getOwnerUUID(stack);
        if (current != null) {
            clearOwner(stack);
            player.addChatMessage(new ChatComponentTranslation("item.extendedae_plus.channel_card.owner.cleared"));
        } else {
            setOwnerUUID(stack, player.getUniqueID());
            player.addChatMessage(new ChatComponentTranslation("item.extendedae_plus.channel_card.owner.bound", player.getCommandSenderName()));
        }
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        long ch = getChannel(stack);
        if (ch == 0L) {
            list.add(StatCollector.translateToLocal("item.extendedae_plus.channel_card.channel.unset"));
        } else {
            list.add(StatCollector.translateToLocalFormatted("item.extendedae_plus.channel_card.channel", ch));
        }
        UUID owner = getOwnerUUID(stack);
        if (owner != null) {
            list.add(StatCollector.translateToLocalFormatted("item.extendedae_plus.channel_card.owner.player", owner.toString().substring(0, 8)));
        } else {
            list.add(StatCollector.translateToLocal("item.extendedae_plus.channel_card.owner.unset"));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icon = reg.registerIcon("ae2wtx:items/channel_card");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return icon;
    }
}
