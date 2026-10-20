package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Builds and drives the Continue Watching strip: the posters, the progress bars, the titles, and the
 * focus behaviour that makes it feel like a shelf rather than a list.
 *
 * <p>Deliberately hand-rolled rather than a RecyclerView: on-deck is a handful of items (eight by
 * default), and the strip is rebuilt whenever the list changes, so a recycling adapter would be more
 * machinery than the row needs.
 *
 * <p>Posters are fetched one at a time on a single background thread and cached in memory by URL, so
 * moving across the row never re-downloads and never blocks the main thread. A card with no poster yet
 * simply shows the placeholder, which is why artwork arriving late never reflows the row.
 */
final class ContinueWatchingRow {
    /** The still's size, matching row_continue_watching_item.xml. */
    private static final int ART_WIDTH_DP = 232;
    private static final int ART_HEIGHT_DP = 130;
    /** Fetched at 2x and downscaled on a TV, whose density makes 1x look soft. */
    private static final int FETCH_SCALE = 2;

    interface Listener {
        /** A card took focus: the caller moves the highlight and lets the dock know. */
        void onItemFocused(PlexItem item, View card);

        /** D-pad Down from the row: back to the dock. */
        void onDownFromRow();

        /** A card was clicked: resume that item in Plex. */
        void onItemClicked(PlexItem item);
    }

    private final Context ctx;
    private final View row;
    private final LinearLayout items;
    private final PlexClient client;
    private final int artW;
    private final int artH;
    private final float density;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "plex-posters");
        t.setDaemon(true);
        return t;
    });
    /** Poster bitmaps by URL, so re-showing the row does not re-download. */
    private final Map<String, Bitmap> posterCache = new HashMap<>();
    private Listener listener;
    private String base = "";
    private String tok = "";
    private List<PlexItem> shown = Collections.emptyList();

    /** The server and token the poster URLs are built from. */
    void setServer(String base, String token) {
        this.base = base == null ? "" : base;
        this.tok = token == null ? "" : token;
    }

    ContinueWatchingRow(Context ctx, View row, PlexClient client) {
        this.ctx = ctx;
        this.row = row;
        this.items = row.findViewById(R.id.continueWatchingItems);
        this.client = client;
        this.density = ctx.getResources().getDisplayMetrics().density;
        this.artW = (int) (ART_WIDTH_DP * density);
        this.artH = (int) (ART_HEIGHT_DP * density);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    /** The strip itself, so the caller can lay it out and animate it. */
    View view() {
        return row;
    }

    /** Items currently on screen, in order. */
    List<PlexItem> shownItems() {
        return shown;
    }

    /** True when there is a card to focus. */
    boolean hasItems() {
        return !shown.isEmpty();
    }

    /**
     * Replaces the strip's contents. An empty list hides the row, which is how "Plex has nothing on
     * deck" and "the server is unreachable" both end up as no row rather than an empty shelf.
     */
    void setItems(List<PlexItem> newItems) {
        shown = newItems == null ? Collections.emptyList() : newItems;
        row.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
        items.removeAllViews();
        if (shown.isEmpty()) return;
        LayoutInflater inflater = LayoutInflater.from(ctx);
        for (PlexItem item : shown) {
            items.addView(buildCard(inflater, item));
        }
    }

    private View buildCard(LayoutInflater inflater, PlexItem item) {
        View card = inflater.inflate(R.layout.row_continue_watching_item, items, false);
        TextView title = card.findViewById(R.id.cwTitle);
        TextView subtitle = card.findViewById(R.id.cwSubtitle);
        title.setText(item.title);
        String sub = item.subtitle();
        subtitle.setText(sub);
        subtitle.setVisibility(sub.isEmpty() ? View.GONE : View.VISIBLE);

        // Progress: the filled portion as a fraction of the track. A zero-duration item shows none.
        View progress = card.findViewById(R.id.cwProgress);
        FrameLayout track = (FrameLayout) progress.getParent();
        track.post(() -> {
            ViewGroup.LayoutParams lp = progress.getLayoutParams();
            lp.width = Math.round(track.getWidth() * item.progress());
            progress.setLayoutParams(lp);
        });

        ImageView art = card.findViewById(R.id.cwArt);
        loadPoster(item, art);

        View rim = card.findViewById(R.id.cwRim);
        rim.setAlpha(0f);
        card.setOnFocusChangeListener((v, focused) -> {
            rim.animate().cancel();
            rim.animate().alpha(focused ? 1f : 0f).setDuration(120).start();
            v.animate().cancel();
            float scale = focused ? 1.06f : 1f;
            v.animate().scaleX(scale).scaleY(scale).setDuration(140).start();
            if (focused && listener != null) listener.onItemFocused(item, v);
        });
        card.setOnClickListener(v -> {
            if (listener != null) listener.onItemClicked(item);
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            // Down leaves the shelf for the dock; Left/Right move between cards and are handled by
            // the parent's focus search, which is what makes the strip feel like one row.
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                if (listener != null) listener.onDownFromRow();
                return true;
            }
            return false;
        });
        return card;
    }

    /** Focuses the first card, if any; returns whether focus moved. */
    boolean focusFirst() {
        return items.getChildCount() > 0 && items.getChildAt(0).requestFocus();
    }

    /**
     * Fetches and shows a poster, from memory when possible. The bitmap is sized to the card so a
     * full-size still never sits in memory on a TV with little to spare.
     */
    private void loadPoster(PlexItem item, ImageView art) {
        String path = item.posterPath();
        if (path == null) return;
        String url = PlexUrls.poster(base, tok, path, artW * FETCH_SCALE, artH * FETCH_SCALE);
        Bitmap cached;
        synchronized (posterCache) {
            cached = posterCache.get(url);
        }
        if (cached != null) {
            art.setImageBitmap(cached);
            return;
        }
        io.execute(() -> {
            byte[] bytes = client.posterBytes(url);
            if (bytes == null) return;
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) return;
            synchronized (posterCache) {
                posterCache.put(url, bmp);
            }
            art.post(() -> art.setImageBitmap(bmp));
        });
    }

    /** Drops cached posters; called when the server or token changes. */
    void clearPosters() {
        synchronized (posterCache) {
            posterCache.clear();
        }
    }

    void shutdown() {
        io.shutdownNow();
    }
}
