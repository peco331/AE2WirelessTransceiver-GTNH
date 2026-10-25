package cn.gtnh.ae2wtx.client.locate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import cn.gtnh.ae2wtx.wireless.LabelNetworkRegistry;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side locate view, modelled on AE2's pattern-provider highlighter:
 * the located transceivers are outlined through walls for a while, and the
 * chat lists them with click-to-fill teleport commands. Targets in another
 * dimension are kept and become visible once the player enters it.
 */
@SideOnly(Side.CLIENT)
public final class TransceiverLocator {

    public static final TransceiverLocator INSTANCE = new TransceiverLocator();

    private static final long HIGHLIGHT_MILLIS = 60_000L;
    private static final int CHAT_LINES = 8;

    /** dimension id -> outlined positions in it */
    private final Map<Integer, List<LabelNetworkRegistry.EndpointInfo>> targets = new HashMap<>();
    private long expiresAt;

    private TransceiverLocator() {}

    /**
     * Replace the current highlight with {@code located} and list them in the
     * chat. {@code total} is the band's transceiver count, which exceeds the
     * list size only when the server capped the list.
     */
    public void locate(String label, List<LabelNetworkRegistry.EndpointInfo> located, int total,
        Map<Integer, String> dimensionNames) {
        targets.clear();
        expiresAt = 0;
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        if (located.isEmpty()) {
            IChatComponent none = new ChatComponentTranslation("ae2wtx.locate.none", label);
            none.getChatStyle().setColor(EnumChatFormatting.GRAY);
            player.addChatMessage(none);
            return;
        }
        for (LabelNetworkRegistry.EndpointInfo e : located) {
            targets.computeIfAbsent(e.dim, d -> new ArrayList<>()).add(e);
        }
        expiresAt = System.currentTimeMillis() + HIGHLIGHT_MILLIS;
        printChat(player, label, located, total, dimensionNames);
    }

    private void printChat(EntityPlayer player, String label, List<LabelNetworkRegistry.EndpointInfo> located,
        int total, Map<Integer, String> dimensionNames) {
        int here = player.dimension;
        List<LabelNetworkRegistry.EndpointInfo> sorted = new ArrayList<>(located);
        // this dimension first, nearest first; other dimensions after, in id order
        Collections.sort(sorted, Comparator.<LabelNetworkRegistry.EndpointInfo>comparingInt(e -> e.dim == here ? 0 : 1)
            .thenComparingInt(e -> e.dim == here ? 0 : e.dim)
            .thenComparingDouble(e -> e.dim == here ? distanceSq(player, e) : 0));
        IChatComponent header = new ChatComponentTranslation("ae2wtx.locate.header", label, located.size(),
            HIGHLIGHT_MILLIS / 1000);
        header.getChatStyle().setColor(EnumChatFormatting.LIGHT_PURPLE);
        player.addChatMessage(header);
        boolean crossDimTeleport = Loader.isModLoaded("serverutilities");
        for (int i = 0; i < Math.min(CHAT_LINES, sorted.size()); i++) {
            player.addChatMessage(entryLine(player, sorted.get(i), dimensionNames, crossDimTeleport));
        }
        if (sorted.size() > CHAT_LINES) {
            IChatComponent more = new ChatComponentTranslation("ae2wtx.locate.more", sorted.size() - CHAT_LINES);
            more.getChatStyle().setColor(EnumChatFormatting.GRAY);
            player.addChatMessage(more);
        }
        if (total > located.size()) {
            IChatComponent capped = new ChatComponentTranslation("ae2wtx.locate.capped", total, located.size());
            capped.getChatStyle().setColor(EnumChatFormatting.GRAY);
            player.addChatMessage(capped);
        }
    }

    private static IChatComponent entryLine(EntityPlayer player, LabelNetworkRegistry.EndpointInfo e,
        Map<Integer, String> dimensionNames, boolean crossDimTeleport) {
        String dimName = dimensionNames.containsKey(e.dim) ? dimensionNames.get(e.dim) : "DIM " + e.dim;
        boolean sameDim = e.dim == player.dimension;
        String where = sameDim
            ? (int) Math.sqrt(distanceSq(player, e)) + "m"
            : dimName;
        IChatComponent line = new ChatComponentText(" ");
        IChatComponent dot = new ChatComponentText("● ");
        dot.getChatStyle().setColor(e.online ? EnumChatFormatting.GREEN : EnumChatFormatting.DARK_GRAY);
        IChatComponent coords = new ChatComponentText(e.x + ", " + e.y + ", " + e.z);
        IChatComponent suffix = new ChatComponentText("  (" + where + ")");
        suffix.getChatStyle().setColor(EnumChatFormatting.GRAY);
        line.appendSibling(dot);
        line.appendSibling(coords);
        line.appendSibling(suffix);

        IChatComponent hover = new ChatComponentText(dimName + "  " + e.x + ", " + e.y + ", " + e.z + "\n");
        hover.appendSibling(new ChatComponentTranslation(
            e.online ? "ae2wtx.locate.online" : "ae2wtx.locate.offline"));
        String command = null;
        if (crossDimTeleport) {
            command = "/tpl " + e.x + " " + (e.y + 1) + " " + e.z + " " + e.dim;
        } else if (sameDim) {
            command = "/tp " + e.x + " " + (e.y + 1) + " " + e.z;
        }
        hover.appendText("\n");
        hover.appendSibling(new ChatComponentTranslation(
            command != null ? "ae2wtx.locate.click" : "ae2wtx.locate.no_teleport"));
        if (!sameDim) {
            hover.appendText("\n");
            hover.appendSibling(new ChatComponentTranslation("ae2wtx.locate.other_dimension"));
        }
        ChatStyle style = coords.getChatStyle();
        style.setColor(EnumChatFormatting.AQUA);
        style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        if (command != null) {
            style.setUnderlined(true);
            style.setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
        }
        return line;
    }

    private static double distanceSq(EntityPlayer player, LabelNetworkRegistry.EndpointInfo e) {
        return player.getDistanceSq(e.x + 0.5D, e.y + 0.5D, e.z + 0.5D);
    }

    public void clear() {
        targets.clear();
        expiresAt = 0;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (targets.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getMinecraft();
        if (now >= expiresAt || mc.theWorld == null || mc.thePlayer == null) {
            clear();
            return;
        }
        List<LabelNetworkRegistry.EndpointInfo> here = targets.get(mc.thePlayer.dimension);
        if (here == null) {
            return;
        }
        EntityPlayer p = mc.thePlayer;
        double camX = p.lastTickPosX + (p.posX - p.lastTickPosX) * event.partialTicks;
        double camY = p.lastTickPosY + (p.posY - p.lastTickPosY) * event.partialTicks;
        double camZ = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * event.partialTicks;
        // fluix purple, pulsing once per second
        float pulse = 0.55F + 0.45F * (float) Math.sin(now % 1000L / 1000.0D * Math.PI * 2.0D);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        GL11.glTranslated(-camX, -camY, -camZ);
        // untextured lines: the lightmap unit would otherwise darken them
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        boolean lightmap = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(3.0F);
        for (LabelNetworkRegistry.EndpointInfo e : here) {
            float r = e.online ? 0.73F : 0.55F;
            float g = e.online ? 0.47F : 0.55F;
            float b = e.online ? 1.0F : 0.6F;
            GL11.glColor4f(r, g, b, pulse);
            outline(e.x - 0.02D, e.y - 0.02D, e.z - 0.02D, e.x + 1.02D, e.y + 1.02D, e.z + 1.02D);
        }
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        if (lightmap) {
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void outline(double x0, double y0, double z0, double x1, double y1, double z1) {
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINES);
        // bottom and top rings
        for (double y : new double[] { y0, y1 }) {
            t.addVertex(x0, y, z0);
            t.addVertex(x1, y, z0);
            t.addVertex(x1, y, z0);
            t.addVertex(x1, y, z1);
            t.addVertex(x1, y, z1);
            t.addVertex(x0, y, z1);
            t.addVertex(x0, y, z1);
            t.addVertex(x0, y, z0);
        }
        // vertical edges
        t.addVertex(x0, y0, z0);
        t.addVertex(x0, y1, z0);
        t.addVertex(x1, y0, z0);
        t.addVertex(x1, y1, z0);
        t.addVertex(x1, y0, z1);
        t.addVertex(x1, y1, z1);
        t.addVertex(x0, y0, z1);
        t.addVertex(x0, y1, z1);
        t.draw();
    }
}
