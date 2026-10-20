package com.gathin.tvhome;

/**
 * What the Bluetooth pill (config bluetooth_pill) reads: the connected audio device's name once one
 * is connected, the generic word while the adapter is off or nothing is connected, and null when the
 * device has no Bluetooth adapter at all, where the pill hides.
 */
final class BluetoothLabel {
    private BluetoothLabel() {}

    static String of(boolean adapter, boolean enabled, String connectedDevice, String generic) {
        if (!adapter) return null;
        return enabled && connectedDevice != null && !connectedDevice.trim().isEmpty()
                ? connectedDevice : generic;
    }
}
