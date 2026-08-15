package cn.gtnh.ae2wtx.client.render;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 1.7.10 renderer for the Light Mode 3D transceiver model (ported from the
 * 1.20.1 blockbench model: 31 elements, rotated edge trims, top protrusions).
 * Renders the baked elements with the light texture; online state uses the
 * animated (breathing) texture which the atlas plays automatically.
 */
@SideOnly(Side.CLIENT)
public class TransceiverRenderer implements ISimpleBlockRenderingHandler {

    private static final LightModel MODEL_OFF = LightModel.load("lable_off.json");
    private static final LightModel MODEL_ON = LightModel.load("lable_on.json");

    private static final String[] DIRS = { "down", "up", "north", "south", "west", "east" };

    /** Per-face brightness like vanilla RenderBlocks (gives the 3D depth). */
    private static final float[] SHADE = { 0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F };

    // 1.20.1 direction tables: 4 corners (x,y,z in 0/1) with matching uv corner
    // (u along the face's horizontal axis, v along its vertical axis).
    // corner order is irrelevant (1.7.10 disables backface culling); only the
    // uv mapping direction matters for texture orientation.
    private static final int[][] CORNERS = {
        // down:  u:x  v:z (z0->v1, z1->v2)
        { 1, 0, 0, 0, 1, 0, 1, 1 }, { 1, 0, 1, 0, 1, 1, 1, 0 }, { 0, 0, 1, 0, 1, 1, 0, 0 }, { 0, 0, 0, 0, 1, 0, 0, 0 },
        // up:  u:x  v:z (z1->v1, z0->v2)
        { 0, 1, 1, 0, 0, 0, 0, 0 }, { 0, 1, 0, 0, 0, 0, 0, 1 }, { 1, 1, 0, 1, 0, 0, 1, 1 }, { 1, 1, 1, 1, 0, 0, 1, 0 },
        // north:  u:x  v:y (y1->v1, y0->v2)
        { 0, 1, 0, 0, 0, 0, 0, 0 }, { 1, 1, 0, 1, 0, 0, 1, 0 }, { 1, 0, 0, 1, 0, 1, 1, 1 }, { 0, 0, 0, 0, 0, 1, 0, 1 },
        // south:  u:x(reversed)  v:y
        { 1, 1, 1, 0, 0, 0, 1, 0 }, { 0, 1, 1, 1, 0, 0, 1, 1 }, { 0, 0, 1, 1, 0, 1, 0, 1 }, { 1, 0, 1, 0, 0, 1, 0, 0 },
        // west:  u:z(reversed)  v:y
        { 0, 1, 1, 0, 1, 0, 0, 0 }, { 0, 1, 0, 1, 1, 0, 0, 1 }, { 0, 0, 0, 1, 1, 1, 0, 1 }, { 0, 0, 1, 0, 1, 1, 0, 0 },
        // east:  u:z  v:y
        { 1, 1, 0, 0, 0, 0, 1, 0 }, { 1, 1, 1, 1, 0, 0, 1, 1 }, { 1, 0, 1, 1, 0, 1, 0, 1 }, { 1, 0, 0, 0, 0, 1, 0, 0 },
    };
    // uv corner offsets for each direction x 4 corners (cu, cv) per corner,
    // where u = u1 + cu*(u2-u1), v = v1 + cv*(v2-v1)
    private static final float[][] UVMAP = {
        // down:  u along x, v along z
        { 0, 0 }, { 0, 1 }, { 1, 1 }, { 1, 0 },
        // up:  u along x, v along z (reversed)
        { 0, 0 }, { 0, 1 }, { 1, 1 }, { 1, 0 },
        // north:  u along x, v along y (v1 top)
        { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 },
        // south:  u along -x, v along y
        { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 },
        // west:  u along -z, v along y
        { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 },
        // east:  u along z, v along y
        { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 },
    };

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        int meta = world.getBlockMetadata(x, y, z);
        IIcon icon = meta == 1
            ? cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlock.iconOn
            : cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlock.iconOff;
        if (icon == null) {
            return false;
        }
        // vanilla 16px textures = the original EAEP look: render as a plain
        // cube. The 3D model only kicks in with the Light Mode pack (128px).
        if (icon.getIconWidth() <= 16) {
            return renderer.renderStandardBlock(block, x, y, z);
        }
        LightModel model = (meta == 1 ? MODEL_ON : MODEL_OFF);
        if (model == null) {
            return false;
        }
        // Angelica and other render optimizers may enable backface culling;
        // our vertex winding is not guaranteed CCW, so disable culling for
        // the model quads and restore the previous state afterwards.
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        if (cull) {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
        Tessellator tess = Tessellator.instance;
        tess.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z));
        int color = block.colorMultiplier(world, x, y, z);
        float cr = (color >> 16 & 255) / 255.0F;
        float cg = (color >> 8 & 255) / 255.0F;
        float cb = (color & 255) / 255.0F;
        tess.addTranslation(x, y, z);
        for (LightModel.Element el : model.elements) {
            renderElement(tess, el, icon, cr, cg, cb);
        }
        tess.addTranslation(-x, -y, -z);
        if (cull) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        }
        return true;
    }

    private static void renderElement(Tessellator tess, LightModel.Element el, IIcon icon,
        float cr, float cg, float cb) {
        float minU = icon.getMinU();
        float maxU = icon.getMaxU();
        float minV = icon.getMinV();
        float maxV = icon.getMaxV();
        float uSpan = maxU - minU;
        float vSpan = maxV - minV;

        for (int d = 0; d < 6; d++) {
            LightModel.Face face = el.faces[d];
            if (face == null) {
                continue;
            }
            // per-face directional shading (vanilla look, gives depth)
            float shade = SHADE[d];
            tess.setColorOpaque_F(cr * shade, cg * shade, cb * shade);
            float u1 = minU + (face.uv[0] / 16.0F) * uSpan;
            float v1 = minV + (face.uv[1] / 16.0F) * vSpan;
            float u2 = minU + (face.uv[2] / 16.0F) * uSpan;
            float v2 = minV + (face.uv[3] / 16.0F) * vSpan;
            for (int c = 0; c < 4; c++) {
                int[] corner = CORNERS[d * 4 + c];
                float[] uv = UVMAP[d * 4 + c];
                float px = el.from[0] + (el.to[0] - el.from[0]) * corner[0];
                float py = el.from[1] + (el.to[1] - el.from[1]) * corner[1];
                float pz = el.from[2] + (el.to[2] - el.from[2]) * corner[2];
                if (el.rotation != null) {
                    rotate(px, py, pz, el.rotation, out);
                    px = out[0];
                    py = out[1];
                    pz = out[2];
                }
                float u = u1 + uv[0] * (u2 - u1);
                float v = v1 + uv[1] * (v2 - v1);
                tess.addVertexWithUV(px / 16.0F, py / 16.0F, pz / 16.0F, u, v);
            }
        }
    }

    private static final float[] out = new float[3];

    /** Rotate a point around the rotation origin by the axis/angle (degrees). */
    private static void rotate(float x, float y, float z, LightModel.Rotation r, float[] out) {
        float ox = x - r.origin[0];
        float oy = y - r.origin[1];
        float oz = z - r.origin[2];
        float rad = (float) Math.toRadians(r.angle);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float rx;
        float ry;
        float rz;
        switch (r.axis) {
            case "x":
                ry = oy * cos - oz * sin;
                rz = oy * sin + oz * cos;
                rx = ox;
                break;
            case "y":
                rx = ox * cos + oz * sin;
                rz = -ox * sin + oz * cos;
                ry = oy;
                break;
            case "z":
                rx = ox * cos - oy * sin;
                ry = ox * sin + oy * cos;
                rz = oz;
                break;
            default:
                out[0] = x;
                out[1] = y;
                out[2] = z;
                return;
        }
        out[0] = rx + r.origin[0];
        out[1] = ry + r.origin[1];
        out[2] = rz + r.origin[2];
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        IIcon icon = metadata == 1 ? cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlock.iconOn
            : cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlock.iconOff;
        if (icon == null) {
            return;
        }
        // vanilla 16px texture: plain cube icon
        if (icon.getIconWidth() <= 16) {
            renderCubeInventory(icon);
            return;
        }
        // rotate a bit for a nice 3D inventory icon
        GL11.glPushMatrix();
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        GL11.glRotatef(-30.0F, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(22.0F, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(0.625F, 0.625F, 0.625F);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        if (cull) {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
        // NOTE: RenderBlocks.renderBlockAsItem() already called
        // Tessellator.startDrawingQuads() before invoking us and will call
        // draw() afterwards - we must NOT start/draw here.
        Tessellator tess = Tessellator.instance;
        // inventory standard full brightness
        tess.setBrightness(0x00F000F0);
        LightModel model = (metadata == 1 ? MODEL_ON : MODEL_OFF);
        if (model != null) {
            for (LightModel.Element el : model.elements) {
                renderElement(tess, el, icon, 1.0F, 1.0F, 1.0F);
            }
        }
        if (cull) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        }
        GL11.glPopMatrix();
    }

    /** Simple 6-face cube for the vanilla (16px) inventory icon. */
    private static void renderCubeInventory(IIcon icon) {
        // NOTE: outer renderBlockAsItem owns startDrawingQuads/draw
        Tessellator tess = Tessellator.instance;
        tess.setBrightness(0x00F000F0); // inventory standard full brightness
        float minU = icon.getMinU();
        float maxU = icon.getMaxU();
        float minV = icon.getMinV();
        float maxV = icon.getMaxV();
        // -y (0.5)
        tess.setColorOpaque_F(0.5F, 0.5F, 0.5F);
        tess.addVertexWithUV(0, 0, 0, minU, minV);
        tess.addVertexWithUV(1, 0, 0, maxU, minV);
        tess.addVertexWithUV(1, 0, 1, maxU, maxV);
        tess.addVertexWithUV(0, 0, 1, minU, maxV);
        // +y (1.0)
        tess.setColorOpaque_F(1.0F, 1.0F, 1.0F);
        tess.addVertexWithUV(0, 1, 0, minU, minV);
        tess.addVertexWithUV(0, 1, 1, maxU, minV);
        tess.addVertexWithUV(1, 1, 1, maxU, maxV);
        tess.addVertexWithUV(1, 1, 0, minU, maxV);
        // -z (0.8)
        tess.setColorOpaque_F(0.8F, 0.8F, 0.8F);
        tess.addVertexWithUV(0, 0, 0, minU, maxV);
        tess.addVertexWithUV(1, 0, 0, maxU, maxV);
        tess.addVertexWithUV(1, 1, 0, maxU, minV);
        tess.addVertexWithUV(0, 1, 0, minU, minV);
        // +z (0.8)
        tess.setColorOpaque_F(0.8F, 0.8F, 0.8F);
        tess.addVertexWithUV(0, 0, 1, minU, minV);
        tess.addVertexWithUV(1, 0, 1, maxU, minV);
        tess.addVertexWithUV(1, 1, 1, maxU, maxV);
        tess.addVertexWithUV(0, 1, 1, minU, maxV);
        // -x (0.6)
        tess.setColorOpaque_F(0.6F, 0.6F, 0.6F);
        tess.addVertexWithUV(0, 0, 0, minU, minV);
        tess.addVertexWithUV(0, 0, 1, maxU, minV);
        tess.addVertexWithUV(0, 1, 1, maxU, maxV);
        tess.addVertexWithUV(0, 1, 0, minU, maxV);
        // +x (0.6)
        tess.setColorOpaque_F(0.6F, 0.6F, 0.6F);
        tess.addVertexWithUV(1, 0, 0, minU, minV);
        tess.addVertexWithUV(1, 1, 0, minU, maxV);
        tess.addVertexWithUV(1, 1, 1, maxU, maxV);
        tess.addVertexWithUV(1, 0, 1, maxU, minV);
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return cn.gtnh.ae2wtx.client.ClientRenderHandler.renderId;
    }
}
