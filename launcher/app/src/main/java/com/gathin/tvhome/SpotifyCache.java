package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * The last Spotify recently-played list, kept on disk so the row survives the TV coming up before the
 * network, and so a short Spotify outage does not empty the shelf.
 *
 * <p>Unlike the Plex cache, which stores the server's raw JSON, this one stores a rendered form of the
 * parsed items. Not for elegance: the Plex cache can replay its body verbatim because re-parsing is
 * free and lossless, but Spotify's reply is large and mostly fields the row never shows, and the one
 * thing worth keeping is the handful of strings and numbers the row actually draws. Writing those
 * directly keeps the file small and makes the offline row independent of Spotify's JSON shape.
 *
 * <p>Nothing here is a secret: an access token is never cached, and the refresh token lives in the
 * theme's config, not in this file.
 */
final class SpotifyCache {
    private static final String FILE_NAME = "spotify_recent.tsv";
    /** Tab-separated fields per line; tabs and newlines are impossible in Spotify's own strings. */
    private static final String SEP = "\t";

    private SpotifyCache() {}

    private static File file(File dir) {
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    /** Saves the items. Null or empty clears the cache instead. */
    static void write(File dir, List<SpotifyItem> items) {
        File f = file(dir);
        if (f == null) return;
        if (items == null || items.isEmpty()) {
            if (f.isFile()) f.delete();
            return;
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            for (SpotifyItem it : items) {
                w.write(clean(it.id) + SEP + clean(it.title) + SEP + clean(it.subtitle) + SEP
                        + clean(it.uri) + SEP + clean(it.imageUrl) + SEP
                        + it.durationMs + SEP + it.resumeMs + SEP + it.playedAtMs + SEP
                        + (it.episode ? "1" : "0") + SEP + clean(it.album) + "\n");
            }
        } catch (Exception e) {
            // Best effort: a missing cache only costs the offline row.
        }
    }

    /** The cached items, or an empty list when there is no cache or it is unreadable. */
    static List<SpotifyItem> read(File dir) {
        File f = file(dir);
        if (f == null || !f.isFile() || f.length() == 0) return Collections.emptyList();
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) Math.min(f.length(), 1 << 20)];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n <= 0) break;
                off += n;
            }
            return parseLines(new String(buf, 0, off, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static List<SpotifyItem> parseLines(String text) {
        List<SpotifyItem> out = new java.util.ArrayList<>();
        for (String line : text.split("\n")) {
            if (line.isEmpty()) continue;
            String[] p = line.split(SEP, -1);
            if (p.length < 10) continue;                       // a truncated line is skipped, not fatal
            try {
                out.add(new SpotifyItem(
                        p[0], p[1], p[2], p[3], emptyToNull(p[4]),
                        Long.parseLong(p[5]), Long.parseLong(p[6]), Long.parseLong(p[7]),
                        "1".equals(p[8]), emptyToNull(p[9])));
            } catch (NumberFormatException e) {
                // A single bad line should not discard the rest of the cache.
            }
        }
        return out;
    }

    static void delete(File dir) {
        File f = file(dir);
        if (f != null && f.isFile()) f.delete();
    }

    /** Tabs and newlines cannot appear inside the file, so they are replaced rather than escaped. */
    private static String clean(String s) {
        if (s == null) return "";
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
