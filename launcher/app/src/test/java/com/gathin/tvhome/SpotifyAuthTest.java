package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpotifyAuthTest {

    @Test
    public void configuredWhenBothHalvesArePresent() {
        assertTrue(new SpotifyAuth("cid", "rt").isConfigured());
    }

    /** Half a credential is not a credential: the row must stay off rather than make a doomed call. */
    @Test
    public void notConfiguredWhenEitherHalfIsMissing() {
        assertFalse(new SpotifyAuth("", "rt").isConfigured());
        assertFalse(new SpotifyAuth("cid", "").isConfigured());
        assertFalse(new SpotifyAuth(null, null).isConfigured());
        assertFalse(new SpotifyAuth("   ", "  ").isConfigured());
    }

    @Test
    public void trimsSurroundingWhitespace() {
        SpotifyAuth a = new SpotifyAuth("  cid  ", "  rt  ");
        assertEquals("cid", a.clientId);
        assertEquals("rt", a.refreshToken);
    }

    /** A rotated token replaces the old one but keeps the client id. */
    @Test
    public void rotatedTokenReplacesTheOldOne() {
        SpotifyAuth a = new SpotifyAuth("cid", "old");
        SpotifyAuth b = a.withRefreshToken("new");
        assertEquals("cid", b.clientId);
        assertEquals("new", b.refreshToken);
        assertNotSame(a, b);
    }

    /** Spotify usually omits the token on refresh; the existing one must be kept as-is. */
    @Test
    public void anAbsentRotationKeepsTheSameObject() {
        SpotifyAuth a = new SpotifyAuth("cid", "same");
        assertSame(a, a.withRefreshToken(null));
        assertSame(a, a.withRefreshToken(""));
        assertSame(a, a.withRefreshToken("same"));
    }

    /** The 6-month expiry case: Spotify answers 400 invalid_grant and must not be retried. */
    @Test
    public void invalidGrantMeansTheLoginIsDead() {
        assertTrue(SpotifyAuth.expired(400, "{\"error\":\"invalid_grant\"}"));
        assertTrue(SpotifyAuth.expired(400, "{\"error\":\"invalid_grant\",\"error_description\":\"x\"}"));
    }

    /** Any other failure is a bad moment, not a dead login, and must keep being retried. */
    @Test
    public void otherFailuresAreNotTreatedAsExpiry() {
        assertFalse(SpotifyAuth.expired(400, "{\"error\":\"invalid_client\"}"));
        assertFalse(SpotifyAuth.expired(401, "{\"error\":\"invalid_grant\"}"));
        assertFalse(SpotifyAuth.expired(429, "{\"error\":\"rate_limited\"}"));
        assertFalse(SpotifyAuth.expired(500, ""));
        assertFalse(SpotifyAuth.expired(400, "not json"));
        assertFalse(SpotifyAuth.expired(400, null));
    }

    /** The whole point of the type: it must never print the secret. */
    @Test
    public void toStringNeverPrintsTheToken() {
        String s = new SpotifyAuth("my-client-id", "my-refresh-token").toString();
        assertFalse(s.contains("my-refresh-token"));
        assertFalse(s.contains("my-client-id"));
        assertTrue(s.contains("set"));
    }

    @Test
    public void toStringSaysWhenSomethingIsMissing() {
        String s = new SpotifyAuth("", "").toString();
        assertFalse(s.contains("set"));
    }
}
