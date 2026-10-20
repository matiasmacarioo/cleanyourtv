package com.gathin.tvhome;

import java.util.Arrays;
import java.util.List;

/**
 * Where the Bluetooth pill goes. There is no single action every TV answers:
 *
 * <ul>
 *   <li>{@code android.settings.BLUETOOTH_SETTINGS} is the plain Android one; Android TV firmware
 *       usually does not declare it (the TCL G03 answers it with nothing at all),</li>
 *   <li>{@code com.google.android.intent.action.CONNECT_INPUT} is Android TV's "Pair accessory"
 *       screen ({@code com.android.tv.settings.accessories.AddAccessoryActivity}) and is what a
 *       headset owner wants: it lists the accessories and starts pairing,</li>
 *   <li>{@code com.android.tv.settings.ACCESSORIES_CONNECTED_DEVICES} is the TCL/VENDOR variant of
 *       the same screen, and {@code android.bluetooth.devicepicker.action.LAUNCH} the older
 *       device-picker one.</li>
 * </ul>
 *
 * The candidates are tried in order and the first one an installed activity resolves wins, so the
 * pill lands on the TV's own screen rather than on the launcher's "not available" toast.
 */
final class BluetoothIntents {
    /** Most specific first: on Android TV the pair-accessory screen, elsewhere the plain settings. */
    static final List<String> CANDIDATES = Arrays.asList(
            "com.google.android.intent.action.CONNECT_INPUT",
            "com.android.tv.settings.ACCESSORIES_CONNECTED_DEVICES",
            "android.settings.BLUETOOTH_SETTINGS",
            "android.bluetooth.devicepicker.action.LAUNCH");

    private BluetoothIntents() {}

    /** The first action {@code resolves} accepts, or the plain settings action when none does. */
    static String pick(java.util.function.Predicate<String> resolves) {
        for (String action : CANDIDATES) {
            if (resolves.test(action)) return action;
        }
        return "android.settings.BLUETOOTH_SETTINGS";
    }
}
