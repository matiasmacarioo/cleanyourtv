package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpotifyUrlsTest {

    @Test
    public void recentlyPlayedUsesTheDocumentedEndpoint() {
        assertTrue(SpotifyUrls.recentlyPlayed(5).startsWith(
                "https://api.spotify.com/v1/me/player/recently-played?limit="));
    }

    @Test
    public void recentlyPlayedCarriesTheLimit() {
        assertEquals("https://api.spotify.com/v1/me/player/recently-played?limit=8",
                SpotifyUrls.recentlyPlayed(8));
    }

    /** Spotify rejects anything above 50, so the URL must never ask for more. */
    @Test
    public void limitIsClampedToSpotifysMaximum() {
        assertTrue(SpotifyUrls.recentlyPlayed(500).endsWith("limit=50"));
    }

    /** A zero or negative limit would be a 400, so it is raised to the smallest valid request. */
    @Test
    public void limitIsRaisedWhenNonsensical() {
        assertTrue(SpotifyUrls.recentlyPlayed(0).endsWith("limit=1"));
        assertTrue(SpotifyUrls.recentlyPlayed(-3).endsWith("limit=1"));
    }

    @Test
    public void tokenEndpointIsTheAccountsHost() {
        assertEquals("https://accounts.spotify.com/api/token", SpotifyUrls.TOKEN_ENDPOINT);
    }

    @Test
    public void refreshBodyIsAUrlEncodedGrant() {
        String b = SpotifyUrls.refreshBody("cid", "rt");
        assertEquals("grant_type=refresh_token&client_id=cid&refresh_token=rt", b);
    }

    /** The refresh token is opaque and may contain characters that must be escaped. */
    @Test
    public void refreshBodyEncodesAwkwardTokens() {
        String b = SpotifyUrls.refreshBody("cid", "a+b/c=d");
        assertTrue(b.contains("refresh_token=a%2Bb%2Fc%3Dd"));
        assertFalse(b.contains("refresh_token=a+b/c=d"));
    }

    @Test
    public void bearerIsPrefixedWithBearer() {
        assertEquals("Bearer abc123", SpotifyUrls.bearer("abc123"));
    }

    /** The refresh token in a form body is the secret most likely to reach a log; it must be masked. */
    @Test
    public void redactHidesTheRefreshToken() {
        String redacted = SpotifyUrls.redact("grant_type=refresh_token&client_id=x&refresh_token=SECRET123");
        assertFalse(redacted.contains("SECRET123"));
        assertTrue(redacted.contains("refresh_token=REDACTED"));
        assertTrue("the client id is not a secret and should survive for diagnosis",
                redacted.contains("client_id=x"));
    }

    /** A bearer header carries the access token and must be masked too. */
    @Test
    public void redactHidesTheBearerToken() {
        String redacted = SpotifyUrls.redact("Authorization: Bearer BQ-abcdef123.xyz");
        assertFalse(redacted.contains("BQ-abcdef123.xyz"));
        assertTrue(redacted.contains("Bearer REDACTED"));
    }

    @Test
    public void redactMatchesBearerCaseInsensitively() {
        assertFalse(SpotifyUrls.redact("bearer SECRET").contains("SECRET"));
    }

    /** Redaction must never itself throw on the odd inputs that reach a failure log. */
    @Test
    public void redactHandlesEmptyAndNull() {
        assertEquals("", SpotifyUrls.redact(null));
        assertEquals("", SpotifyUrls.redact(""));
        assertEquals("no secrets here", SpotifyUrls.redact("no secrets here"));
    }
}
