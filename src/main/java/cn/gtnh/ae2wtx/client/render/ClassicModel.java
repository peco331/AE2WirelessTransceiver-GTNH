package cn.gtnh.ae2wtx.client.render;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cn.gtnh.ae2wtx.AE2Wtx;

/**
 * An optional resource-pack look: when a pack provides
 * assets/ae2wtx/models/classic/transceiver_off.json and transceiver_on.json,
 * the block renders those instead of the built-in shell and the effects stay
 * off. The files use the modern JSON block-model format (Blockbench "Java
 * block"): axis-aligned elements with per-face uv, texture and rotation, and
 * light_emission. Element rotation is not supported. tools/packs/build_packs.py
 * builds such a pack from the look releases up to 1.1.0 used.
 */
public final class ClassicModel {

    private static final String PATH = "models/classic/transceiver_";

    static final class Face {

        ForgeDirection dir;
        double[] uv;
        int rotation;
        IIcon icon;
    }

    static final class Element {

        double[] from;
        double[] to;
        boolean emissive;
        final List<Face> faces = new ArrayList<>();
    }

    final List<Element> elements = new ArrayList<>();
    /** render_type "translucent" draws in the alpha-blended pass 1, anything else in pass 0. */
    boolean translucent;

    private static volatile ClassicModel off;
    private static volatile ClassicModel on;

    private ClassicModel() {}

    public static boolean isActive() {
        return off != null && on != null;
    }

    static ClassicModel get(boolean online) {
        return online ? on : off;
    }

    /** Called while the block atlas is rebuilt, i.e. after every resource reload. */
    public static void reload(IIconRegister reg) {
        off = null;
        on = null;
        JsonObject offJson = read("off");
        JsonObject onJson = read("on");
        if (offJson == null || onJson == null) {
            return;
        }
        try {
            ClassicModel parsedOff = parse(offJson, reg);
            ClassicModel parsedOn = parse(onJson, reg);
            off = parsedOff;
            on = parsedOn;
            AE2Wtx.LOG.info("Using the transceiver model from a resource pack ({} / {} elements)",
                parsedOff.elements.size(), parsedOn.elements.size());
        } catch (RuntimeException e) {
            AE2Wtx.LOG.warn("Ignoring the resource-pack transceiver model: {}", e.toString());
        }
    }

    private static JsonObject read(String state) {
        ResourceLocation loc = new ResourceLocation(AE2Wtx.MODID, PATH + state + ".json");
        try (InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(loc).getInputStream()) {
            return new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            return null;    // no pack provides it
        } catch (RuntimeException e) {
            AE2Wtx.LOG.warn("Ignoring unreadable {}: {}", loc, e.toString());
            return null;
        }
    }

    private static ClassicModel parse(JsonObject json, IIconRegister reg) {
        ClassicModel model = new ClassicModel();
        model.translucent = json.has("render_type") && "translucent".equals(json.get("render_type").getAsString());
        Map<String, String> textures = new HashMap<>();
        if (json.has("textures")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("textures").entrySet()) {
                textures.put(e.getKey(), e.getValue().getAsString());
            }
        }
        Map<String, IIcon> icons = new HashMap<>();
        for (JsonElement el : json.getAsJsonArray("elements")) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("rotation") && o.getAsJsonObject("rotation").get("angle").getAsDouble() != 0) {
                throw new IllegalArgumentException("rotated elements are not supported");
            }
            Element element = new Element();
            element.from = vec(o.getAsJsonArray("from"), 3);
            element.to = vec(o.getAsJsonArray("to"), 3);
            element.emissive = o.has("light_emission") && o.get("light_emission").getAsInt() > 0;
            for (Map.Entry<String, JsonElement> f : o.getAsJsonObject("faces").entrySet()) {
                JsonObject fo = f.getValue().getAsJsonObject();
                Face face = new Face();
                face.dir = direction(f.getKey());
                face.uv = fo.has("uv") ? vec(fo.getAsJsonArray("uv"), 4) : null;
                face.rotation = fo.has("rotation") ? fo.get("rotation").getAsInt() : 0;
                face.icon = icon(fo.get("texture").getAsString(), textures, icons, reg);
                element.faces.add(face);
            }
            model.elements.add(element);
        }
        return model;
    }

    private static IIcon icon(String ref, Map<String, String> textures, Map<String, IIcon> icons,
        IIconRegister reg) {
        String name = ref;
        for (int depth = 0; name.startsWith("#") && depth < 8; depth++) {
            name = textures.get(name.substring(1));
            if (name == null) {
                throw new IllegalArgumentException("undefined texture " + ref);
            }
        }
        IIcon icon = icons.get(name);
        if (icon == null) {
            // "ns:blocks/x" (modern layout) and "ns:x" both mean textures/blocks/x
            int colon = name.indexOf(':');
            String ns = colon < 0 ? "minecraft" : name.substring(0, colon);
            String path = colon < 0 ? name : name.substring(colon + 1);
            for (String prefix : new String[] { "blocks/", "block/" }) {
                if (path.startsWith(prefix)) {
                    path = path.substring(prefix.length());
                }
            }
            icon = reg.registerIcon(ns + ":" + path);
            icons.put(name, icon);
        }
        return icon;
    }

    private static ForgeDirection direction(String name) {
        switch (name) {
            case "down":
                return ForgeDirection.DOWN;
            case "up":
                return ForgeDirection.UP;
            case "north":
                return ForgeDirection.NORTH;
            case "south":
                return ForgeDirection.SOUTH;
            case "west":
                return ForgeDirection.WEST;
            case "east":
                return ForgeDirection.EAST;
            default:
                throw new IllegalArgumentException("unknown face " + name);
        }
    }

    private static double[] vec(JsonArray array, int size) {
        if (array == null || array.size() != size) {
            throw new IllegalArgumentException("expected " + size + " numbers");
        }
        double[] out = new double[size];
        for (int i = 0; i < size; i++) {
            out[i] = array.get(i).getAsDouble();
        }
        return out;
    }
}
