package cn.gtnh.ae2wtx.wireless;

/** Player-given transceiver names: one cleaning rule for the GUI, the item and saved data. */
public final class TransceiverNames {

    /** Longest name in characters (code points). */
    public static final int MAX_LENGTH = 32;
    /** Upper bound of a name on the wire: MAX_LENGTH characters of at most three UTF-8 bytes. */
    public static final int MAX_BYTES = MAX_LENGTH * 3;

    private TransceiverNames() {}

    /**
     * Drop formatting codes (section sign plus code), control characters and
     * characters outside the BMP, trim, and cut to {@link #MAX_LENGTH}.
     * Returns null for no name.
     */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length();) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§') {
                // a formatting code: the section sign and the code letter after it
                if (i < raw.length()) {
                    i += Character.charCount(raw.codePointAt(i));
                }
                continue;
            }
            // the 1.7.10 font has no glyphs outside the BMP, so those go too
            if (cp > 0xFFFF || Character.isISOControl(cp) || Character.isSurrogate((char) cp)) {
                continue;
            }
            out.appendCodePoint(cp);
        }
        String name = out.toString().trim();
        if (name.codePointCount(0, name.length()) > MAX_LENGTH) {
            name = name.substring(0, name.offsetByCodePoints(0, MAX_LENGTH)).trim();
        }
        return name.isEmpty() ? null : name;
    }
}
