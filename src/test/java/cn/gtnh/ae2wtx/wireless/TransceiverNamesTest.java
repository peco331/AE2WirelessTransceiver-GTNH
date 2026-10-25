package cn.gtnh.ae2wtx.wireless;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TransceiverNamesTest {

    @Test
    public void trimsAndKeepsOrdinaryText() {
        assertEquals("主网 控制器-1", TransceiverNames.clean("  主网 控制器-1  "));
        assertEquals("Ore line", TransceiverNames.clean("Ore line"));
    }

    @Test
    public void blankOrMissingMeansNoName() {
        assertNull(TransceiverNames.clean(null));
        assertNull(TransceiverNames.clean(""));
        assertNull(TransceiverNames.clean("   "));
        assertNull(TransceiverNames.clean("§c"));
    }

    @Test
    public void dropsFormattingControlAndAstralCharacters() {
        assertEquals("red", TransceiverNames.clean("§cred"));
        assertEquals("ab", TransceiverNames.clean("a\nb\u0000"));
        assertEquals("ok", TransceiverNames.clean("o😀k"));
        assertEquals("xy", TransceiverNames.clean("x\uD800y"));
    }

    @Test
    public void cutsToTheMaximumLength() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            longName.append('频');
        }
        String clean = TransceiverNames.clean(longName.toString());
        assertEquals(TransceiverNames.MAX_LENGTH, clean.length());
        assertEquals(true, clean.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
            <= TransceiverNames.MAX_BYTES);
    }
}
