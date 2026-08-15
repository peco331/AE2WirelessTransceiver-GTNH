import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

/**
 * Offline preview renderer for 1.20.1 blockbench models (Light Mode transceiver).
 * Renders the model with the confirmed UV semantics (16 units = texture size,
 * 8 px/unit for 128px textures) to PNG so the geometry/texture mapping can be
 * verified WITHOUT launching Minecraft.
 *
 * Usage: java ModelPreview <model.json> <texture.png> <out.png>
 */
public class ModelPreview {

    static class Face {
        float[][] v = new float[4][3]; // world coords (0-16 space)
        float[][] uv = new float[4][2]; // texture coords 0-1
        float[] normal = new float[3];
        double depth;
    }

    static class Element {
        float[] from = new float[3];
        float[] to = new float[3];
        float[] rotOrigin = new float[3];
        String rotAxis = null;
        float rotAngle = 0;
        float[][] faces = new float[6][]; // direction -> uv[4] in 16-space, null if absent
    }

    static List<Element> parseModel(String json) {
        List<Element> out = new ArrayList<>();
        // split elements by top-level "from"
        Matcher m = Pattern.compile("\\{\\s*\"from\"\\s*:\\s*\\[([^\\]]+)\\]\\s*,\\s*\"to\"\\s*:\\s*\\[([^\\]]+)\\]").matcher(json);
        // need full elements: use a manual split on "name" or element boundaries
        // simpler: iterate elements via brace matching
        int idx = json.indexOf("\"elements\"");
        idx = json.indexOf('[', idx);
        int depth = 0;
        int start = -1;
        for (int i = idx; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') { depth++; if (depth == 1) start = i; }
            else if (c == ']') { depth--; if (depth == 0) { parseElements(json.substring(start + 1, i), out); break; } }
            else if (c == '{' && depth == 2) { /* nested objects inside elements */ }
        }
        return out;
    }

    static void parseElements(String body, List<Element> out) {
        // split top-level element objects (depth-aware)
        List<String> elems = splitTopLevel(body);
        for (String e : elems) {
            Element el = new Element();
            double[] f = getArray(e, "\"from\"");
            double[] t = getArray(e, "\"to\"");
            if (f == null || t == null) continue;
            for (int i = 0; i < 3; i++) { el.from[i] = (float) f[i]; el.to[i] = (float) t[i]; }
            Matcher rm = Pattern.compile("\"rotation\"\\s*:\\s*\\{[^}]*\"origin\"\\s*:\\s*\\[([^\\]]+)\\][^}]*\"axis\"\\s*:\\s*\"([xyz])\"[^}]*\"angle\"\\s*:\\s*([-\\d.]+)").matcher(e);
            if (rm.find()) {
                String[] o = rm.group(1).trim().split(",");
                for (int i = 0; i < 3; i++) el.rotOrigin[i] = Float.parseFloat(o[i].trim());
                el.rotAxis = rm.group(2);
                el.rotAngle = Float.parseFloat(rm.group(3));
            }
            Matcher fm = Pattern.compile("\"([a-z]+)\"\\s*:\\s*\\{\\s*\"uv\"\\s*:\\s*\\[([^\\]]+)\\]").matcher(e);
            while (fm.find()) {
                int di = dirIndex(fm.group(1));
                if (di < 0) continue;
                String[] uv = fm.group(2).trim().split(",");
                float[] u = new float[4];
                for (int i = 0; i < 4; i++) u[i] = Float.parseFloat(uv[i].trim());
                el.faces[di] = u;
            }
            out.add(el);
        }
    }

    static int dirIndex(String s) {
        switch (s) {
            case "down": return 0;
            case "up": return 1;
            case "north": return 2;
            case "south": return 3;
            case "west": return 4;
            case "east": return 5;
            default: return -1;
        }
    }

    static List<String> splitTopLevel(String body) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') { if (depth == 0) start = i; depth++; }
            else if (c == '}') { depth--; if (depth == 0) out.add(body.substring(start, i + 1)); }
        }
        return out;
    }

    static double[] getArray(String s, String key) {
        Matcher m = Pattern.compile(Pattern.quote(key) + "\\s*:\\s*\\[([^\\]]+)\\]").matcher(s);
        if (!m.find()) return null;
        String[] parts = m.group(1).trim().split(",");
        double[] r = new double[parts.length];
        for (int i = 0; i < parts.length; i++) r[i] = Double.parseDouble(parts[i].trim());
        return r;
    }

    // direction corner tables (world coords, matching our renderer)
    static final int[][][] CORNERS = {
        { { 1, 0, 0 }, { 1, 0, 1 }, { 0, 0, 1 }, { 0, 0, 0 } }, // down
        { { 0, 1, 1 }, { 0, 1, 0 }, { 1, 1, 0 }, { 1, 1, 1 } }, // up
        { { 0, 1, 0 }, { 1, 1, 0 }, { 1, 0, 0 }, { 0, 0, 0 } }, // north
        { { 1, 1, 1 }, { 0, 1, 1 }, { 0, 0, 1 }, { 1, 0, 1 } }, // south
        { { 0, 1, 1 }, { 0, 1, 0 }, { 0, 0, 0 }, { 0, 0, 1 } }, // west
        { { 1, 1, 0 }, { 1, 1, 1 }, { 1, 0, 1 }, { 1, 0, 0 } }, // east
    };
    static final float[][] UVMAP = {
        { 0, 0 }, { 0, 1 }, { 1, 1 }, { 1, 0 },
        { 0, 0 }, { 0, 1 }, { 1, 1 }, { 1, 0 },
        { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 },
        { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 },
        { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 },
        { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 },
    };

    static void rotate(float[] p, Element el) {
        if (el.rotAxis == null) return;
        float x = p[0] - el.rotOrigin[0], y = p[1] - el.rotOrigin[1], z = p[2] - el.rotOrigin[2];
        double rad = Math.toRadians(el.rotAngle);
        double c = Math.cos(rad), s = Math.sin(rad);
        float nx, ny, nz;
        switch (el.rotAxis) {
            case "x": ny = (float) (y * c - z * s); nz = (float) (y * s + z * c); nx = x; break;
            case "y": nx = (float) (x * c + z * s); nz = (float) (-x * s + z * c); ny = y; break;
            default: nx = (float) (x * c - y * s); ny = (float) (x * s + y * c); nz = z; break;
        }
        p[0] = nx + el.rotOrigin[0];
        p[1] = ny + el.rotOrigin[1];
        p[2] = nz + el.rotOrigin[2];
    }

    // camera: isometric-ish orthographic
    static double yaw = Math.toRadians(-35.0); // horizontal
    static double pitch = Math.toRadians(30.0); // vertical
    static int SIZE = 512;

    static double[] project(float x, float y, float z) {
        // rotate yaw around Y, then pitch around X
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        double rx = x * cy - z * sy;
        double rz = x * sy + z * cy;
        double ry = y;
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double py = ry * cp - rz * sp;
        double pz = ry * sp + rz * cp;
        double scale = SIZE * 0.55 / 16.0; // normalize 0-16 model space
        return new double[] { rx * scale + SIZE / 2.0, -py * scale + SIZE / 2.0, pz };
    }

    static boolean pointInQuad(double px, double py, double[][] q) {
        boolean inside = false;
        for (int i = 0, j = 3; i < 4; j = i++) {
            double xi = q[i][0], yi = q[i][1], xj = q[j][0], yj = q[j][1];
            if (((yi > py) != (yj > py)) && (px < (xj - xi) * (py - yi) / (yj - yi) + xi)) inside = !inside;
        }
        return inside;
    }

    static double[] barycentric(double px, double py, double[][] q) {
        // solve for uv via cross products on the projected quad (approximate)
        double x0 = q[0][0], y0 = q[0][1], x1 = q[1][0], y1 = q[1][1], x2 = q[2][0], y2 = q[2][1], x3 = q[3][0], y3 = q[3][1];
        double denom = (y1 - y2) * (x0 - x3) - (x1 - x2) * (y0 - y3);
        if (Math.abs(denom) < 1e-9) return new double[] { 0.5, 0.25, 0.25 };
        double a = ((y1 - y2) * (px - x3) - (x1 - x2) * (py - y3)) / denom;
        double b = ((y2 - y0) * (px - x3) - (x2 - x0) * (py - y3)) / denom;
        double c = 1 - a - b;
        return new double[] { a, b, c };
    }

    static int sample(BufferedImage tex, double u, double v, int[] outA) {
        double tx = Math.max(0, Math.min(0.9999, u)) * tex.getWidth();
        double ty = Math.max(0, Math.min(0.9999, v)) * tex.getHeight();
        int x0 = (int) tx, y0 = (int) ty;
        x0 = Math.min(x0, tex.getWidth() - 1);
        y0 = Math.min(y0, tex.getHeight() - 1);
        int rgb = tex.getRGB(x0, y0);
        int a = (rgb >> 24) & 0xFF;
        outA[0] = a;
        return rgb;
    }

    public static void main(String[] args) throws Exception {
        String modelPath = args[0], texPath = args[1], outPath = args[2];
        String json = new String(java.nio.file.Files.readAllBytes(new File(modelPath).toPath()), "UTF-8");
        List<Element> elements = parseModel(json);
        BufferedImage tex = ImageIO.read(new File(texPath));
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int[] bg = img.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE);
        for (int i = 0; i < bg.length; i++) bg[i] = 0xFF202020;
        img.setRGB(0, 0, SIZE, SIZE, bg, 0, SIZE);

        List<Face> faces = new ArrayList<>();
        for (Element el : elements) {
            for (int d = 0; d < 6; d++) {
                float[] uv = el.faces[d];
                if (uv == null) continue;
                Face f = new Face();
                for (int c = 0; c < 4; c++) {
                    float px = el.from[0] + (el.to[0] - el.from[0]) * CORNERS[d][c][0];
                    float py = el.from[1] + (el.to[1] - el.from[1]) * CORNERS[d][c][1];
                    float pz = el.from[2] + (el.to[2] - el.from[2]) * CORNERS[d][c][2];
                    f.v[c][0] = px; f.v[c][1] = py; f.v[c][2] = pz;
                }
                // rotate all 4 corners
                for (int c = 0; c < 4; c++) rotate(f.v[c], el);
                float[] uvmap = UVMAP[d * 4];
                for (int c = 0; c < 4; c++) {
                    float[] uvC = UVMAP[d * 4 + c];
                    f.uv[c][0] = (uv[0] + uvC[0] * (uv[2] - uv[0])) / 16.0f;
                    f.uv[c][1] = (uv[1] + uvC[1] * (uv[3] - uv[1])) / 16.0f;
                }
                // normal from first 3 vertices
                float ax = f.v[1][0] - f.v[0][0], ay = f.v[1][1] - f.v[0][1], az = f.v[1][2] - f.v[0][2];
                float bx = f.v[2][0] - f.v[0][0], by = f.v[2][1] - f.v[0][1], bz = f.v[2][2] - f.v[0][2];
                f.normal[0] = ay * bz - az * by;
                f.normal[1] = az * bx - ax * bz;
                f.normal[2] = ax * by - ay * bx;
                faces.add(f);
            }
        }
        // depth sort
        for (Face f : faces) {
            double dz = 0;
            for (int c = 0; c < 4; c++) dz += project(f.v[c][0], f.v[c][1], f.v[c][2])[2];
            f.depth = dz / 4;
        }
        Collections.sort(faces, new Comparator<Face>() {
            public int compare(Face a, Face b) { return Double.compare(b.depth, a.depth); }
        });

        for (Face f : faces) {
            double[][] q = new double[4][2];
            for (int c = 0; c < 4; c++) {
                double[] p = project(f.v[c][0], f.v[c][1], f.v[c][2]);
                q[c][0] = p[0]; q[c][1] = p[1];
            }
            int minX = SIZE, minY = SIZE, maxX = 0, maxY = 0;
            for (int c = 0; c < 4; c++) {
                minX = Math.min(minX, (int) Math.floor(q[c][0]));
                maxX = Math.max(maxX, (int) Math.ceil(q[c][0]));
                minY = Math.min(minY, (int) Math.floor(q[c][1]));
                maxY = Math.max(maxY, (int) Math.ceil(q[c][1]));
            }
            minX = Math.max(0, minX); maxX = Math.min(SIZE - 1, maxX);
            minY = Math.max(0, minY); maxY = Math.min(SIZE - 1, maxY);
            // simple lighting
            float[] light = { -0.5f, 0.7f, 0.5f };
            double nl = Math.abs(f.normal[0] * light[0] + f.normal[1] * light[1] + f.normal[2] * light[2])
                / (Math.sqrt(f.normal[0] * f.normal[0] + f.normal[1] * f.normal[1] + f.normal[2] * f.normal[2]) + 1e-9)
                * 0.6 + 0.4;
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    if (!pointInQuad(x + 0.5, y + 0.5, q)) continue;
                    double[] bc = barycentric(x + 0.5, y + 0.5, q);
                    double u = bc[0] * f.uv[0][0] + bc[1] * f.uv[1][0] + bc[2] * f.uv[2][0] + (1 - bc[0] - bc[1] - bc[2]) * f.uv[3][0];
                    double v = bc[0] * f.uv[0][1] + bc[1] * f.uv[1][1] + bc[2] * f.uv[2][1] + (1 - bc[0] - bc[1] - bc[2]) * f.uv[3][1];
                    int[] a = new int[1];
                    int rgb = sample(tex, u, v, a);
                    int alpha = a[0];
                    if (alpha < 20) continue;
                    int r = (int) (((rgb >> 16) & 0xFF) * nl);
                    int g = (int) (((rgb >> 8) & 0xFF) * nl);
                    int b = (int) ((rgb & 0xFF) * nl);
                    // blend over existing
                    int cur = img.getRGB(x, y);
                    int ca = (cur >> 24) & 0xFF;
                    int na = Math.min(255, ca + alpha);
                    double fa = alpha / 255.0;
                    int nr = (int) (r * fa + ((cur >> 16) & 0xFF) * (1 - fa));
                    int ng = (int) (g * fa + ((cur >> 8) & 0xFF) * (1 - fa));
                    int nb = (int) (b * fa + (cur & 0xFF) * (1 - fa));
                    img.setRGB(x, y, (na << 24) | (nr << 16) | (ng << 8) | nb);
                }
            }
        }
        ImageIO.write(img, "png", new File(outPath));
        System.out.println("faces=" + faces.size() + " -> " + outPath);
    }
}
