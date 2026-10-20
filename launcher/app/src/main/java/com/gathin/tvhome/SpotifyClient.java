package com.gathin.tvhome;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * The launcher's Spotify calls: exchange the refresh token for an access token, then read the
 * recently-played list.
 *
 * <p>Two requests, both plain {@link HttpURLConnection}, matching the Plex client so the APK still
 * ships no networking library.
 *
 * <p>The access token lives for an hour, so it is held in memory and only re-fetched when it is
 * close to expiring. The refresh token can be rotated by Spotify on any refresh, and when that
 * happens {@link #rotatedRefreshToken()} reports the replacement so the caller can persist it —
 * dropping one would silently break the row until the next manual login.
 *
 * <p>Every method blocks and must be called off the main thread. Nothing here logs a token: failures
 * go through {@link SpotifyUrls#redact}.
 */
final class SpotifyClient {
    private static final String TAG = "TvHome.Spotify";
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final int MAX_BODY_BYTES = 1 << 20;
    /** Refresh a little before the real expiry, so a request cannot start on a dying token. */
    private static final long EXPIRY_SLACK_MS = 60_000L;

    private final SpotifyAuth auth;
    private String accessToken = "";
    private long accessTokenExpiresAt;
    /** The newest refresh token seen, which may differ from the one the row started with. */
    private volatile String rotatedRefresh;
    /** Set when Spotify says the refresh token is dead, so the row can stop retrying and say so. */
    private volatile boolean needsLogin;

    SpotifyClient(SpotifyAuth auth) {
        this.auth = auth;
    }

    /** True when a client id and refresh token were both supplied. */
    boolean isConfigured() {
        return auth.isConfigured();
    }

    /** The refresh token Spotify last issued, or null when it has not rotated. */
    String rotatedRefreshToken() {
        return rotatedRefresh;
    }

    /** True once Spotify has told us the login has to be redone. */
    boolean needsLogin() {
        return needsLogin;
    }

    /**
     * The recently-played list, or an empty list when anything goes wrong. Never throws: a Spotify
     * outage, an expired login and a bad network all end as "no row" rather than a broken Home.
     */
    java.util.List<SpotifyItem> recentlyPlayed(int limit) {
        if (!isConfigured()) return java.util.Collections.emptyList();
        if (!ensureAccessToken()) return java.util.Collections.emptyList();
        try {
            HttpURLConnection c = open(SpotifyUrls.recentlyPlayed(limit));
            c.setRequestProperty("Authorization", SpotifyUrls.bearer(accessToken));
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code == 401) {
                // The token died early. Drop it so the next call refreshes rather than reusing it.
                accessToken = "";
                accessTokenExpiresAt = 0;
                Log.w(TAG, "Spotify refused the access token (401); it will be refreshed next time");
                c.disconnect();
                return java.util.Collections.emptyList();
            }
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Spotify answered " + code + " for recently-played");
                c.disconnect();
                return java.util.Collections.emptyList();
            }
            try (InputStream in = c.getInputStream()) {
                String body = new String(readAll(in), StandardCharsets.UTF_8);
                return SpotifyItem.parse(body);
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            Log.w(TAG, "recently-played failed: " + e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Fetches an arbitrary URL's bytes, used for artwork. No access token is attached: Spotify serves
     * {@code i.scdn.co} images publicly, and the URLs come from the API rather than from user input.
     * Null on any failure, so the row shows its placeholder rather than crashing.
     */
    byte[] fetchBytes(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            HttpURLConnection c = open(url);
            c.setRequestProperty("Accept", "image/*");
            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                c.disconnect();
                return null;
            }
            try (InputStream in = c.getInputStream()) {
                return readAll(in);
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            Log.w(TAG, "artwork fetch failed: " + e);
            return null;
        }
    }

    /**
     * Makes sure a usable access token is held, refreshing when there is none or it is nearly expired.
     * Returns whether one is available.
     */
    private boolean ensureAccessToken() {
        long now = System.currentTimeMillis();
        if (!accessToken.isEmpty() && now < accessTokenExpiresAt - EXPIRY_SLACK_MS) return true;
        return refreshAccessToken();
    }

    /**
     * Exchanges the refresh token for an access token. On success also records a rotated refresh token
     * if Spotify sent one. A {@code invalid_grant} marks the client as needing a new login, which is
     * the 6-month expiry case and is not retried.
     */
    private boolean refreshAccessToken() {
        try {
            HttpURLConnection c = open(SpotifyUrls.TOKEN_ENDPOINT);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            c.setRequestProperty("Accept", "application/json");
            byte[] body = SpotifyUrls.refreshBody(auth.clientId, auth.refreshToken)
                    .getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = c.getOutputStream()) {
                out.write(body);
            }
            int code = c.getResponseCode();
            String text = readBody(c, code == HttpURLConnection.HTTP_OK);
            c.disconnect();
            if (code != HttpURLConnection.HTTP_OK) {
                if (SpotifyAuth.expired(code, text)) {
                    needsLogin = true;
                    Log.w(TAG, "Spotify refresh token is no longer valid; a new login is needed");
                } else {
                    Log.w(TAG, "Spotify token refresh failed with " + code
                            + ": " + SpotifyUrls.redact(text));
                }
                return false;
            }
            return applyTokenResponse(text);
        } catch (Exception e) {
            Log.w(TAG, "Spotify token refresh failed: " + e);
            return false;
        }
    }

    /** Stores the access token and expiry, and records a rotated refresh token when present. */
    private boolean applyTokenResponse(String json) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            String token = o.optString("access_token", "");
            if (token.isEmpty()) {
                Log.w(TAG, "Spotify returned no access token");
                return false;
            }
            accessToken = token;
            long expiresIn = o.optLong("expires_in", 3600L);
            accessTokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000L;
            // Spotify may or may not rotate the refresh token; when it does, it must be kept.
            String rotated = o.optString("refresh_token", "");
            if (!rotated.isEmpty() && !rotated.equals(auth.refreshToken)) rotatedRefresh = rotated;
            needsLogin = false;
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Spotify token reply was unparseable");
            return false;
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        c.setRequestProperty("X-Client", "CleanYourTV");
        return c;
    }

    /** Reads the reply text from whichever stream the status code implies. */
    private static String readBody(HttpURLConnection c, boolean ok) {
        try (InputStream in = ok ? c.getInputStream() : c.getErrorStream()) {
            return in == null ? "" : new String(readAll(in), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BODY_BYTES) throw new IllegalStateException("Spotify body too large");
        }
        return out.toByteArray();
    }
}
