package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlexUrlsTest {

    /** A theme may write the address the way a person would. */
    @Test
    public void bareHostGetsTheSchemeAndPlexsPort() {
        assertEquals("http://192.168.120.230:32400", PlexUrls.normalise("192.168.120.230"));
    }

    /** A fully written address is left alone. */
    @Test
    public void explicitSchemeAndPortAreKept() {
        assertEquals("http://192.168.120.230:32400", PlexUrls.normalise("http://192.168.120.230:32400"));
    }

    @Test
    public void trailingSlashIsDropped() {
        assertEquals("http://192.168.120.230:32400", PlexUrls.normalise("192.168.120.230/"));
        assertEquals("http://plex.local:32400", PlexUrls.normalise("http://plex.local:32400/"));
    }

    /** A hostname with no port, https, and a non-default port all survive. */
    @Test
    public void hostnameAndHttpsAndCustomPortsAreHandled() {
        assertEquals("http://plex.local:32400", PlexUrls.normalise("plex.local"));
        assertEquals("https://plex.example.com", PlexUrls.normalise("https://plex.example.com"));
        assertEquals("http://192.168.120.230:32401", PlexUrls.normalise("192.168.120.230:32401"));
    }

    /** An empty value means "no Plex configured", and stays empty. */
    @Test
    public void emptyStaysEmpty() {
        assertEquals("", PlexUrls.normalise(null));
        assertEquals("", PlexUrls.normalise(""));
        assertEquals("", PlexUrls.normalise("   "));
    }

    @Test
    public void onDeckCarriesTheToken() {
        assertEquals("http://10.0.0.1:32400/library/onDeck?X-Plex-Token=abc",
                PlexUrls.onDeck("http://10.0.0.1:32400", "abc"));
    }

    /** With no token the URL is still built: the call will 401, which is a visible, honest failure. */
    @Test
    public void onDeckWithoutATokenStaysTokenless() {
        assertEquals("http://10.0.0.1:32400/library/onDeck", PlexUrls.onDeck("http://10.0.0.1:32400", ""));
        assertEquals("http://10.0.0.1:32400/library/onDeck", PlexUrls.onDeck("http://10.0.0.1:32400", null));
    }

    /** The poster asks Plex to resize, so the TV does not pull the multi-megabyte original. */
    @Test
    public void posterAsksForAResizedImage() {
        String u = PlexUrls.poster("http://10.0.0.1:32400", "abc",
                "/library/metadata/228464/thumb/1792444653", 320, 180);
        assertTrue(u.startsWith("http://10.0.0.1:32400/photo/:/transcode?"));
        assertTrue(u.contains("width=320"));
        assertTrue(u.contains("height=180"));
        assertTrue(u.contains("X-Plex-Token=abc"));
        // The artwork path travels as a query parameter, encoded.
        assertTrue(u.contains("url=%2Flibrary%2Fmetadata%2F228464%2Fthumb%2F1792444653"));
    }

    @Test
    public void posterWithNoArtworkIsEmpty() {
        assertEquals("", PlexUrls.poster("http://10.0.0.1:32400", "abc", null, 320, 180));
        assertEquals("", PlexUrls.poster("http://10.0.0.1:32400", "abc", "", 320, 180));
    }

    /** A token already carrying a query gets "&", not a second "?". */
    @Test
    public void tokenJoinsWithAmpersandWhenAQueryExists() {
        assertEquals("http://h/photo?width=1&X-Plex-Token=t", PlexUrls.withToken("http://h/photo?width=1", "t"));
        assertEquals("http://h/x?X-Plex-Token=t", PlexUrls.withToken("http://h/x", "t"));
    }

    /** The whole point: a URL that ever leaves the app must not carry the token. */
    @Test
    public void redactHidesTheToken() {
        assertEquals("http://h/library/onDeck?X-Plex-Token=REDACTED",
                PlexUrls.redact("http://h/library/onDeck?X-Plex-Token=super-secret-value"));
        assertEquals("http://h/p?width=1&X-Plex-Token=REDACTED",
                PlexUrls.redact("http://h/p?width=1&X-Plex-Token=super-secret-value"));
    }

    /** Redaction must not eat a following parameter. */
    @Test
    public void redactKeepsLaterParameters() {
        assertEquals("http://h/p?X-Plex-Token=REDACTED&width=5",
                PlexUrls.redact("http://h/p?X-Plex-Token=abc123&width=5"));
    }

    @Test
    public void redactIsSafeOnUrlsWithoutAToken() {
        assertEquals("http://h/library/onDeck", PlexUrls.redact("http://h/library/onDeck"));
        assertEquals("", PlexUrls.redact(null));
    }

    /**
     * A redacted URL really does not contain the secret. The secret here is a made-up string, not a
     * real token: a test fixture is exactly the kind of place a live credential gets committed by
     * accident, so none is ever pasted into this file.
     */
    @Test
    public void redactedUrlNeverContainsTheSecret() {
        String secret = "not-a-real-token-0123456789";
        String url = PlexUrls.onDeck("http://10.0.0.1:32400", secret);
        String safe = PlexUrls.redact(url);
        assertFalse(safe.contains(secret));
        assertTrue(safe.contains("REDACTED"));
    }

    /** The identity endpoint, which is where a Plezy link's machineIdentifier comes from. */
    @Test
    public void identityCarriesTheToken() {
        assertEquals("http://10.0.0.1:32400/identity?X-Plex-Token=abc",
                PlexUrls.identity("http://10.0.0.1:32400", "abc"));
    }

    /**
     * The exact shape verified against Plezy 2.23.0 on the TV, and the one its source parses: a
     * {@code play} host with a {@code content_id} of {@code plezy_<server>_<ratingKey>}. The
     * parameter is percent-encoded, so the underscores survive and nothing in the value can end it
     * early.
     */
    @Test
    public void buildsThePlayLinkPlezyParses() {
        String machine = "c33593fef84063e28ac29f752383a01c8310bb4c";
        assertEquals("plezy://play?content_id=plezy_" + machine + "_217769",
                PlexUrls.plezyDeepLink(machine, "217769"));
    }

    /**
     * No server identity means no link. A theme whose server was asleep, or whose {@code /identity}
     * failed, must open the app instead of sending {@code plezy__217769}, which Plezy would read as
     * a server named "".
     */
    @Test
    public void noLinkWithoutAMachineIdentifier() {
        assertEquals("", PlexUrls.plezyDeepLink("", "217769"));
        assertEquals("", PlexUrls.plezyDeepLink(null, "217769"));
    }

    /** Likewise no item: a card with no ratingKey has nothing to open. */
    @Test
    public void noLinkWithoutARatingKey() {
        assertEquals("", PlexUrls.plezyDeepLink("machine", ""));
        assertEquals("", PlexUrls.plezyDeepLink("machine", null));
    }

    /** The link is handed to another app, so it must never carry the token. */
    @Test
    public void theLinkCarriesNoToken() {
        String link = PlexUrls.plezyDeepLink("machine", "217769");
        assertTrue(link.startsWith("plezy://play?content_id="));
        assertFalse("no token in a link handed to another app", link.contains("X-Plex-Token"));
    }
}
