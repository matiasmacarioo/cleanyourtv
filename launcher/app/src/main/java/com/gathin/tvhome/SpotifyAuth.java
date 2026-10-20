package com.gathin.tvhome;

import org.json.JSONObject;

/**
 * The Spotify credentials the row runs on, and the rules around refreshing them.
 *
 * <p>Spotify's Authorization Code with PKCE flow is the documented way for a client that cannot keep a
 * secret, which is what a sideloaded TV app is. The one-time login is done on a PC (see
 * {@code docs/spotify.md}); what lands on the TV is a {@code client_id} and a {@code refresh_token}.
 * No client secret is involved at any point, which is why this is a supported flow rather than a
 * workaround.
 *
 * <p>Two behaviours of the refresh token matter here and are easy to get wrong:
 *
 * <ul>
 *   <li><b>Refreshing can return a new refresh token.</b> Spotify says to keep using the existing one
 *       when the reply omits it, and to store the new one when it is present. {@link #withRefreshToken}
 *       exists for exactly that, because dropping a rotated token would silently break the row until
 *       the next manual login.</li>
 *   <li><b>A refresh token lasts 6 months and refreshing does not extend it.</b> After that the token
 *       endpoint answers 400 {@code invalid_grant} and the only fix is to log in again. {@link #expired}
 *       detects that case so the row can say so instead of looking broken.</li>
 * </ul>
 */
final class SpotifyAuth {
    /** The client id of the Spotify app the user registered; not a secret and safe to keep anywhere. */
    final String clientId;
    /** The long-lived token from the one-time login. Rotates; see the class note. */
    final String refreshToken;

    SpotifyAuth(String clientId, String refreshToken) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.refreshToken = refreshToken == null ? "" : refreshToken.trim();
    }

    /** True when both halves are present, so a refresh can be attempted at all. */
    boolean isConfigured() {
        return !clientId.isEmpty() && !refreshToken.isEmpty();
    }

    /** A copy carrying a refresh token Spotify just issued, leaving the client id alone. */
    SpotifyAuth withRefreshToken(String rotated) {
        if (rotated == null || rotated.isEmpty() || rotated.equals(refreshToken)) return this;
        return new SpotifyAuth(clientId, rotated);
    }

    /**
     * Whether a token-endpoint error body means the refresh token is dead and only a new login will
     * fix it. Spotify answers 400 with {@code {"error":"invalid_grant"}} in that case, and it says
     * explicitly not to retry, so the row stops trying and asks for a login rather than looping.
     */
    static boolean expired(int httpStatus, String body) {
        if (httpStatus != 400) return false;
        if (body == null || body.isEmpty()) return false;
        try {
            return "invalid_grant".equals(new JSONObject(body).optString("error", ""));
        } catch (Exception e) {
            return false;
        }
    }

    /** Never prints the token: this is what any diagnostic or log line uses. */
    @Override public String toString() {
        return "SpotifyAuth{clientId=" + (clientId.isEmpty() ? "" : "set")
                + ", refreshToken=" + (refreshToken.isEmpty() ? "" : "set") + "}";
    }
}
