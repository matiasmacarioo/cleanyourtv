package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The last on-deck reply, kept on disk so the row still shows when the Plex server is asleep, the
 * laptop is shut, or the TV came up before the network did.
 *
 * <p>Stores the server's raw JSON rather than a serialised model: re-parsing it through
 * {@link PlexItem#parse} costs nothing and means the cache can never disagree with a live reply.
 *
 * <p>Deliberately not in the launcher's config directory, which {@code tools/theme.sh} wipes on every
 * theme push. A write is best-effort; a cache that cannot be written simply means no offline row.
 */
final class PlexCache {
    private static final String FILE_NAME = "plex_on_deck.json";

    private PlexCache() {}

    private static File file(File dir) {
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    /** Saves the raw on-deck JSON. A null or empty body clears the cache instead. */
    static void write(File dir, String json) {
        File f = file(dir);
        if (f == null) return;
        if (json == null || json.isEmpty()) {
            delete(dir);
            return;
        }
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Best effort: a missing cache only costs the offline row.
        }
    }

    /** The cached JSON, or null when there is none. */
    static String read(File dir) {
        File f = file(dir);
        if (f == null || !f.isFile() || f.length() == 0) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0;
            while (off < buf.length) {
                int n = in.read(buf, off, buf.length - off);
                if (n <= 0) break;
                off += n;
            }
            return new String(buf, 0, off, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    static void delete(File dir) {
        File f = file(dir);
        if (f != null && f.isFile()) f.delete();
    }
}
