package cn.gtnh.ae2wtx.content.wireless;

/** How synced channel figures map onto the sparks orbiting the black hole. */
public final class TransceiverVisuals {

    public static final int SPARKS = 8;
    public static final int CHANNELS_PER_SPARK = 4;

    /** Frequency load at which the sparks turn from purple to orange. */
    public static final double WARN_RATIO = 0.75D;

    public enum Load {
        OK,
        WARN,
        FULL
    }

    private TransceiverVisuals() {}

    /** One spark per started block of four channels this transceiver carries, at most eight. */
    public static int litSparks(int channels) {
        if (channels <= 0) {
            return 0;
        }
        return Math.min(SPARKS, (channels + CHANNELS_PER_SPARK - 1) / CHANNELS_PER_SPARK);
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
