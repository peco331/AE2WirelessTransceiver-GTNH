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
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Channel card: stores a frequency (channel) and an owner UUID.
 * <ul>
 * <li>right click air: channel +1 (sneak: -1, floor 0)</li>
 * <li>sneak + left click air/block: bind/unbind the current player's UUID</li>
 * <li>sneak + left click a transceiver: write the card's owner into it</li>
 * <li>implements AE2 IUpgradeModule so it fits into AE2 upgrade slots
 * (ME Interface, import/export/storage bus), mirroring ExtendedAE_Plus</li>
 * </ul>
 */
public class ChannelCardItem extends Item implements IUpgradeModule {

    public static final String TAG_CHANNEL = "channel";
    public static final String TAG_OWNER_UUID = "ownerUUID";
    public static final String TAG_OWNER_NAME = "ownerName";

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

    public static void setOwnerName(ItemStack stack, String name) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setString(TAG_OWNER_NAME, name == null ? "" : name);
    }

    public static String getOwnerName(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TAG_OWNER_NAME) ? tag.getString(TAG_OWNER_NAME) : null;
    }

    public static void clearOwner(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            tag.removeTag(TAG_OWNER_UUID);
            tag.removeTag(TAG_OWNER_NAME);
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

    /** Bind the current player's UUID to the card, or clear an existing binding. */
    public static void bindOrUnbind(ItemStack stack, EntityPlayer player) {
        UUID current = getOwnerUUID(stack);
        if (current != null) {
            clearOwner(stack);
            player.addChatMessage(new ChatComponentTranslation("item.extendedae_plus.channel_card.owner.cleared"));
        } else {
            setOwnerUUID(stack, player.getUniqueID());
            setOwnerName(stack, player.getCommandSenderName());
            player.addChatMessage(new ChatComponentTranslation(
                "item.extendedae_plus.channel_card.owner.bound", player.getCommandSenderName()));
        }
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
        bindOrUnbind(stack, player);
        return true;
    }

    /**
     * Sneak + right click (block): bind/unbind the card owner; sneak + right
     * click on a transceiver writes the card's owner into it. The client must
     * NOT return true here, otherwise the right-click packet is swallowed and
     * the server never sees the interaction.
     */
    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) {
            return false;
        }
        if (world.isRemote) {
            return false; // let the packet through to the server
        }
        if (world.getBlock(x, y, z) instanceof cn.gtnh.ae2wtx.content.wireless.WirelessTransceiverBlock) {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof cn.gtnh.ae2wtx.content.wireless.WirelessTransceiverBlockEntity) {
                cn.gtnh.ae2wtx.content.wireless.WirelessTransceiverBlockEntity wte =
                    (cn.gtnh.ae2wtx.content.wireless.WirelessTransceiverBlockEntity) te;
                UUID cardOwner = getOwnerUUID(stack);
                String cardName = getOwnerName(stack);
                if (cardOwner != null) {
                    // write the card's owner (with its recorded name) into the transceiver
                    wte.setPlacerId(cardOwner, cardName != null ? cardName : player.getCommandSenderName());
                    player.addChatMessage(new ChatComponentTranslation(
                        "extendedae_plus.chat.wireless_transceiver.bound_to",
                        cardName != null ? cardName : cardOwner.toString().substring(0, 8)));
                } else {
                    wte.setPlacerId(player.getUniqueID(), player.getCommandSenderName());
                    player.addChatMessage(new ChatComponentTranslation(
                        "extendedae_plus.chat.wireless_transceiver.card_unbound"));
                }
                // and copy the transceiver's channel onto the card so inserting the
                // card into an AE device carries the channel along
                long teChannel = wte.getFrequency();
                if (teChannel > 0 && getChannel(stack) != teChannel) {
                    setChannel(stack, teChannel);
                    player.addChatMessage(new ChatComponentTranslation(
                        "item.extendedae_plus.channel_card.set", teChannel));
                }
            }
            return true;
        }
        bindOrUnbind(stack, player);
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
            String name = getOwnerName(stack);
            if (name != null && !name.isEmpty()) {
                list.add(StatCollector.translateToLocalFormatted("item.extendedae_plus.channel_card.owner.team", name));
            } else {
                list.add(StatCollector.translateToLocalFormatted("item.extendedae_plus.channel_card.owner.player",
                    owner.toString().substring(0, 8)));
            }
        } else {
            list.add(StatCollector.translateToLocal("item.extendedae_plus.channel_card.owner.unset"));
        }
        // compatible devices (device reads the card and wirelessly joins the master)
        list.add(StatCollector.translateToLocal("item.extendedae_plus.channel_card.compatible"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        // 1.7.10 prepends "textures/items/" automatically (no "items/" segment)
        icon = reg.registerIcon("ae2wtx:channel_card");
    }

    /**
     * AE2 upgrade-module hook: lets the card sit in AE2 upgrade slots
     * (registered for ME Interface + import/export/storage bus, max 1 each).
     * Uses PATTERN_CAPACITY, which no rv3 machine actually reads, so the card
     * has no fake upgrade side effects.
     */
    @Override
    public Upgrades getType(ItemStack itemStack) {
        return Upgrades.PATTERN_CAPACITY;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return icon;
    }
}
