package cn.gtnh.ae2wtx.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import cn.gtnh.ae2wtx.config.ModConfig;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

/**
 * The transceiver shell (design 7, see tools/art/generate_assets.py): a
 * 2-wide frame on all twelve edges; on every face a shallow 45-degree bevel
 * from the frame down to a glass pane 1 unit in, and a wiring panel in the
 * middle reaching from the surface to the glass. Pass 0 draws the solid parts,
 * pass 1 the glass and, while online, the full-bright arc overlay on it. A
 * wiring panel made see-through in the config moves to pass 1 with a faded
 * texture (or is left out at 0 %). TransceiverEffectsRenderer draws the black
 * hole inside; the item, which has no tile entity, gets a dark inner box.
 */
public final class TransceiverBlockRenderer implements ISimpleBlockRenderingHandler {

    static final double FRAME = 2;
    static final double BEVEL_DEPTH = 1;
    static final double GLASS_LO = 3;
    static final double GLASS_HI = 13;
    static final double PANEL_LO = 5;
    static final double PANEL_HI = 11;
    /** Half size of the black cube left in the middle when the effects are not drawn. */
    private static final double CORE = 1.2;
    /** The arc overlay sits just above the glass. */
    private static final double ARC_DEPTH = BEVEL_DEPTH - 0.02;

    private static final ForgeDirection[] FACES = ForgeDirection.VALID_DIRECTIONS;
    private static final double[] PANEL_FRONT_UV = { 0, 0, 6, 6 };
    private static final double[] PANEL_BACK_UV = { 6, 0, 12, 6 };
    private static final double[] PANEL_SIDE_UV = { 0, 15, 6, 16 };

    private final int renderId;

    public TransceiverBlockRenderer(int renderId) {
        this.renderId = renderId;
    }

    @Override
    public int getRenderId() {
        return renderId;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public void renderInventoryBlock(Block block, int meta, int modelId, RenderBlocks renderer) {
        Tessellator t = Tessellator.instance;
        QuadWriter q = new QuadWriter(t, -0.5D, -0.5D, -0.5D, true);
        ClassicModel classic = ClassicModel.get(false);
        TransceiverIcons icons = TransceiverIcons.forTheme(ModConfig.clientDefaultThemeLight);
        if (classic != null) {
            t.startDrawingQuads();
            renderClassic(q, classic, 0);
            drawBlended(t, classic.translucent);
            return;
        }
        if (ModConfig.clientSimpleBlockModel) {
            t.startDrawingQuads();
            for (ForgeDirection face : FACES) {
                q.boxFace(face, 0, 0, 0, 16, 16, 16, null, 0, icons.fallbackOn, 0, 1, 1, 1, 1);
            }
            t.draw();
            return;
        }
        int[] outer = new int[6];
        boolean[] culled = new boolean[6];
        t.startDrawingQuads();
        renderSolid(q, icons, true, outer, culled, 0);
        interior(q, icons);
        t.draw();
        t.startDrawingQuads();
        renderGlass(q, icons, true, outer, culled, 0);
        drawBlended(t, true);
    }

    private static void drawBlended(Tessellator t, boolean blend) {
        if (!blend) {
            t.draw();
            return;
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        t.draw();
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        int pass = ForgeHooksClient.getWorldRenderPass();
        boolean online = world.getBlockMetadata(x, y, z) == 1;
        ClassicModel classic = ClassicModel.get(online);
        if (classic != null) {
            if ((pass == 1) != classic.translucent) {
                return false;
            }
            renderClassic(new QuadWriter(Tessellator.instance, x, y, z, false), classic,
                block.getMixedBrightnessForBlock(world, x, y, z));
            return true;
        }
        if (ModConfig.clientSimpleBlockModel) {
            return pass == 0 && renderer.renderStandardBlock(block, x, y, z);
        }
        TransceiverIcons icons = TransceiverIcons.forBlock(world, x, y, z);
        QuadWriter q = new QuadWriter(Tessellator.instance, x, y, z, false);
        int own = block.getMixedBrightnessForBlock(world, x, y, z);
        int[] outer = new int[6];
        boolean[] culled = new boolean[6];
        for (ForgeDirection d : FACES) {
            int i = d.ordinal();
            int nx = x + d.offsetX, ny = y + d.offsetY, nz = z + d.offsetZ;
            outer[i] = block.getMixedBrightnessForBlock(world, nx, ny, nz);
            culled[i] = !block.shouldSideBeRendered(world, nx, ny, nz, i);
        }
        if (pass == 0) {
            renderSolid(q, icons, online, outer, culled, own);
        } else {
            renderGlass(q, icons, online, outer, culled, own);
        }
        return true;
    }

    /**
     * Frame beams, bevels, opaque wiring panels and the black stand-in core.
     * {@code outer} is the light on each side of the block and {@code culled}
     * the sides a neighbour hides; the inventory passes all zero / none.
     */
    private static void renderSolid(QuadWriter q, TransceiverIcons icons, boolean online, int[] outer,
        boolean[] culled, int own) {
        double hi = 16 - FRAME;
        for (double a : new double[] { 0, hi }) {
            for (double b : new double[] { 0, hi }) {
                box(q, 0, a, b, 16, a + FRAME, b + FRAME, icons.beam, outer, culled, own);
                box(q, a, FRAME, b, a + FRAME, hi, b + FRAME, icons.beam, outer, culled, own);
                box(q, a, b, FRAME, a + FRAME, b + FRAME, hi, icons.beam, outer, culled, own);
            }
        }
        IIcon bevel = online ? icons.bevelOn : icons.bevelOff;
        IIcon port = online ? icons.portOn : icons.portOff;
        for (ForgeDirection face : FACES) {
            bevels(q, face, bevel, own);
        }
        if (panelLevel() >= 10) {
            panels(q, port, outer, culled, own, false);
        }
        double lo = 8 - CORE, top = 8 + CORE;
        for (ForgeDirection side : FACES) {
            q.boxFace(side, lo, lo, lo, top, top, top, PANEL_SIDE_UV, 0, port, own, 0, 0, 0, 1);
        }
    }

    /**
     * Panel opacity in tenths: 0 hidden, 10 opaque, 1..9 a faded texture. The
     * alpha has to come from the texture: Angelica's terrain renderer takes
     * vertex alpha as a shade factor, so a faded vertex colour only darkens.
     */
    static int panelLevel() {
        return Math.max(0, Math.min(10, Math.round(ModConfig.clientPanelOpacity / 10F)));
    }

    /**
     * Item only: a dark box just behind the glass. The black hole is a tile
     * entity effect, so without it the item is hollow and shows the world
     * through its glass.
     */
    private static void interior(QuadWriter q, TransceiverIcons icons) {
        double lo = BEVEL_DEPTH + 0.1, hi = 16 - lo;
        for (ForgeDirection side : FACES) {
            q.boxFace(side, lo, lo, lo, hi, hi, hi, PANEL_SIDE_UV, 0, icons.portOff, 0, 0.05F, 0.04F, 0.08F, 1);
        }
    }

    /**
     * The wiring panel on every face, from the surface down to the glass. A
     * see-through one leaves out its back, which lies on the glass and would
     * fight with it.
     */
    private static void panels(QuadWriter q, IIcon port, int[] outer, boolean[] culled, int own,
        boolean seeThrough) {
        for (ForgeDirection face : FACES) {
            double[] a = at(face, PANEL_LO, PANEL_LO, 0);
            double[] b = at(face, PANEL_HI, PANEL_HI, BEVEL_DEPTH);
            double x0 = Math.min(a[0], b[0]), y0 = Math.min(a[1], b[1]), z0 = Math.min(a[2], b[2]);
            double x1 = Math.max(a[0], b[0]), y1 = Math.max(a[1], b[1]), z1 = Math.max(a[2], b[2]);
            for (ForgeDirection side : FACES) {
                if (side == face) {
                    if (!culled[face.ordinal()]) {
                        q.boxFace(side, x0, y0, z0, x1, y1, z1, PANEL_FRONT_UV, 0, port, outer[face.ordinal()],
                            1, 1, 1, 1);
                    }
                } else if (side == face.getOpposite()) {
                    if (!seeThrough) {
                        q.boxFace(side, x0, y0, z0, x1, y1, z1, PANEL_BACK_UV, 0, port, own, 1, 1, 1, 1);
                    }
                } else {
                    q.boxFace(side, x0, y0, z0, x1, y1, z1, PANEL_SIDE_UV, 0, port, own, 1, 1, 1, 1);
                }
            }
        }
    }

    /** A frame beam; faces lying on the block boundary take the neighbour's light and may be culled. */
    private static void box(QuadWriter q, double x0, double y0, double z0, double x1, double y1, double z1,
        IIcon icon, int[] outer, boolean[] culled, int own) {
        for (ForgeDirection f : FACES) {
            double edge = f.offsetX != 0 ? (f.offsetX > 0 ? x1 : x0)
                : f.offsetY != 0 ? (f.offsetY > 0 ? y1 : y0) : (f.offsetZ > 0 ? z1 : z0);
            boolean boundary = edge == (f.offsetX + f.offsetY + f.offsetZ > 0 ? 16 : 0);
            if (boundary && culled[f.ordinal()]) {
                continue;
            }
            q.boxFace(f, x0, y0, z0, x1, y1, z1, null, 0, icon, boundary ? outer[f.ordinal()] : own, 1, 1, 1, 1);
        }
    }

    /** Four mitred trapezoids from the frame's inner edge at the surface to the glass edge. */
    private static void bevels(QuadWriter q, ForgeDirection face, IIcon icon, int brightness) {
        double o0 = FRAME, o1 = 16 - FRAME, i0 = GLASS_LO, i1 = GLASS_HI;
        double[][][] sides = {
            { { o0, o0 }, { o1, o0 }, { i1, i0 }, { i0, i0 } },
            { { o1, o0 }, { o1, o1 }, { i1, i1 }, { i1, i0 } },
            { { o1, o1 }, { o0, o1 }, { i0, i1 }, { i1, i1 } },
            { { o0, o1 }, { o0, o0 }, { i0, i0 }, { i0, i1 } } };
        double[][] uv = { { 0, 0 }, { 16, 0 }, { 16, 16 }, { 0, 16 } };
        for (double[][] side : sides) {
            double[][] p = new double[4][];
            for (int k = 0; k < 4; k++) {
                p[k] = at(face, side[k][0], side[k][1], k < 2 ? 0 : BEVEL_DEPTH);
                q.vertex(k, p[k][0], p[k][1], p[k][2], uv[k][0], uv[k][1]);
            }
            double ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
            double bx = p[3][0] - p[0][0], by = p[3][1] - p[0][1], bz = p[3][2] - p[0][2];
            double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            if (nx * face.offsetX + ny * face.offsetY + nz * face.offsetZ < 0) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            q.emit(icon, nx, ny, nz, brightness, 1, 1, 1, 1);
        }
    }

    /**
     * Single-sided panes, so from outside only the near ones show; the arcs
     * only while online; and the wiring panels when they are see-through.
     */
    private static void renderGlass(QuadWriter q, TransceiverIcons icons, boolean online, int[] outer,
        boolean[] culled, int brightness) {
        for (ForgeDirection face : FACES) {
            pane(q, face, BEVEL_DEPTH, icons.glass, 0, brightness);
            if (online && ModConfig.clientGlassArcs) {
                // turn the shared arc animation per face so neighbouring faces do not flicker in step
                pane(q, face, ARC_DEPTH, icons.arcs, face.ordinal(), QuadWriter.FULL_BRIGHT);
            }
        }
        int level = panelLevel();
        if (level > 0 && level < 10) {
            panels(q, icons.port(online, level), outer, culled, brightness, true);
        }
    }

    private static void pane(QuadWriter q, ForgeDirection face, double depth, IIcon icon, int turn,
        int brightness) {
        double[][] corners = { { GLASS_LO, GLASS_LO }, { GLASS_HI, GLASS_LO }, { GLASS_HI, GLASS_HI },
            { GLASS_LO, GLASS_HI } };
        for (int k = 0; k < 4; k++) {
            double[] p = at(face, corners[k][0], corners[k][1], depth);
            double u = QuadWriter.projectU(face, p[0], p[1], p[2]);
            double v = QuadWriter.projectV(face, p[0], p[1], p[2]);
            for (int r = 0; r < turn % 4; r++) {
                double tmp = u;
                u = 16 - v;
                v = tmp;
            }
            if (turn >= 4) {
                u = 16 - u;
            }
            q.vertex(k, p[0], p[1], p[2], u, v);
        }
        q.emit(icon, face.offsetX, face.offsetY, face.offsetZ, brightness, 1, 1, 1, 1);
    }

    private static void renderClassic(QuadWriter q, ClassicModel model, int brightness) {
        for (ClassicModel.Element e : model.elements) {
            int light = e.emissive ? QuadWriter.FULL_BRIGHT : brightness;
            for (ClassicModel.Face f : e.faces) {
                q.boxFace(f.dir, e.from[0], e.from[1], e.from[2], e.to[0], e.to[1], e.to[2], f.uv, f.rotation,
                    f.icon, light, 1, 1, 1, 1);
            }
        }
    }

    /**
     * A point on a face: (u, v) in the face plane and d in from the outer
     * surface, all in model units, to block coordinates.
     */
    static double[] at(ForgeDirection face, double u, double v, double d) {
        switch (face) {
            case NORTH:
                return new double[] { u, v, d };
            case SOUTH:
                return new double[] { u, v, 16 - d };
            case WEST:
                return new double[] { d, v, u };
            case EAST:
                return new double[] { 16 - d, v, u };
            case DOWN:
                return new double[] { u, d, v };
            default:
                return new double[] { u, 16 - d, v };
        }
    }
}
