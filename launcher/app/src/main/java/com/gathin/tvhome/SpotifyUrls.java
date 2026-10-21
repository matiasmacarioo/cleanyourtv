package com.gathin.tvhome;

import java.net.URLEncoder;

/**
 * The Spotify endpoints the row uses, built as plain strings so a test can pin them down.
 *
 * <p>All three are the documented Web API rather than anything reverse-engineered:
 * {@code accounts.spotify.com/api/token} for the refresh grant, and
 * {@code api.spotify.com/v1/me/player/recently-played} for the list. The recently-played endpoint
 * needs the {@code user-read-recently-played} scope, which the one-time login asks for.
 *
 * <p>{@link #redact} covers both secrets that can appear in a URL or header: the refresh token in a
 * form body and the short-lived access token in a bearer header. Neither should ever reach a log.
 */
final class SpotifyUrls {
    static final String TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token";
    static final String RECENTLY_PLAYED = "https://api.spotify.com/v1/me/player/recently-played";

    private SpotifyUrls() {}

    /**
     * The recently-played list, capped at {@code limit}. Spotify's own maximum is 50 and its default
     * is 20; the row asks for what it will actually show.
     */
    static String recentlyPlayed(int limit) {
        int n = limit < 1 ? 1 : Math.min(limit, 50);
        return RECENTLY_PLAYED + "?limit=" + n;
    }

    /**
     * The body of the refresh-token grant. Sent as {@code application/x-www-form-urlencoded}, which is
     * what the token endpoint requires. The client id is included because Spotify asks for it
     * explicitly on a PKCE refresh; there is no client secret, by design.
     */
    static String refreshBody(String clientId, String refreshToken) {
        return "grant_type=refresh_token"
                + "&client_id=" + encode(clientId)
                + "&refresh_token=" + encode(refreshToken);
    }

    /** The {@code Authorization} header value for a bearer token. */
    static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    /**
     * A string safe to log: strips a refresh token from a form body and a bearer token from a header,
     * leaving enough to tell which endpoint failed. The only way any of this should ever be printed.
     */
    static String redact(String s) {
        if (s == null) return "";
        String out = s.replaceAll("(refresh_token=)[^&\\s]*", "$1REDACTED");
        out = out.replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._~+/-]+", "$1REDACTED");
        return out;
    }

    /**
     * Percent-encodes a form value.
     *
     * <p>{@code URLEncoder.encode(String, Charset)} would be the obvious call, but it arrived in Java
     * 10 and Android 11 does not have it: the app compiles against a newer SDK and then dies at the
     * first Spotify fetch with {@code NoSuchMethodError}. The {@code String} charset overload is the
     * one every Android release has, so it is the one used here.
     */
    @SuppressWarnings("CharsetObjectCanBeUsed")
    private static String encode(String s) {
        if (s == null) return "";
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;   // UTF-8 is guaranteed present; this cannot happen
        }
    }
}
