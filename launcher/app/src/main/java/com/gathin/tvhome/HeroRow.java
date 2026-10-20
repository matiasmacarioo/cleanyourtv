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
 * Builds and drives a hero shelf: the strip of cards above the dock that shows what you were in the
 * middle of. One instance serves one service, so Plex's on-deck and Spotify's recently-played each
 * get their own.
 *
 * <p>The row draws nothing service-specific. It asks each {@link RowItem} for a title, a second line,
 * a progress fraction and an artwork URL, and it fetches artwork through the {@link ImageLoader} it
 * was given — which is what lets the two services differ without the row knowing: Plex needs its
 * server and token appended to a relative path, Spotify just needs a GET.
 *
 * <p>Deliberately hand-rolled rather than a RecyclerView: a shelf is a handful of items (eight by
 * default), and the strip is rebuilt whenever the list changes, so a recycling adapter would be more
 * machinery than the row needs.
 *
 * <p>Artwork is fetched one at a time on a single background thread and cached in memory by URL, so
 * moving across the row never re-downloads and never blocks the main thread. A card with no artwork
 * yet simply shows the placeholder, which is why artwork arriving late never reflows the row.
 */
final class HeroRow {
    /** The card's size, matching row_continue_watching_item.xml. */
    private static final int ART_WIDTH_DP = 232;
    private static final int ART_HEIGHT_DP = 130;
    /** Fetched at 2x and downscaled on a TV, whose density makes 1x look soft. */
    private static final int FETCH_SCALE = 2;

    /** Fetches the bytes behind an artwork URL, or null. Called on a background thread. */
    interface ImageLoader {
        byte[] load(String url);
    }

    interface Listener {
        /** A card took focus: the caller moves the highlight and lets the dock know. */
        void onItemFocused(RowItem item, View card);

        /** D-pad Down from the row: back to the dock. */
        void onDownFromRow();

        /** A card was clicked: open that item in its app. */
        void onItemClicked(RowItem item);
    }

    private final Context ctx;
    private final View row;
    private final LinearLayout items;
    private final ImageLoader imageLoader;
    private final int artW;
    private final int artH;
    private final float density;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hero-art");
        t.setDaemon(true);
        return t;
    });
    /** Artwork bitmaps by URL, so re-showing the row does not re-download. */
    private final Map<String, Bitmap> artCache = new HashMap<>();
    private Listener listener;
    private List<RowItem> shown = Collections.emptyList();

    HeroRow(Context ctx, View row, int itemsId, ImageLoader imageLoader) {
        this.ctx = ctx;
        this.row = row;
        this.imageLoader = imageLoader;
        this.items = row.findViewById(itemsId);
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
    List<RowItem> shownItems() {
        return shown;
    }

    /** True when there is a card to focus. */
    boolean hasItems() {
        return !shown.isEmpty();
    }

    /**
     * Replaces the strip's contents. An empty list hides the row, which is how "nothing to continue"
     * and "the service is unreachable" both end up as no row rather than an empty shelf.
     */
    void setItems(List<? extends RowItem> newItems) {
        shown = newItems == null ? Collections.emptyList()
                : Collections.unmodifiableList(new java.util.ArrayList<>(newItems));
        row.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
        items.removeAllViews();
        if (shown.isEmpty()) return;
        LayoutInflater inflater = LayoutInflater.from(ctx);
        for (RowItem item : shown) {
            items.addView(buildCard(inflater, item));
        }
    }

    private View buildCard(LayoutInflater inflater, RowItem item) {
        View card = inflater.inflate(R.layout.row_continue_watching_item, items, false);
        TextView title = card.findViewById(R.id.cwTitle);
        TextView subtitle = card.findViewById(R.id.cwSubtitle);
        title.setText(item.rowTitle());
        String sub = item.rowSubtitle();
        subtitle.setText(sub == null ? "" : sub);
        subtitle.setVisibility(sub == null || sub.isEmpty() ? View.GONE : View.VISIBLE);

        // Progress: the filled portion as a fraction of the track. An item with no position shows none.
        View progress = card.findViewById(R.id.cwProgress);
        FrameLayout track = (FrameLayout) progress.getParent();
        boolean hasBar = item.rowHasProgress();
        track.setVisibility(hasBar ? View.VISIBLE : View.GONE);
        if (hasBar) {
            track.post(() -> {
                ViewGroup.LayoutParams lp = progress.getLayoutParams();
                lp.width = Math.round(track.getWidth() * item.rowProgress());
                progress.setLayoutParams(lp);
            });
        }

        ImageView art = card.findViewById(R.id.cwArt);
        loadArt(item.rowImageUrl(), art);

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
     * Fetches and shows artwork, from memory when possible. The bitmap is sized to the card so a
     * full-size image never sits in memory on a TV with little to spare.
     */
    private void loadArt(String url, ImageView art) {
        if (url == null || url.isEmpty()) return;
        Bitmap cached;
        synchronized (artCache) {
            cached = artCache.get(url);
        }
        if (cached != null) {
            art.setImageBitmap(cached);
            return;
        }
        io.execute(() -> {
            byte[] bytes = imageLoader == null ? null : imageLoader.load(url);
            if (bytes == null) return;
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) return;
            synchronized (artCache) {
                artCache.put(url, bmp);
            }
            art.post(() -> art.setImageBitmap(bmp));
        });
    }

    /** Drops cached artwork; called when the service's server or credentials change. */
    void clearArt() {
        synchronized (artCache) {
            artCache.clear();
        }
    }

    /** The pixel size the row asks for, so a loader can request a sensibly-sized image. */
    int artPixelWidth() {
        return artW * FETCH_SCALE;
    }

    int artPixelHeight() {
        return artH * FETCH_SCALE;
    }

    void shutdown() {
        io.shutdownNow();
    }
}
