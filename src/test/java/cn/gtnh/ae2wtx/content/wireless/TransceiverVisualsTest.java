package cn.gtnh.ae2wtx.content.wireless;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TransceiverVisualsTest {

    @Test
    public void oneSparkPerStartedEighthOfTheCapacity() {
        assertEquals(0, TransceiverVisuals.litSparks(0, 32));
        assertEquals(0, TransceiverVisuals.litSparks(-3, 32));
        assertEquals(1, TransceiverVisuals.litSparks(1, 32));
        assertEquals(1, TransceiverVisuals.litSparks(4, 32));
        assertEquals(2, TransceiverVisuals.litSparks(5, 32));
        assertEquals(7, TransceiverVisuals.litSparks(27, 32));
        assertEquals(8, TransceiverVisuals.litSparks(32, 32));
        assertEquals(8, TransceiverVisuals.litSparks(33, 32));
        assertEquals(1, TransceiverVisuals.litSparks(8, 64));
        assertEquals(2, TransceiverVisuals.litSparks(9, 64));
        assertEquals(8, TransceiverVisuals.litSparks(Integer.MAX_VALUE, 32));
    }

    @Test
    public void withoutALimitEverySparkIsFourChannels() {
        assertEquals(1, TransceiverVisuals.litSparks(3, 0));
        assertEquals(2, TransceiverVisuals.litSparks(5, -1));
        assertEquals(8, TransceiverVisuals.litSparks(40, Integer.MAX_VALUE));
    }

    @Test
    public void loadTurnsOrangeAtThreeQuartersAndRedWhenFull() {
        assertEquals(TransceiverVisuals.Load.OK, TransceiverVisuals.load(0, 32));
        assertEquals(TransceiverVisuals.Load.OK, TransceiverVisuals.load(23, 32));
        assertEquals(TransceiverVisuals.Load.WARN, TransceiverVisuals.load(24, 32));
        assertEquals(TransceiverVisuals.Load.WARN, TransceiverVisuals.load(31, 32));
        assertEquals(TransceiverVisuals.Load.FULL, TransceiverVisuals.load(32, 32));
        assertEquals(TransceiverVisuals.Load.FULL, TransceiverVisuals.load(33, 32));
    }

    @Test
    public void unlimitedChannelsNeverWarn() {
        assertEquals(TransceiverVisuals.Load.OK, TransceiverVisuals.load(500, 0));
        assertEquals(TransceiverVisuals.Load.OK, TransceiverVisuals.load(500, -1));
    }

    @Test
    public void themeCyclesAndResolvesTheDefault() {
        assertEquals(TransceiverTheme.DARK, TransceiverTheme.DEFAULT.next());
        assertEquals(TransceiverTheme.LIGHT, TransceiverTheme.DARK.next());
        assertEquals(TransceiverTheme.DEFAULT, TransceiverTheme.LIGHT.next());
        assertFalse(TransceiverTheme.DEFAULT.isLight(false));
        assertTrue(TransceiverTheme.DEFAULT.isLight(true));
        assertFalse(TransceiverTheme.DARK.isLight(true));
        assertTrue(TransceiverTheme.LIGHT.isLight(false));
        assertEquals(TransceiverTheme.DEFAULT, TransceiverTheme.fromOrdinal(7));
        assertEquals(TransceiverTheme.DEFAULT, TransceiverTheme.fromOrdinal(-1));
    }
}
