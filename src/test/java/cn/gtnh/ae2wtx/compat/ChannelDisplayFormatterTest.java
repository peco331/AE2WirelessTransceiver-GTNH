package cn.gtnh.ae2wtx.compat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ChannelDisplayFormatterTest {

    @Test
    public void rendersThirtyThreeOfThirtyTwoInRed() {
        assertEquals("\u00A7c33/32", ChannelDisplayFormatter.formatLocal(33, 32));
        assertEquals("\u00A7c33/32", ChannelDisplayFormatter.formatBand(33, 32));
    }

    @Test
    public void retainsNormalFullAndUnlimitedStates() {
        assertEquals("31/32", ChannelDisplayFormatter.formatLocal(31, 32));
        assertEquals("\u00A7e32/32", ChannelDisplayFormatter.formatLocal(32, 32));
        assertEquals("33", ChannelDisplayFormatter.formatLocal(33, Integer.MAX_VALUE));
        assertEquals("33/\u221E", ChannelDisplayFormatter.formatBand(33, Integer.MAX_VALUE));
    }
}
