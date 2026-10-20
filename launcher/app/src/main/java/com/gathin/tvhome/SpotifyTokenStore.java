package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Where a rotated Spotify refresh token is kept.
 *
 * <p>Spotify may return a new refresh token on any token refresh. It is not optional to store it: the
 * old one is retired, so a row that drops the replacement dies at the next restart and looks like an
 * expired login.
 *
 * <p>Kept in its own file rather than written back into the theme's {@code config.json}, for two
 * reasons. The theme file is a user-facing document that the theme tooling rewrites wholesale, and a
 * stream of machine edits from a background thread would be a poor neighbour to that. And a rotation
 * is a small, frequent, best-effort write — exactly the thing that should not risk half-rewriting the
 * file that describes the whole home screen.
 *
 * <p>This file therefore holds a credential. It lives in the app's private data directory, which no
 * other app can read, and it is wiped along with the cache when Spotify is reconfigured.
 *
 * <p>The precedence is: the theme's {@code spotify_refresh_token}, unless a rotated one is on disk,
 * in which case the rotated one wins because it is the newer of the two. See
 * {@link #readIfPresent}.
 */
final class SpotifyTokenStore {
    private static final String TAG = "TvHome.Spotify";
    private static final String FILE_NAME = "spotify_refresh_token";

    private SpotifyTokenStore() {}

    private static File file(Context ctx) {
        File dir = Config.dir(ctx);
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    /**
     * Saves a refresh token Spotify just issued. Best-effort and never logged: a failure here means
     * the row keeps working until the TV restarts, at which point the theme's original token is used
     * again and may already have been retired.
     */
    static void save(Context ctx, String token) {
        if (token == null || token.isEmpty()) return;
        File f = file(ctx);
        if (f == null) return;
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "could not persist a rotated refresh token: " + e);
        }
    }

    /** The rotated token, or null when none has been issued. */
    static String readIfPresent(Context ctx) {
        File f = file(ctx);
        if (f == null || !f.isFile() || f.length() == 0) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) Math.min(f.length(), 4096)];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n <= 0) break;
                off += n;
            }
            String token = new String(buf, 0, off, StandardCharsets.UTF_8).trim();
            return token.isEmpty() ? null : token;
        } catch (Exception e) {
            return null;
        }
    }

    /** Removes the stored token; used when Spotify is reconfigured or disconnected. */
    static void clear(Context ctx) {
        File f = file(ctx);
        if (f != null && f.isFile()) f.delete();
    }
}
