package cn.gtnh.ae2wtx.client.render;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Minimal 1.20.1-style block model reader: elements with from/to boxes,
 * optional rotation (origin/axis/angle) and per-direction faces with
 * 16-unit UVs. Only what the transceiver Light Mode model needs.
 */
public class LightModel {

    public final List<Element> elements = new ArrayList<>();

    /** Load from the mod's resources; returns null on failure. */
    public static LightModel load(String path) {
        try {
            ResourceLocation loc = new ResourceLocation("ae2wtx", "models/block/light/" + path);
            InputStreamReader reader = new InputStreamReader(
                cn.gtnh.ae2wtx.AE2Wtx.class.getClassLoader().getResourceAsStream(
                    "assets/ae2wtx/models/block/light/" + path),
                StandardCharsets.UTF_8);
            if (reader == null) {
                return null;
            }
            LightModel model = new LightModel();
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            reader.close();
            for (JsonElement el : root.getAsJsonArray("elements")) {
                model.elements.add(Element.parse(el.getAsJsonObject()));
            }
            return model;
        } catch (Throwable t) {
            cn.gtnh.ae2wtx.AE2Wtx.LOG.warn("ae2wtx: failed to load light model " + path, t);
            return null;
        }
    }

    public static class Element {

        public final float[] from = new float[3];
        public final float[] to = new float[3];
        public Rotation rotation;
        public final Face[] faces = new Face[6]; // indexed by 0=down,1=up,2=north,3=south,4=west,5=east

        static Element parse(JsonObject o) {
            Element e = new Element();
            copy3(o.getAsJsonArray("from"), e.from);
            copy3(o.getAsJsonArray("to"), e.to);
            if (o.has("rotation")) {
                JsonObject r = o.getAsJsonObject("rotation");
                e.rotation = new Rotation();
                copy3(r.getAsJsonArray("origin"), e.rotation.origin);
                e.rotation.axis = r.get("axis").getAsString();
                e.rotation.angle = r.get("angle").getAsFloat();
            }
            if (o.has("faces")) {
                JsonObject faces = o.getAsJsonObject("faces");
                for (java.util.Map.Entry<String, JsonElement> entry : faces.entrySet()) {
                    String dir = entry.getKey();
                    Face f = Face.parse(entry.getValue().getAsJsonObject());
                    e.faces[faceIndex(dir)] = f;
                }
            }
            return e;
        }

        static void copy3(JsonArray a, float[] out) {
            out[0] = a.get(0).getAsFloat();
            out[1] = a.get(1).getAsFloat();
            out[2] = a.get(2).getAsFloat();
        }

        static int faceIndex(String dir) {
            switch (dir) {
                case "down":
                    return 0;
                case "up":
                    return 1;
                case "north":
                    return 2;
                case "south":
                    return 3;
                case "west":
                    return 4;
                case "east":
                    return 5;
                default:
                    return -1;
            }
        }
    }

    public static class Rotation {

        public final float[] origin = new float[3];
        public String axis;
        public float angle;
    }

    public static class Face {

        public final float[] uv = new float[4]; // u1,v1,u2,v2 (16-unit space)

        static Face parse(JsonObject o) {
            Face f = new Face();
            JsonArray a = o.getAsJsonArray("uv");
            f.uv[0] = a.get(0).getAsFloat();
            f.uv[1] = a.get(1).getAsFloat();
            f.uv[2] = a.get(2).getAsFloat();
            f.uv[3] = a.get(3).getAsFloat();
            return f;
        }
    }
}
