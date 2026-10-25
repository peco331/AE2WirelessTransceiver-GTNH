package cn.gtnh.ae2wtx.content.wireless;

/**
 * How synced channel figures map onto the sparks orbiting the black hole.
 * The eight sparks stand for the frequency's capacity (four channels each at
 * the usual 32): the lit ones show what the whole frequency uses, and the
 * share this transceiver carries burns brighter.
 */
public final class TransceiverVisuals {

    public static final int SPARKS = 8;
    public static final int CHANNELS_PER_SPARK = 4;
    /** At or above this the channel limit is AE2's "infinite" mode. */
    public static final int UNLIMITED_CHANNELS = 1_000_000;

    /** Frequency load at which the sparks turn from purple to orange. */
    public static final double WARN_RATIO = 0.75D;

    public enum Load {
        OK,
        WARN,
        FULL
    }

    private TransceiverVisuals() {}

    /**
     * Sparks for a channel count: every started eighth of {@code maxChannels}
     * lights one. Without a channel limit every started block of four does.
     */
    public static int litSparks(int channels, int maxChannels) {
        if (channels <= 0) {
            return 0;
        }
        if (maxChannels <= 0 || maxChannels >= UNLIMITED_CHANNELS) {
            return Math.min(SPARKS, (channels + CHANNELS_PER_SPARK - 1) / CHANNELS_PER_SPARK);
        }
        long scaled = ((long) channels * SPARKS + maxChannels - 1) / maxChannels;
        return (int) Math.min(SPARKS, scaled);
    }

    /**
     * Load of the frequency: channels demanded on it against the channels one
     * transceiver can carry. A non-positive limit means unlimited.
     */
    public static Load load(int frequencyChannels, int maxChannels) {
        if (maxChannels <= 0 || frequencyChannels <= 0) {
            return Load.OK;
        }
        if (frequencyChannels >= maxChannels) {
            return Load.FULL;
        }
        return frequencyChannels >= maxChannels * WARN_RATIO ? Load.WARN : Load.OK;
    }
}
