package com.gathin.tvhome;

import android.view.KeyEvent;

/**
 * Which keys {@link InputKeyService} opens the input switcher for. The rules sit here rather than in the
 * service so a test can pin them down with plain numbers:
 *
 * <ul>
 *   <li>a key mapped in {@code app_keys} is never an input key: that button is meant to start an app,</li>
 *   <li>{@link #TV_INPUT} (178), the remote's Input button, always is,</li>
 *   <li>{@link #TV} (170), the tuner/TV button, only while {@code input_key_service_tv} is on (the
 *       default): on a set whose firmware already opens Live TV for that button, claiming it here would
 *       take that away,</li>
 *   <li>plus whatever {@code input_key_keycodes} lists.</li>
 * </ul>
 */
final class InputKeys {
    /** {@link KeyEvent#KEYCODE_TV_INPUT}: the remote's Input button. */
    static final int TV_INPUT = 178;
    /** {@link KeyEvent#KEYCODE_TV}: the remote's tuner/TV button. */
    static final int TV = 170;

    private InputKeys() {}

    static boolean opensSwitcher(int keyCode, boolean tvKey, boolean mappedToApp, int[] extraKeycodes) {
        if (mappedToApp) return false;
        if (keyCode == TV_INPUT) return true;
        if (keyCode == TV) return tvKey;
        for (int code : extraKeycodes) if (keyCode == code) return true;
        return false;
    }
}
