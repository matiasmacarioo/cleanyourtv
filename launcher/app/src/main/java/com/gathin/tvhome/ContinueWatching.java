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
 * Keeps the Continue Watching row's data: fetches the Plex on-deck list off the main thread, caches it
 * to disk, and hands items back on the main thread.
 *
 * <p>The row is only ever shown while the Plex tile is focused, so there is no polling. {@link #load}
 * is called when the row comes into view; {@link #refreshIfStale} throttles a re-fetch so flicking
 * across the dock does not hammer the server.
 *
 * <p>When the server cannot be reached, the last cached reply is used instead. Only a genuinely empty
 * result — no cache, nothing from the server — leaves the row with nothing to show, which the caller
 * treats as "no row".
 *
 * <p>One request is in flight at a time; a second {@link #load} while one is running is dropped rather
 * than queued, because a stale list is worse than no refresh.
 */
final class ContinueWatching {
    private static final String TAG = "TvHome.Plex";
    /** Do not re-fetch more often than this when the tile is focused again. */
    private static final long STALE_AFTER_MS = 60_000L;

    /** Called on the main thread with the items to show; never null, possibly empty. */
    interface Listener {
        void onItems(List<PlexItem> items);
    }

    private final PlexClient client;
    private final File cacheDir;
    private final int max;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "plex-on-deck");
        t.setDaemon(true);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean inFlight = new AtomicBoolean(false);

    private volatile List<PlexItem> items = Collections.emptyList();
    private volatile long fetchedAt;

    ContinueWatching(Context ctx, String base, String token, int max) {
        this.client = new PlexClient(base, token);
        this.cacheDir = Config.dir(ctx);
        this.max = max;
    }

    /** The items last loaded, for a caller that needs them without kicking off a fetch. */
    List<PlexItem> current() {
        return items;
    }

    /** True once a fetch has produced something worth showing. */
    boolean hasItems() {
        return !items.isEmpty();
    }

    /**
     * Fetches the on-deck list and delivers it to {@code listener} on the main thread. Falls back to
     * the disk cache when the server is unreachable, so the row survives a sleeping server.
     */
    void load(Listener listener) {
        if (!inFlight.compareAndSet(false, true)) return;
        io.execute(() -> {
            List<PlexItem> fresh = Collections.emptyList();
            java.util.concurrent.atomic.AtomicReference<String> raw =
                    new java.util.concurrent.atomic.AtomicReference<>();
            try {
                fresh = client.onDeck(raw);
                String body = raw.get();
                if (!fresh.isEmpty() && body != null) {
                    PlexCache.write(cacheDir, body);
                    fetchedAt = System.currentTimeMillis();
                }
            } catch (Exception e) {
                Log.w(TAG, "on-deck load failed: " + e);
            } finally {
                List<PlexItem> result = fresh;
                if (result.isEmpty()) result = cached();
                if (result.size() > max) result = result.subList(0, max);
                final List<PlexItem> out = result;
                items = out;
                inFlight.set(false);
                main.post(() -> {
                    if (listener != null) listener.onItems(out);
                });
            }
        });
    }

    /**
     * Builds the deep link that opens {@code ratingKey}'s item directly, and hands it to
     * {@code onLink} on the main thread; an empty link means no direct link could be made.
     *
     * <p>Runs on the worker because it needs the server's {@code machineIdentifier}, which is one
     * more request. The client caches it, so only the first click after a start pays for it, and the
     * work is off-thread so a sleeping server cannot freeze the row mid-click.
     */
    void deepLink(String ratingKey, java.util.function.Consumer<String> onLink) {
        io.execute(() -> {
            String link;
            try {
                link = PlexUrls.plezyDeepLink(client.machineIdentifier(), ratingKey);
            } catch (Exception e) {
                Log.w(TAG, "deep link build failed: " + e);
                link = "";
            }
            if (link.isEmpty()) {
                // Not a fault: Plex for Android has no item link, and any server hiccup lands here
                // too. Debug because the caller falls back to opening the app, which still works.
                Log.d(TAG, "no direct link for ratingKey " + ratingKey + ", opening the app instead");
            }
            final String out = link;
            main.post(() -> onLink.accept(out));
        });
    }

    /** Re-fetches only when the last good reply is old enough to be worth replacing. */
    void refreshIfStale(Listener listener) {
        if (System.currentTimeMillis() - fetchedAt < STALE_AFTER_MS && hasItems()) return;
        load(listener);
    }

    /** Seeds the row from the cache without touching the network, so a cold start has something at once. */
    void primeFromCache() {
        List<PlexItem> cached = cached();
        if (cached.size() > max) cached = cached.subList(0, max);
        items = cached;
    }

    private List<PlexItem> cached() {
        return PlexItem.parse(PlexCache.read(cacheDir));
    }

    /** Stops the worker; called when Home is destroyed. */
    void shutdown() {
        io.shutdownNow();
    }
}
