package cn.gtnh.ae2wtx.client.render;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Writes textured quads in model units (0..16 per block) to the Tessellator.
 * In the world the face shade is baked into the vertex colour like vanilla
 * flat lighting; for the inventory item it sets normals for GL lighting instead.
 */
final class QuadWriter {

    /** Vanilla face shade: up, down, north/south, west/east. */
    private static final float SHADE_UP = 1.0F;
    private static final float SHADE_DOWN = 0.5F;
    private static final float SHADE_Z = 0.8F;
    private static final float SHADE_X = 0.6F;

    static final int FULL_BRIGHT = 0xF000F0;

    private final Tessellator t;
    private final double ox;
    private final double oy;
    private final double oz;
    private final boolean inventory;
    private final double[][] v = new double[4][3];
    private final double[][] uv = new double[4][2];

    QuadWriter(Tessellator t, double ox, double oy, double oz, boolean inventory) {
        this.t = t;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.inventory = inventory;
    }

    /**
     * Texture coordinates a face projects onto (model units, v down), the same
     * convention as JSON block models: u runs left to right as seen from outside.
     */
    static double projectU(ForgeDirection face, double x, double y, double z) {
        switch (face) {
            case NORTH:
                return 16 - x;
            case WEST:
                return z;
            case EAST:
                return 16 - z;
            default:
                return x;   // up, down, south
        }
    }

    static double projectV(ForgeDirection face, double x, double y, double z) {
        switch (face) {
            case UP:
                return z;
            case DOWN:
                return 16 - z;
            default:
                return 16 - y;
        }
    }

    static float shade(double nx, double ny, double nz) {
        double len = nx * nx + ny * ny + nz * nz;
        return (float) ((nx * nx * SHADE_X + nz * nz * SHADE_Z + ny * ny * (ny > 0 ? SHADE_UP : SHADE_DOWN)) / len);
    }

    void vertex(int i, double x, double y, double z, double u, double vv) {
        v[i][0] = x;
        v[i][1] = y;
        v[i][2] = z;
        uv[i][0] = u;
        uv[i][1] = vv;
    }

    /**
     * Emit the four vertices set with {@link #vertex}, front side towards the
     * given outward normal (the order is flipped when needed so culling keeps it).
     * colour is multiplied by the face shade in the world.
     */
    void emit(IIcon icon, double nx, double ny, double nz, int brightness, float r, float g, float b, float a) {
        double ax = v[1][0] - v[0][0], ay = v[1][1] - v[0][1], az = v[1][2] - v[0][2];
        double bx = v[2][0] - v[0][0], by = v[2][1] - v[0][1], bz = v[2][2] - v[0][2];
        boolean flip = (ay * bz - az * by) * nx + (az * bx - ax * bz) * ny + (ax * by - ay * bx) * nz < 0;
        if (inventory) {
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            t.setNormal((float) (nx / len), (float) (ny / len), (float) (nz / len));
            t.setColorRGBA_F(r, g, b, a);
        } else {
            float s = brightness == FULL_BRIGHT ? 1.0F : shade(nx, ny, nz);
            t.setBrightness(brightness);
            t.setColorRGBA_F(r * s, g * s, b * s, a);
        }
        for (int k = 0; k < 4; k++) {
            int i = flip ? (4 - k) % 4 : k;
            t.addVertexWithUV(ox + v[i][0] / 16.0D, oy + v[i][1] / 16.0D, oz + v[i][2] / 16.0D,
                icon.getInterpolatedU(uv[i][0]), icon.getInterpolatedV(uv[i][1]));
        }
    }

    /**
     * One face of an axis-aligned box. uvRect is {u0, v0, u1, v1} in model units,
     * or null for the face's own projection; rotation (0/90/180/270) turns the
     * texture clockwise like the JSON model face property.
     */
    void boxFace(ForgeDirection face, double x0, double y0, double z0, double x1, double y1, double z1,
        double[] uvRect, int rotation, IIcon icon, int brightness, float r, float g, float b, float a) {
        double[][] c;
        switch (face) {
            case DOWN:
                c = new double[][] { { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 }, { x0, y0, z1 } };
                break;
            case UP:
                c = new double[][] { { x0, y1, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x0, y1, z1 } };
                break;
            case NORTH:
                c = new double[][] { { x0, y0, z0 }, { x1, y0, z0 }, { x1, y1, z0 }, { x0, y1, z0 } };
                break;
            case SOUTH:
                c = new double[][] { { x0, y0, z1 }, { x1, y0, z1 }, { x1, y1, z1 }, { x0, y1, z1 } };
                break;
            case WEST:
                c = new double[][] { { x0, y0, z0 }, { x0, y0, z1 }, { x0, y1, z1 }, { x0, y1, z0 } };
                break;
            default:
                c = new double[][] { { x1, y0, z0 }, { x1, y0, z1 }, { x1, y1, z1 }, { x1, y1, z0 } };
                break;
        }
        double pu0 = Double.MAX_VALUE, pu1 = -Double.MAX_VALUE, pv0 = Double.MAX_VALUE, pv1 = -Double.MAX_VALUE;
        double[][] p = new double[4][2];
        for (int i = 0; i < 4; i++) {
            p[i][0] = projectU(face, c[i][0], c[i][1], c[i][2]);
            p[i][1] = projectV(face, c[i][0], c[i][1], c[i][2]);
            pu0 = Math.min(pu0, p[i][0]);
            pu1 = Math.max(pu1, p[i][0]);
            pv0 = Math.min(pv0, p[i][1]);
            pv1 = Math.max(pv1, p[i][1]);
        }
        for (int i = 0; i < 4; i++) {
            if (uvRect == null) {
                vertex(i, c[i][0], c[i][1], c[i][2], p[i][0], p[i][1]);
                continue;
            }
            double s = pu1 > pu0 ? (p[i][0] - pu0) / (pu1 - pu0) : 0;
            double tt = pv1 > pv0 ? (p[i][1] - pv0) / (pv1 - pv0) : 0;
            double rs, rt;
            switch (((rotation % 360) + 360) % 360) {
                case 90:
                    rs = tt;
                    rt = 1 - s;
                    break;
                case 180:
                    rs = 1 - s;
                    rt = 1 - tt;
                    break;
                case 270:
                    rs = 1 - tt;
                    rt = s;
                    break;
                default:
                    rs = s;
                    rt = tt;
                    break;
            }
            vertex(i, c[i][0], c[i][1], c[i][2], uvRect[0] + rs * (uvRect[2] - uvRect[0]),
                uvRect[1] + rt * (uvRect[3] - uvRect[1]));
        }
        emit(icon, face.offsetX, face.offsetY, face.offsetZ, brightness, r, g, b, a);
    }
}
