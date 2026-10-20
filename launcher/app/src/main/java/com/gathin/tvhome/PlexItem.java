package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One in-progress item from the Plex server's on-deck list: what you were watching, and how far in.
 *
 * <p>Built from the JSON Plex returns for {@code /library/onDeck}. Plex's JSON nests the interesting
 * fields under {@code MediaContainer.Metadata}, and uses two shapes: an episode carries
 * {@code grandparentTitle} (the show) plus {@code parentIndex}/{@code index} (season/episode), while a
 * movie only has {@code title}. {@link #subtitle()} hides that difference.
 *
 * <p>Parsing is deliberately forgiving of missing fields: an item Plex describes only partially is
 * still worth showing, so anything absent becomes an empty string or zero rather than an error.
 */
final class PlexItem implements RowItem {
    /** Stable id for the row's focus memory; Plex's own {@code ratingKey}. */
    final String ratingKey;
    /** An episode or movie; Plex's {@code type}. */
    final String type;
    /** The episode's own name, or the movie's title. */
    final String title;
    /** The show's name for an episode (Plex {@code grandparentTitle}); null for a movie. */
    final String showTitle;
    /** Season number for an episode, or 0 when Plex gave none. */
    final int season;
    /** Episode number for an episode, or 0 when Plex gave none. */
    final int episode;
    /** Total length in milliseconds; 0 when Plex gave none. */
    final long duration;
    /** How far in, in milliseconds. */
    final long viewOffset;
    /** Path to the item's own artwork ({@code thumb}), relative to the server root. */
    final String thumb;
    /** Plex's own key for the item ({@code /library/metadata/228464}); used to link back to it. */
    final String key;
    /** Path to the show's artwork ({@code grandparentThumb}) for an episode, else null. */
    final String showThumb;
    /** Year, or 0 when Plex gave none. */
    final int year;

    PlexItem(String ratingKey, String type, String title, String showTitle, int season, int episode,
             long duration, long viewOffset, String thumb, String showThumb, int year, String key) {
        this.ratingKey = ratingKey;
        this.type = type;
        this.title = title;
        this.showTitle = showTitle;
        this.season = season;
        this.episode = episode;
        this.duration = duration;
        this.viewOffset = viewOffset;
        this.thumb = thumb;
        this.showThumb = showThumb;
        this.year = year;
        this.key = key;
    }

    /** True for a Plex episode, false for a movie or anything else. */
    boolean isEpisode() {
        return "episode".equals(type);
    }

    /** The second line: "Show · S2E1" for an episode, the year for a movie, else nothing. */
    String subtitle() {
        if (isEpisode()) {
            StringBuilder b = new StringBuilder();
            if (showTitle != null && !showTitle.isEmpty()) b.append(showTitle);
            if (season > 0 && episode > 0) {
                if (b.length() > 0) b.append(" · ");
                b.append('S').append(season).append('E').append(episode);
            }
            return b.toString();
        }
        return year > 0 ? String.valueOf(year) : "";
    }

    /** How far in, 0..1. Zero when Plex gave no duration, so the bar simply does not show. */
    float progress() {
        if (duration <= 0) return 0f;
        float f = (float) viewOffset / (float) duration;
        if (f < 0f) return 0f;
        if (f > 1f) return 1f;
        return f;
    }

    /** The artwork to show: the item's own, else the show's, else null. */
    String posterPath() {
        if (thumb != null && !thumb.isEmpty()) return thumb;
        if (showThumb != null && !showThumb.isEmpty()) return showThumb;
        return null;
    }

    // ---- RowItem: what the hero shelf above the dock draws ----------------------------------------

    @Override public String rowTitle() {
        return title;
    }

    @Override public String rowSubtitle() {
        return subtitle();
    }

    @Override public float rowProgress() {
        return progress();
    }

    /** Plex always reports a duration, so the bar is meaningful whenever there is one. */
    @Override public boolean rowHasProgress() {
        return duration > 0;
    }

    /**
     * The absolute URL the shelf fetches. Plex serves artwork from the server root and needs the
     * token, so the row hands the loader a URL it can use directly; {@link #posterPath()} is the
     * server-relative form and stays public for that reason.
     */
    @Override public String rowImageUrl() {
        return posterPath();
    }

    /**
     * The on-deck items in a Plex {@code /library/onDeck} JSON body, in the order Plex sent them
     * (most recently watched first). Returns an empty list for anything unparseable, so a server
     * that answers with an error page or an unexpected shape leaves the row empty rather than
     * crashing Home.
     */
    static List<PlexItem> parse(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException e) {
            return Collections.emptyList();
        }
        JSONObject container = root.optJSONObject("MediaContainer");
        JSONArray metadata = container != null ? container.optJSONArray("Metadata") : null;
        if (metadata == null) return Collections.emptyList();
        List<PlexItem> items = new ArrayList<>();
        for (int i = 0; i < metadata.length(); i++) {
            JSONObject o = metadata.optJSONObject(i);
            if (o == null) continue;
            String title = o.optString("title", "");
            if (title.isEmpty()) continue;                     // an item with no name is not worth a tile
            items.add(new PlexItem(
                    o.optString("ratingKey", ""),
                    o.optString("type", ""),
                    title,
                    nullIfEmpty(o.optString("grandparentTitle", "")),
                    o.optInt("parentIndex", 0),
                    o.optInt("index", 0),
                    o.optLong("duration", 0L),
                    o.optLong("viewOffset", 0L),
                    nullIfEmpty(o.optString("thumb", "")),
                    nullIfEmpty(o.optString("grandparentThumb", "")),
                    o.optInt("year", 0),
                    o.optString("key", "")));
        }
        return items;
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
