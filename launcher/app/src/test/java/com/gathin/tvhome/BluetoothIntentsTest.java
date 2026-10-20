package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class BluetoothIntentsTest {

    /** The TCL G03 (Android 11): the plain settings action resolves to nothing, only the TV one does. */
    @Test
    public void androidTvLandsOnThePairAccessoryScreen() {
        Set<String> resolves = new HashSet<>(Arrays.asList("com.google.android.intent.action.CONNECT_INPUT"));
        assertEquals("com.google.android.intent.action.CONNECT_INPUT",
                BluetoothIntents.pick(resolves::contains));
    }

    /** A phone or a TV whose settings app declares the standard action: that one is used. */
    @Test
    public void standardActionIsUsedWhenTheTvDoesNotAnswerThePairScreen() {
        Set<String> resolves = new HashSet<>(Arrays.asList("android.settings.BLUETOOTH_SETTINGS"));
        assertEquals("android.settings.BLUETOOTH_SETTINGS", BluetoothIntents.pick(resolves::contains));
    }

    /** Both available: the pair-accessory screen wins, it is where an accessory gets connected. */
    @Test
    public void pairScreenOutranksPlainSettings() {
        Set<String> resolves = new HashSet<>(Arrays.asList("com.google.android.intent.action.CONNECT_INPUT",
                "android.settings.BLUETOOTH_SETTINGS"));
        assertEquals("com.google.android.intent.action.CONNECT_INPUT",
                BluetoothIntents.pick(resolves::contains));
    }

    @Test
    public void vendorAccessoriesScreenIsTriedToo() {
        Set<String> resolves = new HashSet<>(Arrays.asList(
                "com.android.tv.settings.ACCESSORIES_CONNECTED_DEVICES",
                "android.bluetooth.devicepicker.action.LAUNCH"));
        assertEquals("com.android.tv.settings.ACCESSORIES_CONNECTED_DEVICES",
                BluetoothIntents.pick(resolves::contains));
    }

    /** Nothing resolves: the plain action is returned so the click ends in the can't-open toast. */
    @Test
    public void nothingResolvingFallsBackToTheStandardAction() {
        assertEquals("android.settings.BLUETOOTH_SETTINGS", BluetoothIntents.pick(action -> false));
    }
}
