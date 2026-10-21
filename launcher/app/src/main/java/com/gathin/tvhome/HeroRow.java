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
    /** The card's artwork size, matching row_continue_watching_item.xml. */
    private static final int ART_WIDTH_DP = 232;
    private static final int ART_HEIGHT_DP = 130;
    /** The card is exactly as wide as its artwork; the focus gap is a margin, not part of the card. */
    private static final int CARD_WIDTH_DP = ART_WIDTH_DP;
    /**
     * How much the focused card grows. The card keeps 12dp of padding on each side beyond its artwork
     * (row_continue_watching_item.xml), and 1.08 needs 10.2dp of that, so it fits with room to spare.
     * Raising this without widening that padding is exactly what caused the overlap - the growth has
     * to come out of space the card already reserved, not out of a neighbour.
     */
    private static final float FOCUS_SCALE = 1.08f;
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

        /** D-pad Up from the row: out of the shelf and into the top bar. */
        void onUpFromRow();

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
        // Width is pinned here rather than left to the layout: the title is match_parent inside the
        // card, so a card that measures to its children would size itself to the longest title and shove
        // every card after it along. A fixed width is what clips a long title to an ellipsis instead.
        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(cardWidthPx(), ViewGroup.LayoutParams.WRAP_CONTENT);
        int growth = Math.round(cardWidthPx() * (FOCUS_SCALE - 1f));
        int margin = growth / 2 + Math.round(2 * density);
        cardParams.setMarginStart(margin);
        cardParams.setMarginEnd(margin);
        card.setLayoutParams(cardParams);
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
            // Grows into the padding the card and strip keep around it rather than over a neighbour -
            // see row_continue_watching_item.xml for why that space exists. Elevation carries the
            // sense of the card lifting; without it a scaled card just looks like a wider one.
            float scale = focused ? FOCUS_SCALE : 1f;
            v.animate()
                    .scaleX(scale).scaleY(scale)
                    .translationZ((focused ? 1f : 0f) * 8 * density)
                    .setDuration(140)
                    .start();
            if (focused && listener != null) listener.onItemFocused(item, v);
        });
        card.setOnClickListener(v -> {
            if (listener != null) listener.onItemClicked(item);
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            // Up and Down leave the shelf: Down to the dock, Up out to the top bar. Both are handled
            // here rather than left to the focus search, which would otherwise pick whichever view
            // happens to be nearest and make leaving the row feel arbitrary.
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                if (listener != null) listener.onDownFromRow();
                return true;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                if (listener != null) listener.onUpFromRow();
                return true;
            }
            return false;   // Left/Right move between cards via the parent's focus search
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

    /** The card's full laid-out width, which is what {@link #FOCUS_SCALE} grows it from. */
    private int cardWidthPx() {
        return Math.round(CARD_WIDTH_DP * density);
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
