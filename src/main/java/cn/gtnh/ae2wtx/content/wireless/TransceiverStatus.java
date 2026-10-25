package cn.gtnh.ae2wtx.content.wireless;

import java.util.Locale;

import appeng.api.networking.pathing.ControllerState;

/**
 * Why a transceiver is (not) working, shown in Waila and the GUI. Ordered by
 * diagnostic priority: the first problem that applies is the one reported.
 * Colours are chosen for dark backgrounds (Waila tooltip, GUI status screen).
 */
public enum TransceiverStatus {

    OK(0x55D055),
    NO_BAND(0xA0A0A0),
    LINK_DENIED(0xFF6060),
    LINKING(0xE0A030),
    CONTROLLER_CONFLICT(0xFF6060),
    BOOTING(0xE0A030),
    NO_POWER(0xFF6060);

    private final int color;

    TransceiverStatus(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    public String langKey() {
        return "ae2wtx.status." + name().toLowerCase(Locale.ROOT);
    }

    public static TransceiverStatus compute(boolean hasBand, boolean linked, boolean linkDenied,
        ControllerState controllerState, boolean booting, boolean powered) {
        if (!hasBand) {
            return NO_BAND;
        }
        if (!linked) {
            return linkDenied ? LINK_DENIED : LINKING;
        }
        if (controllerState == ControllerState.CONTROLLER_CONFLICT) {
            return CONTROLLER_CONFLICT;
        }
        if (booting) {
            return BOOTING;
        }
        return powered ? OK : NO_POWER;
    }

    public static TransceiverStatus fromOrdinal(int ordinal) {
        TransceiverStatus[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NO_BAND;
    }
}
