package cn.gtnh.ae2wtx.content.wireless;

/**
 * Per-block colour scheme of the transceiver. DEFAULT follows the client's
 * config (client.defaultTheme), so a block placed without a choice changes
 * with that setting.
 */
public enum TransceiverTheme {

    DEFAULT,
    DARK,
    LIGHT;

    private static final TransceiverTheme[] VALUES = values();

    public static TransceiverTheme fromOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : DEFAULT;
    }

    /** GUI cycle: default, dark, light, default. */
    public TransceiverTheme next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public boolean isLight(boolean defaultIsLight) {
        return this == LIGHT || (this == DEFAULT && defaultIsLight);
    }
}
