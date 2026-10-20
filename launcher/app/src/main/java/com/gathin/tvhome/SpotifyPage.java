package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * The Intent that opens a recently-played item in Spotify.
 *
 * <p>Spotify's TV app declares a real, checkable link contract, unlike Plex's bare {@code plex:}
 * scheme. Its manifest registers {@code open.spotify.com} with an explicit path prefix per item kind
 * — {@code /track}, {@code /album}, {@code /episode}, {@code /show} and others — so
 * {@code https://open.spotify.com/track/<id>} is a documented shape rather than a guess. Verified on
 * the TV with {@code cmd package query-activities}: every form built here resolves to
 * {@code com.spotify.tv.android/.SpotifyTVActivity}.
 *
 * <p>{@code https} is preferred over Spotify's own {@code spotify:} scheme because the https form
 * carries the item kind in its path, which is what lets one builder serve both a track and a podcast
 * episode. The app accepts both; only this one is self-describing.
 *
 * <p>What that buys is a click that always lands somewhere useful. Spotify owns the decision of where
 * in an episode to resume; the launcher only says which item.
 */
final class SpotifyPage {
    /** The package the dock lists; the TV build, not the phone one. */
    static final String PACKAGE = "com.spotify.tv.android";

    private SpotifyPage() {}

    /**
     * Opens {@code item} in Spotify, or the app's own front screen when the item carries no id to
     * link to. A click must never be a no-op, so the fallback is folded in here rather than left to
     * the caller.
     */
    static Intent itemIntent(Context ctx, SpotifyItem item) {
        Uri uri = itemUri(item);
        if (uri == null) return launchIntent(ctx);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage(PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /**
     * The {@code open.spotify.com} link for an item, or null when there is no id to build one from.
     * A track and an episode differ only in the path segment, which is the whole reason this form is
     * used over {@code spotify:}.
     *
     * <p>An id is taken from the item's own {@code uri} when the JSON supplied one, because that is
     * Spotify's authoritative identifier; the bare {@code id} field is the fallback.
     */
    static Uri itemUri(SpotifyItem item) {
        String url = itemUrl(item);
        return url == null ? null : Uri.parse(url);
    }

    /**
     * The link as a string, split out from {@link #itemUri} so the URL shape — the one thing a device
     * either resolves or does not — is testable without the Android framework in the way.
     *
     * <p>An id is taken from the item's own {@code uri} when the JSON supplied one, because that is
     * Spotify's authoritative identifier; the bare {@code id} field is the fallback. Null when neither
     * gives one, which the caller turns into the app's front screen.
     */
    static String itemUrl(SpotifyItem item) {
        if (item == null) return null;
        String id = idOf(item);
        if (id == null || id.isEmpty()) return null;
        String kind = item.isEpisode() ? "episode" : "track";
        return "https://open.spotify.com/" + kind + "/" + encode(id);
    }

    /**
     * Percent-encodes an id for a URL path. Spotify's ids are base62 so this is a no-op in practice,
     * but it keeps a malformed reply from producing a link with a space or a slash in it. Uses
     * {@link java.net.URLEncoder} rather than {@code android.net.Uri.encode} so the URL shape can be
     * checked by a plain JVM test.
     */
    private static String encode(String s) {
        try {
            // URLEncoder is form encoding: a space becomes "+", which is wrong in a path.
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    /** Spotify's own id for the item, from its {@code uri} where possible. */
    private static String idOf(SpotifyItem item) {
        String uri = item.uri;
        if (uri != null && !uri.isEmpty()) {
            // spotify:track:<id> — the id is whatever follows the last colon.
            int colon = uri.lastIndexOf(':');
            if (colon >= 0 && colon < uri.length() - 1) return uri.substring(colon + 1);
        }
        return item.id;
    }

    /**
     * The app's own start screen, used when an item cannot be linked to (no id, or an item that has
     * since left the account).
     */
    static Intent launchIntent(Context ctx) {
        Intent launch = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(PACKAGE);
        if (launch == null) {
            launch = ctx.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        }
        if (launch != null) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return launch;
    }
}
