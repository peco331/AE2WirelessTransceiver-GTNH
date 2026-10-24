package cn.gtnh.ae2wtx.content.wireless;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import appeng.api.networking.pathing.ControllerState;

public class TransceiverStatusTest {

    private static final ControllerState ONLINE = ControllerState.CONTROLLER_ONLINE;

    @Test
    public void reportsTheFirstProblemInPriorityOrder() {
        assertEquals(TransceiverStatus.NO_BAND,
            TransceiverStatus.compute(false, false, true, ControllerState.CONTROLLER_CONFLICT, true, false));
        assertEquals(TransceiverStatus.LINK_DENIED,
            TransceiverStatus.compute(true, false, true, ONLINE, false, true));
        assertEquals(TransceiverStatus.LINKING,
            TransceiverStatus.compute(true, false, false, ONLINE, false, true));
        assertEquals(TransceiverStatus.CONTROLLER_CONFLICT,
            TransceiverStatus.compute(true, true, false, ControllerState.CONTROLLER_CONFLICT, true, false));
        assertEquals(TransceiverStatus.BOOTING,
            TransceiverStatus.compute(true, true, false, ONLINE, true, false));
        assertEquals(TransceiverStatus.NO_POWER,
            TransceiverStatus.compute(true, true, false, ONLINE, false, false));
        assertEquals(TransceiverStatus.OK,
            TransceiverStatus.compute(true, true, false, ONLINE, false, true));
    }

    @Test
    public void controllerlessNetworkCanStillBeOk() {
        assertEquals(TransceiverStatus.OK,
            TransceiverStatus.compute(true, true, false, ControllerState.NO_CONTROLLER, false, true));
    }

    @Test
    public void unknownSyncedOrdinalFallsBackSafely() {
        assertEquals(TransceiverStatus.OK, TransceiverStatus.fromOrdinal(0));
        assertEquals(TransceiverStatus.NO_BAND, TransceiverStatus.fromOrdinal(99));
        assertEquals(TransceiverStatus.NO_BAND, TransceiverStatus.fromOrdinal(-1));
    }
}
