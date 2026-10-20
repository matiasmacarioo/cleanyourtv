package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InputKeysTest {

    private static final int[] NONE = new int[0];

    @Test
    public void inputButtonAlwaysOpensTheSwitcher() {
        assertTrue(InputKeys.opensSwitcher(InputKeys.TV_INPUT, false, false, NONE));
        assertTrue(InputKeys.opensSwitcher(InputKeys.TV_INPUT, true, false, NONE));
    }

    /** The default: the tuner button opens the switcher, as it always did. */
    @Test
    public void tunerButtonIsClaimedByDefault() {
        assertTrue(InputKeys.opensSwitcher(InputKeys.TV, true, false, NONE));
    }

    /** input_key_service_tv false: the firmware's TV app keeps the button. */
    @Test
    public void tunerButtonIsHandedBackWhenTurnedOff() {
        assertFalse(InputKeys.opensSwitcher(InputKeys.TV, false, false, NONE));
    }

    /** A button mapped in app_keys starts an app; it must not be swallowed by the switcher. */
    @Test
    public void appKeysBeatEveryInputRule() {
        assertFalse(InputKeys.opensSwitcher(InputKeys.TV, true, true, NONE));
        assertFalse(InputKeys.opensSwitcher(InputKeys.TV_INPUT, true, true, NONE));
        assertFalse(InputKeys.opensSwitcher(192, true, true, new int[]{192}));
    }

    @Test
    public void extraKeycodesOpenTheSwitcher() {
        assertTrue(InputKeys.opensSwitcher(192, true, false, new int[]{192}));
        assertFalse(InputKeys.opensSwitcher(192, true, false, NONE));
    }

    @Test
    public void unrelatedKeysPassThrough() {
        assertFalse(InputKeys.opensSwitcher(23, true, false, NONE));
        assertFalse(InputKeys.opensSwitcher(4, true, false, NONE));
    }
}
