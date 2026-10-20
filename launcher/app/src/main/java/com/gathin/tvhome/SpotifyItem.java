package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One track or episode from Spotify's {@code /me/player/recently-played}, with how far it was played.
 *
 * <p>Built from the Spotify Web API's JSON. The interesting shape is that Spotify wraps each entry in
 * a {@code played_at} timestamp and puts the track itself under {@code track}, so the response is
 * "items[] → track" rather than a flat list. An episode has {@code show} where a track has
 * {@code artists}, and {@code resume_point} where a track has nothing; {@link #subtitle()} and
 * {@link #progress()} hide both differences.
 *
 * <p>Resume position is only sent for podcast episodes — Spotify does not report how far into a song
 * you were — so a track is shown as recently played rather than part-played, which is what the service
 * actually knows. {@link #hasProgress()} lets the row leave the bar off instead of drawing an empty
 * one.
 *
 * <p>Parsing is forgiving: an entry missing a name is skipped, and a missing field becomes empty or
 * zero rather than an error, so a partially-described track is still worth showing.
 */
final class SpotifyItem implements RowItem {
    /** The track or episode id, used to build the app's own URI and for focus memory. */
    final String id;
    /** The track's own name. */
    final String title;
    /** The artists, joined; for an episode, the show's name instead. */
    final String subtitle;
    /** The item's {@code uri} ({@code spotify:track:...}), which is how the app is asked to open it. */
    final String uri;
    /** The artwork URL: Spotify serves these as absolute {@code https://i.scdn.co/...} links. */
    final String imageUrl;
    /** Total length in milliseconds; 0 when Spotify gave none. */
    final long durationMs;
    /** How far in, in milliseconds; only ever set for episodes. */
    final long resumeMs;
    /** When it was played, as an epoch millisecond from Spotify's {@code played_at}. */
    final long playedAtMs;
    /** True for a podcast episode, false for a track. */
    final boolean episode;
    /** The album name for a track, or null. Shown as the third line when there is room. */
    final String album;

    SpotifyItem(String id, String title, String subtitle, String uri, String imageUrl,
                long durationMs, long resumeMs, long playedAtMs, boolean episode, String album) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.uri = uri;
        this.imageUrl = imageUrl;
        this.durationMs = durationMs;
        this.resumeMs = resumeMs;
        this.playedAtMs = playedAtMs;
        this.episode = episode;
        this.album = album;
    }

    /**
     * How far in, 0..1, or 0 when Spotify gave no position. Only episodes carry one, so a track
     * returns 0 and the row draws no bar.
     */
    float progress() {
        if (durationMs <= 0 || resumeMs <= 0) return 0f;
        float f = (float) resumeMs / (float) durationMs;
        return f > 1f ? 1f : f;
    }

    /** True when there is a real resume position to draw, rather than just "played recently". */
    boolean hasProgress() {
        return durationMs > 0 && resumeMs > 0;
    }

    /** True when this came from Spotify as a podcast episode. */
    boolean isEpisode() {
        return episode;
    }

    /**
     * The recently-played entries in a Spotify JSON body, in the order Spotify sent them (most recent
     * first). Returns an empty list for anything unparseable, so an error body or an unexpected shape
     * leaves the row empty rather than crashing Home.
     */
    static List<SpotifyItem> parse(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException e) {
            return Collections.emptyList();
        }
        JSONArray items = root.optJSONArray("items");
        if (items == null) return Collections.emptyList();
        List<SpotifyItem> out = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject entry = items.optJSONObject(i);
            if (entry == null) continue;
            // A track and an episode live in different keys; whichever is present decides the type.
            JSONObject track = entry.optJSONObject("track");
            boolean episode = false;
            if (track == null) {
                track = entry.optJSONObject("episode");
                episode = track != null;
            }
            if (track == null) continue;
            String title = track.optString("name", "");
            if (title.isEmpty()) continue;                     // nothing to show without a name
            String id = track.optString("id", "");
            String uri = track.optString("uri", "");
            if (uri.isEmpty() && !id.isEmpty()) {
                uri = (episode ? "spotify:episode:" : "spotify:track:") + id;
            }
            long duration = track.optLong("duration_ms", 0L);
            long resume = 0L;
            JSONObject resumePoint = track.optJSONObject("resume_point");
            if (resumePoint != null) resume = resumePoint.optLong("resume_position_ms", 0L);
            String album = null;
            JSONObject albumObj = track.optJSONObject("album");
            if (albumObj != null) album = nullIfEmpty(albumObj.optString("name", ""));
            out.add(new SpotifyItem(
                    id,
                    title,
                    episode ? showName(track) : artistNames(track),
                    uri,
                    imageUrl(track),
                    duration,
                    resume,
                    parseTime(entry.optString("played_at", "")),
                    episode,
                    album));
        }
        return out;
    }

    /** The show's name for an episode, or empty. */
    private static String showName(JSONObject episode) {
        JSONObject show = episode.optJSONObject("show");
        if (show == null) return "";
        return show.optString("name", "");
    }

    /** A track's artists joined with commas, which is how Spotify itself lists them. */
    private static String artistNames(JSONObject track) {
        JSONArray artists = track.optJSONArray("artists");
        if (artists == null || artists.length() == 0) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < artists.length(); i++) {
            JSONObject a = artists.optJSONObject(i);
            if (a == null) continue;
            String name = a.optString("name", "");
            if (name.isEmpty()) continue;
            if (b.length() > 0) b.append(", ");
            b.append(name);
        }
        return b.toString();
    }

    /**
     * The artwork URL. Spotify nests these under {@code album.images} for a track and
     * {@code images} for an episode, each an array largest-first; the middle one is picked because the
     * largest is needlessly big for a TV tile and the smallest is visibly soft.
     */
    private static String imageUrl(JSONObject item) {
        JSONArray images = item.optJSONArray("images");
        if (images == null) {
            JSONObject album = item.optJSONObject("album");
            if (album != null) images = album.optJSONArray("images");
        }
        if (images == null || images.length() == 0) return null;
        JSONObject pick = images.optJSONObject(images.length() > 1 ? 1 : 0);
        if (pick == null) return null;
        return nullIfEmpty(pick.optString("url", ""));
    }

    // ---- RowItem: what the hero shelf above the dock draws ----------------------------------------

    @Override public String rowTitle() {
        return title;
    }

    @Override public String rowSubtitle() {
        return subtitle;
    }

    @Override public float rowProgress() {
        return progress();
    }

    @Override public boolean rowHasProgress() {
        return hasProgress();
    }

    /**
     * Spotify serves artwork as absolute {@code https://i.scdn.co/...} links, so unlike Plex's
     * server-relative paths this needs no assembling before it is fetched.
     */
    @Override public String rowImageUrl() {
        return imageUrl;
    }

    /**
     * Spotify's {@code played_at} is ISO-8601 UTC ({@code 2026-10-20T19:04:05.123Z}). Parsed with
     * {@code java.time} rather than a formatter pattern, because the fractional seconds are optional
     * and a strict pattern would reject half the replies. An unparseable timestamp is 0, which just
     * means "no time to show".
     */
    private static long parseTime(String iso) {
        if (iso == null || iso.isEmpty()) return 0L;
        try {
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (Exception e1) {
            try {
                return java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli();
            } catch (Exception e2) {
                return 0L;
            }
        }
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
