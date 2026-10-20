package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class BluetoothLabelTest {

    @Test
    public void noAdapterHidesThePill() {
        assertNull(BluetoothLabel.of(false, false, null, "Bluetooth"));
        assertNull(BluetoothLabel.of(false, true, "AirPods", "Bluetooth"));
    }

    @Test
    public void connectedDeviceNameWins() {
        assertEquals("AirPods", BluetoothLabel.of(true, true, "AirPods", "Bluetooth"));
    }

    @Test
    public void adapterOffKeepsTheGenericLabel() {
        assertEquals("Bluetooth", BluetoothLabel.of(true, false, null, "Bluetooth"));
        // A stale name must not outlive an adapter that was switched off.
        assertEquals("Bluetooth", BluetoothLabel.of(true, false, "AirPods", "Bluetooth"));
    }

    @Test
    public void nothingConnectedOrUnreadableStaysGeneric() {
        assertEquals("Bluetooth", BluetoothLabel.of(true, true, null, "Bluetooth"));
        assertEquals("Bluetooth", BluetoothLabel.of(true, true, "", "Bluetooth"));
        assertEquals("Bluetooth", BluetoothLabel.of(true, true, "   ", "Bluetooth"));
    }
}
