package cn.gtnh.ae2wtx.compat;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class NetworkVisualiserAnchorTest {

    @Test
    public void prefersTheControllerSideEndpointOverTheLowestCoordinate() {
        // Sorted by coordinate: a remote multiblock endpoint comes first, the
        // controller-side endpoint of the main network comes later.
        List<Boolean> controllerSide = Arrays.asList(false, false, true, false);

        assertEquals(2, WirelessVisualLinks.selectAnchorIndex(controllerSide, Boolean::booleanValue));
    }

    @Test
    public void keepsCoordinateOrderAmongSeveralControllerSideEndpoints() {
        List<Boolean> controllerSide = Arrays.asList(false, true, true);

        assertEquals(1, WirelessVisualLinks.selectAnchorIndex(controllerSide, Boolean::booleanValue));
    }

    @Test
    public void fallsBackToTheFirstEndpointWithoutAController() {
        List<Boolean> controllerSide = Collections.nCopies(3, false);

        assertEquals(0, WirelessVisualLinks.selectAnchorIndex(controllerSide, Boolean::booleanValue));
    }
}
