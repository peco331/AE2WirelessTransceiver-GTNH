package cn.gtnh.ae2wtx.client.render;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;

import cn.gtnh.ae2wtx.config.ModConfig;
import cn.gtnh.ae2wtx.content.wireless.LabeledWirelessTransceiverBlockEntity;
import cn.gtnh.ae2wtx.content.wireless.TransceiverVisuals;

/**
 * The black hole inside the transceiver and the channel sparks around it.
 * <p>
 * Model units (16 per block) around the block centre, scaled to 0.95 so the
 * disk clears the glass. The hole is drawn for the camera: a black shadow, a
 * thin accretion disk tilted 12 degrees towards the viewer, the lensed far
 * side of the disk arching over and under the shadow, and the photon ring.
 * Physics touches: Doppler beaming with a colour shift, gravitational
 * redshift at the inner edge and Keplerian differential rotation. Eight
 * sparks on three tilted orbits: the lit ones show the channels the whole
 * frequency uses, the share this transceiver carries burns brighter, and the
 * colour is the frequency load (see TransceiverVisuals).
 * Runs before the terrain's translucent pass, so the glass blends over it.
 */
public final class TransceiverEffectsRenderer extends TileEntitySpecialRenderer {

    private static final double SCALE = 0.95D / 16.0D;
    /** One animation loop in ticks; every band and spark phase repeats with it. */
    private static final int LOOP_TICKS = 80;

    private static final double SHADOW = 2.2;
    private static final double DISK_IN = 3.0;
    private static final double DISK_OUT = 6.0;
    private static final double TILT = 12;
    private static final double BETA = 0.35;
    private static final int DISK_RINGS = 8;
    private static final int DISK_SEGMENTS = 64;
    private static final int ARC_SEGMENTS = 48;
    private static final int RING_SEGMENTS = 64;
    private static final int SPHERE_LAT = 10;
    private static final int SPHERE_LON = 16;

    private static final double SPARK_ORBIT = 5.4;
    private static final double[][] ORBITS = {
        rotation(new double[] { 1, 0, 0 }, 70),
        multiply(rotation(new double[] { 0, 0, 1 }, 60), rotation(new double[] { 1, 0, 0 }, 20)),
        multiply(rotation(new double[] { 0, 0, 1 }, -60), rotation(new double[] { 1, 0, 0 }, 20)) };
    private static final float[][] LOAD_RGB = { rgb(0xb48cff), rgb(0xffb040), rgb(0xff4a4a) };
    private static final float[] SPARK_OFF = rgb(0x2a2536);

    private final double[] right = new double[3];
    private final double[] up = new double[3];
    private final double[] view = new double[3];
    private double[] tilt;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof LabeledWirelessTransceiverBlockEntity) || !ModConfig.clientRenderEffects
            || ModConfig.clientSimpleBlockModel || ClassicModel.isActive()) {
            return;
        }
        double cx = x + 0.5D, cy = y + 0.5D, cz = z + 0.5D;
        double dist = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double max = ModConfig.clientEffectRenderDistance;
        if (dist > max || dist < 1.0E-3) {
            return;
        }
        LabeledWirelessTransceiverBlockEntity lte = (LabeledWirelessTransceiverBlockEntity) te;
        boolean online = te.getWorldObj() != null && te.getWorldObj().getBlockMetadata(te.xCoord, te.yCoord,
            te.zCoord) == 1;
        long ticks = te.getWorldObj() == null ? 0 : te.getWorldObj().getTotalWorldTime();
        double t = ((ticks % LOOP_TICKS) + partialTicks) / LOOP_TICKS;
        camera(-cx / dist, -cy / dist, -cz / dist);

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT);
        GL11.glTranslated(cx, cy, cz);
        GL11.glScaled(SCALE, SCALE, SCALE);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        Tessellator tes = Tessellator.instance;

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        tes.startDrawingQuads();
        shadow(tes);
        int maxChannels = lte.getMaxChannelsForDisplay();
        int own = online ? TransceiverVisuals.litSparks(lte.getUsedChannelsForDisplay(), maxChannels) : 0;
        int lit = online
            ? Math.max(own, TransceiverVisuals.litSparks(lte.getNetworkChannelsForDisplay(), maxChannels))
            : 0;
        sparksOff(tes, t, online, lit);
        tes.draw();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        if (!online) {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            tes.startDrawingQuads();
            dormantDisk(tes);
            tes.draw();
        } else {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            float[] load = LOAD_RGB[TransceiverVisuals.load(lte.getNetworkChannelsForDisplay(), maxChannels)
                .ordinal()];
            tes.startDrawingQuads();
            double side = disk(tes, t);
            lensedArc(tes, t, side, 2.42, 3.6, true, 1.15);
            lensedArc(tes, t, side, 2.38, 2.9, false, 0.6);
            photonRing(tes, side, 2.22, 2.34, 1.0);
            photonRing(tes, side, 2.36, 2.41, 0.45);
            sparksOn(tes, t, lit, own, load);
            tes.draw();
            bloom(tes, 0, 0, 0, 6.0, rgb(0x9664ff), 0.08F);
            for (int k = 0; k < lit; k++) {
                double[] p = sparkPos(k, t, true, 0);
                bloom(tes, p[0], p[1], p[2], k < own ? 1.4 : 1.0, load, k < own ? 0.22F : 0.1F);
            }
        }
        GL11.glDepthMask(true);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    /* ------------------------------------------------------------ camera */

    private void camera(double vx, double vy, double vz) {
        view[0] = vx;
        view[1] = vy;
        view[2] = vz;
        // right = worldUp x view, falling back to +x when looking straight up or down
        double rx = vz, rz = -vx;
        double len = Math.sqrt(rx * rx + rz * rz);
        if (len < 1.0E-4) {
            rx = 1;
            rz = 0;
            len = 1;
        }
        right[0] = rx / len;
        right[1] = 0;
        right[2] = rz / len;
        cross(view, right, up);
        tilt = rotation(right, TILT);
    }

    /* ------------------------------------------------------------ black hole */

    private void shadow(Tessellator tes) {
        tes.setColorRGBA_F(0, 0, 0, 1);
        for (int i = 0; i < SPHERE_LAT; i++) {
            double t0 = Math.PI * i / SPHERE_LAT, t1 = Math.PI * (i + 1) / SPHERE_LAT;
            for (int j = 0; j < SPHERE_LON; j++) {
                double p0 = 2 * Math.PI * j / SPHERE_LON, p1 = 2 * Math.PI * (j + 1) / SPHERE_LON;
                spherePoint(tes, t0, p0);
                spherePoint(tes, t0, p1);
                spherePoint(tes, t1, p1);
                spherePoint(tes, t1, p0);
            }
        }
    }

    private static void spherePoint(Tessellator tes, double theta, double phi) {
        tes.addVertex(SHADOW * Math.sin(theta) * Math.cos(phi), SHADOW * Math.cos(theta),
            SHADOW * Math.sin(theta) * Math.sin(phi));
    }

    private void dormantDisk(Tessellator tes) {
        for (int i = 0; i < 6; i++) {
            double ra = DISK_IN + (DISK_OUT - DISK_IN) * i / 6, rb = DISK_IN + (DISK_OUT - DISK_IN) * (i + 1) / 6;
            tes.setColorRGBA_F(40 / 255F, 30 / 255F, 60 / 255F, 0.55F * (1 - i / 6F));
            for (int j = 0; j < 48; j++) {
                double a0 = 2 * Math.PI * j / 48, a1 = 2 * Math.PI * (j + 1) / 48;
                diskVertex(tes, ra, a0);
                diskVertex(tes, rb, a0);
                diskVertex(tes, rb, a1);
                diskVertex(tes, ra, a1);
            }
        }
    }

    /** Returns which screen side approaches the camera (+1 right), for the billboarded parts. */
    private double disk(Tessellator tes, double t) {
        double rs = 0.72 * DISK_IN;
        double[] tmp = new double[3];
        for (int i = 0; i < DISK_RINGS; i++) {
            double ra = DISK_IN + (DISK_OUT - DISK_IN) * i / DISK_RINGS;
            double rb = DISK_IN + (DISK_OUT - DISK_IN) * (i + 1) / DISK_RINGS;
            double rm = (ra + rb) / 2;
            double xr = (rm - DISK_IN) / (DISK_OUT - DISK_IN);
            double red = Math.pow(Math.sqrt(Math.max(0.05, 1 - rs / rm)), 1.5);
            int turns = Math.max(1, (int) Math.round(4 * Math.pow(DISK_IN / rm, 1.5)));
            for (int j = 0; j < DISK_SEGMENTS; j++) {
                double a0 = 2 * Math.PI * j / DISK_SEGMENTS, a1 = 2 * Math.PI * (j + 1) / DISK_SEGMENTS;
                double am = (a0 + a1) / 2;
                apply(tilt, -Math.sin(am), 0, Math.cos(am), tmp);
                double d = doppler(dot(tmp, view));
                // 9-fold bands; inner rings turn faster, a whole number of ninths per loop
                double phase = 9 * am - 2 * Math.PI * t * turns - 6 * Math.log(rm);
                color(tes, diskRgb(xr, phase, d, red), 0.85);
                diskVertex(tes, ra, a0);
                diskVertex(tes, rb, a0);
                diskVertex(tes, rb, a1);
                diskVertex(tes, ra, a1);
            }
        }
        double[] vel = new double[3], pos = new double[3];
        apply(tilt, 0, 0, 1, vel);
        apply(tilt, 1, 0, 0, pos);
        return (dot(vel, view) > 0) == (dot(pos, right) > 0) ? 1 : -1;
    }

    private void diskVertex(Tessellator tes, double r, double a) {
        double[] p = new double[3];
        apply(tilt, r * Math.cos(a), 0, r * Math.sin(a), p);
        tes.addVertex(p[0], p[1], p[2]);
    }

    /** The far side of the disk, bent over the top (or under the bottom) of the shadow. */
    private void lensedArc(Tessellator tes, double t, double side, double r0, double r1, boolean upper,
        double gain) {
        for (int j = 0; j < ARC_SEGMENTS; j++) {
            double a0 = Math.PI * j / ARC_SEGMENTS, a1 = Math.PI * (j + 1) / ARC_SEGMENTS;
            if (!upper) {
                a0 = -a0;
                a1 = -a1;
            }
            double am = (a0 + a1) / 2;
            double d = doppler(Math.cos(am) * side * 0.9);
            // hugs the shadow at the top, merges with the disk at the limbs
            double rb = r0 + (r1 - r0) * (0.35 + 0.65 * Math.abs(Math.sin(am)));
            for (int s = 0; s < 3; s++) {
                double q0 = r0 + (rb - r0) * s / 3, q1 = r0 + (rb - r0) * (s + 1) / 3;
                double phase = 9 * am * (upper ? 1 : -1) - 2 * Math.PI * t * 4;
                color(tes, diskRgb(s / 3.0 * 0.8, phase, d, 0.9), gain * (1 - 0.3 * s));
                billboard(tes, q0, a0);
                billboard(tes, q1, a0);
                billboard(tes, q1, a1);
                billboard(tes, q0, a1);
            }
        }
    }

    private void photonRing(Tessellator tes, double side, double ra, double rb, double gain) {
        for (int j = 0; j < RING_SEGMENTS; j++) {
            double a0 = 2 * Math.PI * j / RING_SEGMENTS, a1 = 2 * Math.PI * (j + 1) / RING_SEGMENTS;
            double d = doppler(Math.cos((a0 + a1) / 2) * side * 0.9);
            double k = gain * 0.45 * Math.min(Math.max(d * d, 0.45), 2.0);
            tes.setColorRGBA_F((float) (250 / 255.0 * k), (float) (238 / 255.0 * k), (float) k, 1);
            billboard(tes, ra, a0);
            billboard(tes, rb, a0);
            billboard(tes, rb, a1);
            billboard(tes, ra, a1);
        }
    }

    private void billboard(Tessellator tes, double r, double a) {
        double c = r * Math.cos(a), s = r * Math.sin(a);
        tes.addVertex(c * right[0] + s * up[0], c * right[1] + s * up[1], c * right[2] + s * up[2]);
    }

    private static double doppler(double cosV) {
        double g = 1 / Math.sqrt(1 - BETA * BETA);
        return 1 / (g * (1 - BETA * cosV));
    }

    /**
     * Disk colour 0..255 per channel: white-hot inner edge through violet to deep
     * purple, banded; approaching gas whiter and brighter, receding gas redder.
     */
    static double[] diskRgb(double x, double phase, double doppler, double redshift) {
        double a = Math.pow(1 - x, 3), b = 3 * x * (1 - x) * (1 - x) * 1.4, c = x * x;
        double[] col = { 255 * a + 190 * b + 80 * c, 248 * a + 140 * b + 30 * c, 255 * a + 255 * b + 150 * c };
        double bands = (0.6 + 0.4 * Math.sin(phase)) * (0.85 + 0.15 * Math.sin(2 * phase + 3 * x));
        if (doppler > 1) {
            double k = Math.min(0.6, (doppler - 1) * 0.5);
            col[0] += (235 - col[0]) * k;
            col[1] += (240 - col[1]) * k;
            col[2] += (255 - col[2]) * k;
        } else {
            col[1] *= 0.55 + 0.45 * doppler;
            col[2] *= 0.75 + 0.25 * doppler;
        }
        double boost = Math.min(Math.max(doppler * doppler * doppler, 0.5), 2.4);
        double k = bands * Math.pow(1 - x, 0.7) * boost * redshift;
        col[0] *= k;
        col[1] *= k;
        col[2] *= k;
        return col;
    }

    private static void color(Tessellator tes, double[] rgb255, double gain) {
        tes.setColorRGBA_F((float) (rgb255[0] * gain / 255), (float) (rgb255[1] * gain / 255),
            (float) (rgb255[2] * gain / 255), 1);
    }

    /* ------------------------------------------------------------ sparks */

    private static double[] sparkPos(int k, double t, boolean online, int back) {
        double a = 2 * Math.PI * k / TransceiverVisuals.SPARKS + (online ? 2 * Math.PI * t : 0) - back * 0.14;
        double[] p = new double[3];
        apply(ORBITS[k % 3], SPARK_ORBIT * Math.cos(a), 0, SPARK_ORBIT * Math.sin(a), p);
        return p;
    }

    private void sparksOff(Tessellator tes, double t, boolean online, int lit) {
        tes.setColorRGBA_F(SPARK_OFF[0], SPARK_OFF[1], SPARK_OFF[2], 1);
        for (int k = lit; k < TransceiverVisuals.SPARKS; k++) {
            square(tes, sparkPos(k, t, online, 0), 0.22);
        }
    }

    /** The first {@code own} of the {@code lit} sparks are this transceiver's share: full size and brightness. */
    private void sparksOn(Tessellator tes, double t, int lit, int own, float[] col) {
        for (int k = 0; k < lit; k++) {
            float gain = k < own ? 1F : 0.55F;
            double size = k < own ? 1 : 0.8;
            float head = 40 / 255F * gain;
            tes.setColorRGBA_F(col[0] * 0.9F * gain + head, col[1] * 0.9F * gain + head,
                col[2] * 0.9F * gain + head, 1);
            square(tes, sparkPos(k, t, true, 0), 0.3 * size);
            for (int q = 1; q <= 4; q++) {
                float f = (0.5F - q * 0.1F) * gain;
                tes.setColorRGBA_F(col[0] * f, col[1] * f, col[2] * f, 1);
                square(tes, sparkPos(k, t, true, q), (0.24 - q * 0.04) * size);
            }
        }
    }

    private void square(Tessellator tes, double[] c, double h) {
        for (int i = 0; i < 4; i++) {
            double sx = i == 0 || i == 3 ? -h : h, sy = i < 2 ? -h : h;
            tes.addVertex(c[0] + sx * right[0] + sy * up[0], c[1] + sx * right[1] + sy * up[1],
                c[2] + sx * right[2] + sy * up[2]);
        }
    }

    /** Soft additive glow facing the camera, standing in for bloom. */
    private void bloom(Tessellator tes, double x, double y, double z, double radius, float[] col, float strength) {
        tes.startDrawing(GL11.GL_TRIANGLE_FAN);
        float k = strength * 2;
        tes.setColorRGBA_F(col[0] * k, col[1] * k, col[2] * k, 1);
        tes.addVertex(x, y, z);
        tes.setColorRGBA_F(0, 0, 0, 1);
        for (int i = 0; i <= 16; i++) {
            double a = 2 * Math.PI * i / 16, c = radius * Math.cos(a), s = radius * Math.sin(a);
            tes.addVertex(x + c * right[0] + s * up[0], y + c * right[1] + s * up[1], z + c * right[2] + s * up[2]);
        }
        tes.draw();
    }

    /* ------------------------------------------------------------ maths */

    private static float[] rgb(int hex) {
        return new float[] { ((hex >> 16) & 0xff) / 255F, ((hex >> 8) & 0xff) / 255F, (hex & 0xff) / 255F };
    }

    /** Row-major 3x3 rotation by deg about a unit axis (right-handed). */
    static double[] rotation(double[] axis, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a), k = 1 - c;
        double x = axis[0], y = axis[1], z = axis[2];
        return new double[] { c + x * x * k, x * y * k - z * s, x * z * k + y * s, y * x * k + z * s,
            c + y * y * k, y * z * k - x * s, z * x * k - y * s, z * y * k + x * s, c + z * z * k };
    }

    private static double[] multiply(double[] a, double[] b) {
        double[] out = new double[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
            }
        }
        return out;
    }

    private static void apply(double[] m, double x, double y, double z, double[] out) {
        out[0] = m[0] * x + m[1] * y + m[2] * z;
        out[1] = m[3] * x + m[4] * y + m[5] * z;
        out[2] = m[6] * x + m[7] * y + m[8] * z;
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static void cross(double[] a, double[] b, double[] out) {
        out[0] = a[1] * b[2] - a[2] * b[1];
        out[1] = a[2] * b[0] - a[0] * b[2];
        out[2] = a[0] * b[1] - a[1] * b[0];
    }
}
