package com.gathin.tvhome;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * The Plex server URLs the Continue Watching row needs, built as plain strings so they can be pinned
 * down by a test without a server. Two rules matter:
 *
 * <ul>
 *   <li>{@code base} comes from a theme's {@code plex_url} and may be written either as
 *       {@code 192.168.120.230} or {@code http://192.168.120.230:32400}; {@link #normalise} fills in
 *       the scheme and port, so a theme cannot half-configure it,</li>
 *   <li>the token goes in the {@code X-Plex-Token} query parameter, and {@link #redact} exists so
 *       anything ever logged or shown on the TV carries no token.</li>
 * </ul>
 */
final class PlexUrls {
    /** Plex Media Server's default port. */
    static final int DEFAULT_PORT = 32400;

    private PlexUrls() {}

    /**
     * A usable server root: adds {@code http://} when the theme left the scheme off, drops a trailing
     * slash, and fills in a port when none was given — Plex's 32400 for a bare host, and the scheme's
     * own default for an explicit {@code https://}, where 32400 would be wrong. An empty value stays
     * empty, which is how "Plex is not configured" is represented.
     */
    static String normalise(String base) {
        if (base == null) return "";
        String s = base.trim();
        if (s.isEmpty()) return "";
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        boolean https = s.startsWith("https://");
        if (!https && !s.startsWith("http://")) s = "http://" + s;
        String afterScheme = s.substring(s.indexOf("://") + 3);
        // "host" without a port: a plain host is a Plex server on its default port, but an explicit
        // https:// is a real web address where the scheme's own port is the right one.
        if (!afterScheme.contains(":") && !https) s = s + ":" + DEFAULT_PORT;
        return s;
    }

    /** The on-deck list, most recently watched first. */
    static String onDeck(String base, String token) {
        return withToken(base + "/library/onDeck", token);
    }

    /**
     * The server's identity, whose {@code machineIdentifier} a Plezy deep link needs.
     *
     * <p>Not taken from the on-deck response: the {@code identifier} that carries is
     * {@code com.plexapp.plugins.library}, the plugin's name rather than the server's.
     */
    static String identity(String base, String token) {
        return withToken(base + "/identity", token);
    }

    /**
     * A Plezy deep link that opens one item directly, or empty when the required pieces are missing.
     *
     * <p>The shape is Plezy's own, read off its manifest and source: a {@code plezy} scheme with the
     * {@code play} host and a single {@code content_id} parameter of
     * {@code plezy_<machineIdentifier>_<ratingKey>}. Plezy splits that back into the server and the
     * item, fetches the item from the server, and starts playing it — resuming at the saved position,
     * since Plex keeps that server-side.
     *
     * <p>Plex for Android has no equivalent: its {@code plex://} forms land on the app's home screen,
     * which is why a card used to open the app and nothing more. A theme running Plezy gets the better
     * behaviour for free, and one running Plex still gets the app.
     */
    static String plezyDeepLink(String machineIdentifier, String ratingKey) {
        if (machineIdentifier == null || machineIdentifier.isEmpty()) return "";
        if (ratingKey == null || ratingKey.isEmpty()) return "";
        return "plezy://play?content_id=" + encode("plezy_" + machineIdentifier + "_" + ratingKey);
    }

    /**
     * A poster for {@code path} (an item's {@code thumb}) at {@code width} by {@code height} pixels.
     * Plex resizes and transcodes on demand through {@code /photo/:/transcode}, so the TV never
     * downloads the multi-megabyte original.
     */
    static String poster(String base, String token, String path, int width, int height) {
        if (path == null || path.isEmpty()) return "";
        String url = base + "/photo/:/transcode?width=" + width + "&height=" + height
                + "&minSize=1&upscale=0&url=" + encode(path);
        return withToken(url, token);
    }

    /** Adds {@code X-Plex-Token}; without a token the URL is returned untouched. */
    static String withToken(String url, String token) {
        if (token == null || token.isEmpty()) return url;
        return url + (url.contains("?") ? "&" : "?") + "X-Plex-Token=" + encode(token);
    }

    /**
     * The same URL with the token replaced by {@code REDACTED}, for a log line or an on-screen error.
     * This is the only way a URL should ever leave the app.
     */
    static String redact(String url) {
        if (url == null) return "";
        return url.replaceAll("(X-Plex-Token=)[^&]*", "$1REDACTED");
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;                                          // UTF-8 is always present
        }
    }
}
