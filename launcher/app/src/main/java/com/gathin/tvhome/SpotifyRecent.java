package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the Spotify row's data: fetches the recently-played list off the main thread, caches it to
 * disk, and hands items back on the main thread.
 *
 * <p>Deliberately the same shape as {@link ContinueWatching} for Plex, so the two rows behave
 * identically: loaded when the tile comes into view, throttled by {@link #refreshIfStale}, falling
 * back to the cache when Spotify cannot be reached, and one request in flight at a time.
 *
 * <p>Two Spotify-specific duties sit here rather than in the client:
 *
 * <ul>
 *   <li><b>Persisting a rotated refresh token.</b> Spotify can return a new one on any refresh; if it
 *       is not written back, the row dies at the next restart with a token Spotify has retired.</li>
 *   <li><b>Reporting an expired login.</b> The row cannot fix a 6-month-old token itself, so it
 *       surfaces that state instead of looking like an outage.</li>
 * </ul>
 */
final class SpotifyRecent {
    private static final String TAG = "TvHome.Spotify";
    private static final long STALE_AFTER_MS = 120_000L;   // recently-played changes slowly

    /** Called on the main thread with the items to show; never null, possibly empty. */
    interface Listener {
        void onItems(List<SpotifyItem> items);
    }

    /**
     * Told when Spotify issues a replacement refresh token, so it can be written to the theme's
     * config. Called on a background thread.
     */
    interface TokenSink {
        void onRefreshToken(String rotated);
    }

    private final SpotifyClient client;
    private final File cacheDir;
    private final int max;
    private final TokenSink tokenSink;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "spotify-recent");
        t.setDaemon(true);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    private volatile List<SpotifyItem> items = Collections.emptyList();
    private volatile long fetchedAt;

    SpotifyRecent(Context ctx, SpotifyAuth auth, int max, TokenSink tokenSink) {
        this.client = new SpotifyClient(auth);
        this.cacheDir = Config.dir(ctx);
        this.max = max;
        this.tokenSink = tokenSink;
    }

    List<SpotifyItem> current() {
        return items;
    }

    /**
     * Fetches artwork bytes for the row. Spotify's image URLs are absolute and public, so unlike a
     * Plex poster there is nothing to assemble and no token to attach; a plain GET is all it takes.
     * Called on the row's own background thread, and null on any failure.
     */
    byte[] artBytes(String url) {
        if (url == null || url.isEmpty()) return null;
        return client.fetchBytes(url);
    }

    boolean hasItems() {
        return !items.isEmpty();
    }

    /** True when Spotify has retired the login, so the row should say so rather than show nothing. */
    boolean needsLogin() {
        return client.needsLogin();
    }

    boolean isConfigured() {
        return client.isConfigured();
    }

    /**
     * Fetches the recently-played list and delivers it on the main thread, falling back to the disk
     * cache when Spotify cannot be reached.
     */
    void load(Listener listener) {
        if (!inFlight.compareAndSet(false, true)) return;
        io.execute(() -> {
            List<SpotifyItem> fresh = Collections.emptyList();
            try {
                fresh = client.recentlyPlayed(max);
                if (!fresh.isEmpty()) {
                    SpotifyCache.write(cacheDir, fresh);
                    fetchedAt = System.currentTimeMillis();
                }
                persistRotatedToken();
            } catch (Exception e) {
                Log.w(TAG, "recently-played load failed: " + e);
            } finally {
                List<SpotifyItem> result = fresh;
                if (result.isEmpty()) result = SpotifyCache.read(cacheDir);
                if (result.size() > max) result = result.subList(0, max);
                final List<SpotifyItem> out = result;
                items = out;
                inFlight.set(false);
                main.post(() -> {
                    if (listener != null) listener.onItems(out);
                });
            }
        });
    }

    /** Re-fetches only when the last good reply is old enough to be worth replacing. */
    void refreshIfStale(Listener listener) {
        if (System.currentTimeMillis() - fetchedAt < STALE_AFTER_MS && hasItems()) return;
        load(listener);
    }

    /** Seeds the row from the cache without touching the network, so a cold start has something at once. */
    void primeFromCache() {
        List<SpotifyItem> cached = SpotifyCache.read(cacheDir);
        if (cached.size() > max) cached = cached.subList(0, max);
        items = cached;
    }

    /** Hands a rotated refresh token to the caller, which owns writing it to the config. */
    private void persistRotatedToken() {
        String rotated = client.rotatedRefreshToken();
        if (rotated != null && tokenSink != null) tokenSink.onRefreshToken(rotated);
    }

    void shutdown() {
        io.shutdownNow();
    }
}
