package cn.gtnh.ae2wtx.wireless;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.Test;

import cn.gtnh.ae2wtx.config.ModConfig;

public class LabelNetworkRegistryTest {

    private static final UUID OWNER = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

    @Test
    public void aeProfileIsAlwaysCompleteSoAe2ResolvesAPlayerId() {
        // AE2 PlayerRegistry.getID returns -1 for an incomplete profile; a band
        // virtual node with id -1 is rejected by every active Security Station.
        assertTrue(LabelNetworkRegistry.aeProfile(OWNER, null).isComplete());
        assertTrue(LabelNetworkRegistry.aeProfile(OWNER, "").isComplete());
        assertTrue(LabelNetworkRegistry.aeProfile(OWNER, "  ").isComplete());
        assertEquals(OWNER, LabelNetworkRegistry.aeProfile(OWNER, null).getId());
        assertEquals("peco", LabelNetworkRegistry.aeProfile(OWNER, "peco").getName());
    }

    @Test
    public void rejectsInvalidLabelsAndTrimsValidLabels() {
        assertEquals("band_01-X", LabelNetworkRegistry.normalizeLabel("  band_01-X  "));
        assertNull(LabelNetworkRegistry.normalizeLabel("bad label"));
        assertNull(LabelNetworkRegistry.normalizeLabel(""));
    }

    @Test
    public void paginatesAndFiltersWithoutEncodingAnUnboundedList() {
        boolean oldCrossDimensional = ModConfig.wirelessCrossDimEnable;
        ModConfig.wirelessCrossDimEnable = true;
        try {
            LabelNetworkRegistry registry = new LabelNetworkRegistry();
            NBTTagCompound root = rootTag();
            NBTTagList bands = new NBTTagList();
            for (int i = 0; i < 150; i++) {
                bands.appendTag(band("band_" + i, 1_000_000L + i, new NBTTagList()));
            }
            root.setTag("networks", bands);
            registry.readFromNBT(root);

            LabelNetworkRegistry.PagedSnapshots page = registry.listNetworks(null, OWNER, "band_", 2, 64);
            assertEquals(22, page.entries.size());
            assertEquals(2, page.page);
            assertEquals(64, page.pageSize);
            assertEquals(150, page.totalEntries);
            assertEquals(3, page.pageCount);

            LabelNetworkRegistry.PagedSnapshots smallest = registry.listNetworks(null, OWNER, "band_", -9, 0);
            assertEquals(0, smallest.page);
            assertEquals(1, smallest.pageSize);
            assertEquals(1, smallest.entries.size());
            assertEquals(150, smallest.pageCount);

            LabelNetworkRegistry.PagedSnapshots largest = registry.listNetworks(null, OWNER, "band_", 99, 999);
            assertEquals(1, largest.page);
            assertEquals(128, largest.pageSize);
            assertEquals(22, largest.entries.size());
            assertEquals(2, largest.pageCount);

            assertEquals(150, registry.listNetworks(null, OWNER, " BAND_ ", 0, 64).totalEntries);

            LabelNetworkRegistry.PagedSnapshots empty = registry.listNetworks(null, OWNER, "not-present", 99, 64);
            assertEquals(0, empty.page);
            assertEquals(0, empty.totalEntries);
            assertEquals(1, empty.pageCount);
            assertEquals(0, empty.entries.size());
        } finally {
            ModConfig.wirelessCrossDimEnable = oldCrossDimensional;
        }
    }

    @Test
    public void skipsOneCorruptEndpointWithoutDroppingItsBand() {
        boolean oldCrossDimensional = ModConfig.wirelessCrossDimEnable;
        ModConfig.wirelessCrossDimEnable = true;
        try {
            NBTTagCompound corruptEndpoint = new NBTTagCompound();
            corruptEndpoint.setInteger("dim", 0);
            corruptEndpoint.setInteger("y", 64);
            corruptEndpoint.setInteger("z", 2);
            NBTTagList endpoints = new NBTTagList();
            endpoints.appendTag(corruptEndpoint);

            NBTTagCompound root = rootTag();
            NBTTagList bands = new NBTTagList();
            bands.appendTag(band("survives", 1_000_000L, endpoints));
            root.setTag("networks", bands);

            LabelNetworkRegistry registry = new LabelNetworkRegistry();
            registry.readFromNBT(root);
            NBTTagCompound rewritten = new NBTTagCompound();
            registry.writeToNBT(rewritten);
            NBTTagList rewrittenBands = rewritten.getTagList("networks", 10);
            assertEquals(1, rewrittenBands.tagCount());
            assertEquals(0, rewrittenBands.getCompoundTagAt(0).getTagList("endpoints", 10).tagCount());
        } finally {
            ModConfig.wirelessCrossDimEnable = oldCrossDimensional;
        }
    }

    @Test
    public void repairsDuplicateChannelsDeterministically() {
        boolean oldCrossDimensional = ModConfig.wirelessCrossDimEnable;
        ModConfig.wirelessCrossDimEnable = true;
        try {
            NBTTagCompound root = rootTag();
            NBTTagList bands = new NBTTagList();
            bands.appendTag(band("first", 1_000_000L, new NBTTagList()));
            bands.appendTag(band("second", 1_000_000L, new NBTTagList()));
            root.setTag("networks", bands);

            LabelNetworkRegistry registry = new LabelNetworkRegistry();
            registry.readFromNBT(root);
            NBTTagCompound rewritten = new NBTTagCompound();
            registry.writeToNBT(rewritten);
            NBTTagList rewrittenBands = rewritten.getTagList("networks", 10);
            assertEquals(2, rewrittenBands.tagCount());
            NBTTagCompound first = findBand(rewrittenBands, "first", null);
            NBTTagCompound second = findBand(rewrittenBands, "second", null);
            assertNotNull(first);
            assertNotNull(second);
            assertEquals(1_000_000L, first.getLong("channel"));
            assertEquals(1_000_001L, second.getLong("channel"));
            assertNotEquals(first.getLong("channel"), second.getLong("channel"));
            assertEquals(1_000_002L, rewritten.getLong("nextChannel"));
        } finally {
            ModConfig.wirelessCrossDimEnable = oldCrossDimensional;
        }
    }

    @Test
    public void mergesDimensionScopedBandsWhenCrossDimensionIsEnabled() {
        boolean oldCrossDimensional = ModConfig.wirelessCrossDimEnable;
        ModConfig.wirelessCrossDimEnable = true;
        try {
            NBTTagCompound root = rootTag();
            root.setInteger("dataVersion", 2);
            root.setString("scopeMode", "dimension");
            NBTTagList bands = new NBTTagList();
            NBTTagCompound overworld = band("shared", 1_000_005L, endpoints(endpoint(0, 1, 64, 1)));
            overworld.setInteger("dim", 0);
            NBTTagCompound nether = band("shared", 1_000_003L, endpoints(endpoint(-1, 2, 65, 2)));
            nether.setInteger("dim", -1);
            bands.appendTag(overworld);
            bands.appendTag(nether);
            root.setTag("networks", bands);

            LabelNetworkRegistry registry = new LabelNetworkRegistry();
            registry.readFromNBT(root);
            NBTTagCompound rewritten = new NBTTagCompound();
            registry.writeToNBT(rewritten);
            NBTTagList rewrittenBands = rewritten.getTagList("networks", 10);
            assertEquals(1, rewrittenBands.tagCount());
            NBTTagCompound merged = rewrittenBands.getCompoundTagAt(0);
            assertEquals("shared", merged.getString("label"));
            assertEquals(1_000_003L, merged.getLong("channel"));
            assertEquals(2, merged.getTagList("endpoints", 10).tagCount());
            assertEquals("global", rewritten.getString("scopeMode"));
        } finally {
            ModConfig.wirelessCrossDimEnable = oldCrossDimensional;
        }
    }

    @Test
    public void splitsGlobalBandsByEndpointDimensionAndUsesOriginForEmptyBands() {
        boolean oldCrossDimensional = ModConfig.wirelessCrossDimEnable;
        ModConfig.wirelessCrossDimEnable = false;
        try {
            NBTTagCompound root = rootTag();
            root.setInteger("dataVersion", 2);
            root.setString("scopeMode", "global");
            NBTTagList bands = new NBTTagList();
            NBTTagCompound shared = band(
                "shared",
                1_000_000L,
                endpoints(endpoint(0, 1, 64, 1), endpoint(-1, 2, 65, 2)));
            shared.setInteger("originDim", 7);
            bands.appendTag(shared);
            NBTTagCompound empty = band("empty", 1_000_010L, new NBTTagList());
            empty.setInteger("originDim", 7);
            bands.appendTag(empty);
            root.setTag("networks", bands);

            LabelNetworkRegistry registry = new LabelNetworkRegistry();
            registry.readFromNBT(root);
            NBTTagCompound rewritten = new NBTTagCompound();
            registry.writeToNBT(rewritten);
            NBTTagList rewrittenBands = rewritten.getTagList("networks", 10);
            assertEquals(3, rewrittenBands.tagCount());
            NBTTagCompound nether = findBand(rewrittenBands, "shared", -1);
            NBTTagCompound overworld = findBand(rewrittenBands, "shared", 0);
            NBTTagCompound origin = findBand(rewrittenBands, "empty", 7);
            assertNotNull(nether);
            assertNotNull(overworld);
            assertNotNull(origin);
            assertEquals(1, nether.getTagList("endpoints", 10).tagCount());
            assertEquals(1, overworld.getTagList("endpoints", 10).tagCount());
            assertEquals(0, origin.getTagList("endpoints", 10).tagCount());
            assertNotEquals(nether.getLong("channel"), overworld.getLong("channel"));
            assertNotEquals(origin.getLong("channel"), overworld.getLong("channel"));
            assertEquals("dimension", rewritten.getString("scopeMode"));
        } finally {
            ModConfig.wirelessCrossDimEnable = oldCrossDimensional;
        }
    }

    @Test
    public void migratesLegacyGlobalDataWithoutVersionOrOriginDimension() {
        boolean oldCrossDimensional = ModConfig.wirelessCrossDimEnable;
        ModConfig.wirelessCrossDimEnable = false;
        try {
            NBTTagCompound root = new NBTTagCompound();
            root.setLong("nextChannel", 1_000_000L);
            NBTTagList bands = new NBTTagList();
            bands.appendTag(band("legacy", 1_000_000L, endpoints(endpoint(2, 4, 70, 6))));
            root.setTag("networks", bands);

            LabelNetworkRegistry registry = new LabelNetworkRegistry();
            registry.readFromNBT(root);
            NBTTagCompound rewritten = new NBTTagCompound();
            registry.writeToNBT(rewritten);
            assertEquals(3, rewritten.getInteger("dataVersion"));
            assertEquals("dimension", rewritten.getString("scopeMode"));
            assertNotNull(findBand(rewritten.getTagList("networks", 10), "legacy", 2));
        } finally {
            ModConfig.wirelessCrossDimEnable = oldCrossDimensional;
        }
    }

    @Test
    public void resolvesSavedEndpointOnlyForItsOwner() {
        boolean oldCrossDimensional = ModConfig.wirelessCrossDimEnable;
        ModConfig.wirelessCrossDimEnable = true;
        try {
            NBTTagCompound root = rootTag();
            NBTTagList bands = new NBTTagList();
            bands.appendTag(band("recover_me", 1_000_021L, endpoints(endpoint(180, 5, 64, 4))));
            root.setTag("networks", bands);

            LabelNetworkRegistry registry = new LabelNetworkRegistry();
            registry.readFromNBT(root);
            LabelNetworkRegistry.LabelNetwork recovered =
                registry.getNetworkForEndpoint(180, 5, 64, 4, OWNER);

            assertNotNull(recovered);
            assertEquals("recover_me", recovered.label());
            assertEquals(1_000_021L, recovered.channel());
            assertNull(registry.getNetworkForEndpoint(180, 5, 64, 4, UUID.randomUUID()));
            assertNull(registry.getNetworkForEndpoint(180, 5, 64, 5, OWNER));
        } finally {
            ModConfig.wirelessCrossDimEnable = oldCrossDimensional;
        }
    }

    @Test
    public void listsSavedEndpointsSortedAndLimitedForTheOwnerOnly() {
        boolean oldCrossDimensional = ModConfig.wirelessCrossDimEnable;
        ModConfig.wirelessCrossDimEnable = true;
        try {
            NBTTagCompound root = rootTag();
            NBTTagList bands = new NBTTagList();
            bands.appendTag(band("locate", 1_000_030L,
                endpoints(endpoint(0, 9, 64, 1), endpoint(-1, 3, 70, 3), endpoint(0, 2, 65, 8))));
            root.setTag("networks", bands);
            LabelNetworkRegistry registry = new LabelNetworkRegistry();
            registry.readFromNBT(root);

            LabelNetworkRegistry.EndpointList all = registry.listEndpoints(null, "locate", OWNER, 10);
            assertEquals(3, all.total);
            assertEquals(3, all.entries.size());
            assertEquals(-1, all.entries.get(0).dim);
            assertEquals(2, all.entries.get(1).x);
            assertEquals(9, all.entries.get(2).x);
            // nothing is loaded in a unit test, so no endpoint holds a runtime link
            assertTrue(all.entries.stream().noneMatch(e -> e.online));

            LabelNetworkRegistry.EndpointList limited = registry.listEndpoints(null, "locate", OWNER, 2);
            assertEquals(3, limited.total);
            assertEquals(2, limited.entries.size());

            assertEquals(0, registry.listEndpoints(null, "locate", UUID.randomUUID(), 10).total);
            assertEquals(0, registry.listEndpoints(null, "missing", OWNER, 10).total);
        } finally {
            ModConfig.wirelessCrossDimEnable = oldCrossDimensional;
        }
    }

    @Test
    public void keepsTransceiverNamesThroughSaveAndLoad() {
        boolean oldCrossDimensional = ModConfig.wirelessCrossDimEnable;
        ModConfig.wirelessCrossDimEnable = true;
        try {
            NBTTagCompound named = endpoint(0, 9, 64, 1);
            named.setString("name", "  矿场入口 ");
            NBTTagCompound root = rootTag();
            NBTTagList bands = new NBTTagList();
            bands.appendTag(band("named", 1_000_040L, endpoints(named, endpoint(0, 2, 65, 8))));
            root.setTag("networks", bands);
            LabelNetworkRegistry registry = new LabelNetworkRegistry();
            registry.readFromNBT(root);

            NBTTagCompound saved = new NBTTagCompound();
            registry.writeToNBT(saved);
            LabelNetworkRegistry reloaded = new LabelNetworkRegistry();
            reloaded.readFromNBT(saved);

            LabelNetworkRegistry.EndpointList list = reloaded.listEndpoints(null, "named", OWNER, 10);
            assertEquals(2, list.total);
            assertEquals("", list.entries.get(0).name);
            assertEquals("矿场入口", list.entries.get(1).name);
        } finally {
            ModConfig.wirelessCrossDimEnable = oldCrossDimensional;
        }
    }

    @Test
    public void prunesOnlyThePositionsReportedMissing() {
        boolean oldCrossDimensional = ModConfig.wirelessCrossDimEnable;
        ModConfig.wirelessCrossDimEnable = true;
        try {
            NBTTagCompound ghost = endpoint(180, 5, 64, 4);
            ghost.setString("name", "ghost");
            NBTTagCompound root = rootTag();
            NBTTagList bands = new NBTTagList();
            bands.appendTag(band("one", 1_000_050L, endpoints(ghost, endpoint(180, 26, 81, 58))));
            bands.appendTag(band("two", 1_000_051L, endpoints(endpoint(0, -3, 65, 21))));
            root.setTag("networks", bands);
            LabelNetworkRegistry registry = new LabelNetworkRegistry();
            registry.readFromNBT(root);

            int pruned = registry.pruneEndpoints(ref -> ref.x == 5 || ref.x == -3);

            assertEquals(2, pruned);
            assertTrue(registry.isDirty());
            LabelNetworkRegistry.EndpointList one = registry.listEndpoints(null, "one", OWNER, 10);
            assertEquals(1, one.total);
            assertEquals(26, one.entries.get(0).x);
            assertEquals(0, registry.listEndpoints(null, "two", OWNER, 10).total);
            // the frequency itself stays, only the stale position goes
            assertNotNull(registry.getNetwork(null, "two", OWNER));
            assertNull(registry.getNetworkForEndpoint(180, 5, 64, 4, OWNER));
            assertNotNull(registry.getNetworkForEndpoint(180, 26, 81, 58, OWNER));
            assertEquals(0, registry.pruneEndpoints(ref -> false));

            NBTTagCompound saved = new NBTTagCompound();
            registry.writeToNBT(saved);
            NBTTagList savedBands = saved.getTagList("networks", 10);
            int endpointsLeft = 0;
            for (int i = 0; i < savedBands.tagCount(); i++) {
                NBTTagList eps = savedBands.getCompoundTagAt(i).getTagList("endpoints", 10);
                endpointsLeft += eps.tagCount();
                for (int j = 0; j < eps.tagCount(); j++) {
                    assertFalse(eps.getCompoundTagAt(j).hasKey("name"));
                }
            }
            assertEquals(1, endpointsLeft);
        } finally {
            ModConfig.wirelessCrossDimEnable = oldCrossDimensional;
        }
    }

    private static NBTTagCompound rootTag() {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("dataVersion", 3);
        root.setString("scopeMode", "global");
        root.setLong("nextChannel", 1_000_000L);
        return root;
    }

    private static NBTTagCompound band(String label, long channel, NBTTagList endpoints) {
        NBTTagCompound band = new NBTTagCompound();
        band.setString("label", label);
        band.setString("owner", OWNER.toString());
        band.setLong("channel", channel);
        band.setInteger("originDim", -1);
        band.setTag("endpoints", endpoints);
        return band;
    }

    private static NBTTagCompound endpoint(int dim, int x, int y, int z) {
        NBTTagCompound endpoint = new NBTTagCompound();
        endpoint.setInteger("dim", dim);
        endpoint.setInteger("x", x);
        endpoint.setInteger("y", y);
        endpoint.setInteger("z", z);
        return endpoint;
    }

    private static NBTTagList endpoints(NBTTagCompound... values) {
        NBTTagList endpoints = new NBTTagList();
        for (NBTTagCompound value : values) {
            endpoints.appendTag(value);
        }
        return endpoints;
    }

    private static NBTTagCompound findBand(NBTTagList bands, String label, Integer dim) {
        for (int i = 0; i < bands.tagCount(); i++) {
            NBTTagCompound band = bands.getCompoundTagAt(i);
            boolean matchesDim = dim == null ? !band.hasKey("dim", 99) : band.hasKey("dim", 99)
                && band.getInteger("dim") == dim;
            if (label.equals(band.getString("label")) && matchesDim) {
                return band;
            }
        }
        return null;
    }
}
