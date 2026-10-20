package com.gathin.tvhome;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * The launcher's only network code: a blocking fetch of the Plex on-deck list.
 *
 * <p>Deliberately dependency-free — {@link HttpURLConnection} rather than OkHttp or Retrofit, because
 * this is one request for one row and the launcher ships no networking library.
 *
 * <p>Every method here blocks, so it must never be called on the main thread; {@link ContinueWatching}
 * drives it from a background executor.
 *
 * <p>The token is part of the URL (see {@link PlexUrls}) and Plex needs no other credential. Nothing
 * in this class logs a full URL: failures are logged through {@link PlexUrls#redact}.
 */
final class PlexClient {
    private static final String TAG = "TvHome.Plex";
    /** Generous but bounded: a sleeping Plex server should fail the row, not hang Home. */
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final int MAX_BODY_BYTES = 1 << 20;         // 1 MiB; the real list is ~20 KB

    private final String base;
    private final String token;

    PlexClient(String base, String token) {
        this.base = base;
        this.token = token;
    }

    /** True when a theme has supplied at least a server address. */
    boolean isConfigured() {
        return base != null && !base.isEmpty();
    }

    /**
     * The on-deck items, or an empty list when the server is unreachable, refuses the token, or
     * answers with something unparseable. Never throws: a Plex problem must not take Home down.
     */
    java.util.List<PlexItem> onDeck() {
        return onDeck(null);
    }

    /**
     * The on-deck items, handing the server's exact response body to {@code rawOut} when it is not
     * null. The row caches that body verbatim, so the offline copy can never disagree with a live one.
     */
    java.util.List<PlexItem> onDeck(java.util.concurrent.atomic.AtomicReference<String> rawOut) {
        if (!isConfigured()) return java.util.Collections.emptyList();
        String url = PlexUrls.onDeck(base, token);
        try {
            String body = get(url);
            if (body == null) return java.util.Collections.emptyList();
            if (rawOut != null) rawOut.set(body);
            return PlexItem.parse(body);
        } catch (Exception e) {
            Log.w(TAG, "on-deck fetch failed for " + PlexUrls.redact(url) + ": " + e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Downloads a poster. Kept separate from {@link #onDeck} because images are large and the row
     * fetches them one at a time; returns null on any failure so a missing poster is just a gap.
     *
     * <p>Failures are logged at debug, not warning: when the server is asleep every poster in the row
     * fails, and eight warnings per cold start would look like a fault rather than the expected
     * offline case the cache is there to cover.
     */
    byte[] posterBytes(String posterUrl) {
        if (posterUrl == null || posterUrl.isEmpty()) return null;
        try {
            return getBytes(posterUrl);
        } catch (Exception e) {
            Log.d(TAG, "poster fetch failed for " + PlexUrls.redact(posterUrl) + ": " + e);
            return null;
        }
    }

    /** The body as UTF-8, or null on a non-200. */
    private String get(String url) throws Exception {
        byte[] bytes = getBytes(url);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] getBytes(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setRequestMethod("GET");
            c.setConnectTimeout(CONNECT_TIMEOUT_MS);
            c.setReadTimeout(READ_TIMEOUT_MS);
            // Plex decides JSON or XML from Accept; everything here parses JSON.
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("X-Plex-Client-Identifier", "cleanyourtv");
            c.setRequestProperty("X-Plex-Product", "CleanYourTV");
            c.setRequestProperty("X-Plex-Version", "1.0");
            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Plex answered " + code + " for " + PlexUrls.redact(url));
                return null;
            }
            try (InputStream in = c.getInputStream()) {
                return readAll(in);
            }
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BODY_BYTES) throw new IllegalStateException("Plex body too large");
        }
        return out.toByteArray();
    }
}
