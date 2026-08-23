package cn.gtnh.ae2wtx.compat;

/** Pure formatting helpers shared by Waila display code and regression tests. */
public final class ChannelDisplayFormatter {

    private static final int PRACTICALLY_UNLIMITED = 1_000_000;

    private ChannelDisplayFormatter() {}

    /** Local branch demand: finite capacity is shown as used/max with overload coloring. */
    public static String formatLocal(int used, int max) {
        if (max <= 0 || max >= PRACTICALLY_UNLIMITED) {
            return Integer.toString(used);
        }
        return colorizeUsed(used, max) + "/" + max;
    }

    /** Whole-band demand: unlimited capacity retains the infinity denominator. */
    public static String formatBand(int used, int max) {
        if (max <= 0 || max >= PRACTICALLY_UNLIMITED) {
            return used + "/\u221E";
        }
        return colorPrefix(used, max) + used + "/" + max;
    }

    /** Colored first argument for the existing localized used/max template. */
    public static String colorizeUsed(int used, int max) {
        return colorPrefix(used, max) + used;
    }

    private static String colorPrefix(int used, int max) {
        if (used > max) {
            return "\u00A7c";
        }
        if (used == max) {
            return "\u00A7e";
        }
        return "";
    }
}
