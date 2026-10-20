package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class SpotifyItemTest {

    /**
     * A real reply shape: each entry wraps the track under "track" and adds a "played_at" alongside it.
     * Trimmed from Spotify's Web API documentation example.
     */
    private static final String TRACK = "{\"items\":[{\"played_at\":\"2026-10-20T19:04:05.123Z\","
            + "\"track\":{\"id\":\"4uLU6hMCjMI75M1A2tKUQC\",\"name\":\"Never Gonna Give You Up\","
            + "\"uri\":\"spotify:track:4uLU6hMCjMI75M1A2tKUQC\",\"duration_ms\":213573,"
            + "\"artists\":[{\"name\":\"Rick Astley\"}],"
            + "\"album\":{\"name\":\"Whenever You Need Somebody\","
            + "\"images\":[{\"url\":\"https://i.scdn.co/image/big\"},"
            + "{\"url\":\"https://i.scdn.co/image/mid\"},"
            + "{\"url\":\"https://i.scdn.co/image/small\"}]}}}]}";

    /** A podcast episode: no artists, but a show and a resume_point. */
    private static final String EPISODE = "{\"items\":[{\"played_at\":\"2026-10-20T18:00:00Z\","
            + "\"episode\":{\"id\":\"ep1\",\"name\":\"Episode 42\",\"uri\":\"spotify:episode:ep1\","
            + "\"duration_ms\":3600000,"
            + "\"resume_point\":{\"resume_position_ms\":1800000,\"fully_played\":false},"
            + "\"show\":{\"name\":\"Some Podcast\",\"images\":[{\"url\":\"https://i.scdn.co/image/ep\"}]},"
            + "\"images\":[{\"url\":\"https://i.scdn.co/image/ep\"}]}}]}";

    @Test
    public void parsesATrack() {
        List<SpotifyItem> items = SpotifyItem.parse(TRACK);
        assertEquals(1, items.size());
        SpotifyItem t = items.get(0);
        assertEquals("4uLU6hMCjMI75M1A2tKUQC", t.id);
        assertEquals("Never Gonna Give You Up", t.title);
        assertEquals("Rick Astley", t.subtitle);
        assertEquals("spotify:track:4uLU6hMCjMI75M1A2tKUQC", t.uri);
        assertEquals(213573L, t.durationMs);
        assertEquals("Whenever You Need Somebody", t.album);
        assertFalse(t.isEpisode());
    }

    @Test
    public void parsesAnEpisode() {
        SpotifyItem e = SpotifyItem.parse(EPISODE).get(0);
        assertTrue(e.isEpisode());
        assertEquals("Episode 42", e.title);
        assertEquals("Some Podcast", e.subtitle);
        assertEquals(3600000L, e.durationMs);
        assertEquals(1800000L, e.resumeMs);
    }

    /** Multiple artists read the way Spotify writes them. */
    @Test
    public void joinsSeveralArtists() {
        String json = "{\"items\":[{\"track\":{\"name\":\"X\",\"artists\":["
                + "{\"name\":\"A\"},{\"name\":\"B\"},{\"name\":\"C\"}]}}]}";
        assertEquals("A, B, C", SpotifyItem.parse(json).get(0).subtitle);
    }

    /** A track has no resume position, so it must not draw an empty progress bar. */
    @Test
    public void aTrackHasNoProgress() {
        SpotifyItem t = SpotifyItem.parse(TRACK).get(0);
        assertFalse(t.hasProgress());
        assertEquals(0f, t.progress(), 0f);
    }

    /** An episode midway through is half done. */
    @Test
    public void anEpisodeMidwayIsHalfDone() {
        SpotifyItem e = SpotifyItem.parse(EPISODE).get(0);
        assertTrue(e.hasProgress());
        assertEquals(0.5f, e.progress(), 0.001f);
    }

    /** A finished episode must not draw past the end of the bar. */
    @Test
    public void progressIsClampedToOne() {
        String json = "{\"items\":[{\"episode\":{\"name\":\"E\",\"duration_ms\":1000,"
                + "\"resume_point\":{\"resume_position_ms\":99999}}}]}";
        assertEquals(1f, SpotifyItem.parse(json).get(0).progress(), 0f);
    }

    /** The middle image is chosen: the largest is wasteful on a TV and the smallest looks soft. */
    @Test
    public void picksTheMiddleArtwork() {
        assertEquals("https://i.scdn.co/image/mid", SpotifyItem.parse(TRACK).get(0).imageUrl);
    }

    @Test
    public void fallsBackToTheOnlyArtwork() {
        assertEquals("https://i.scdn.co/image/ep", SpotifyItem.parse(EPISODE).get(0).imageUrl);
    }

    @Test
    public void anItemWithNoArtworkHasNoImage() {
        String json = "{\"items\":[{\"track\":{\"name\":\"Bare\"}}]}";
        assertNull(SpotifyItem.parse(json).get(0).imageUrl);
    }

    /** Spotify's timestamp is kept, so the row could show when something was played. */
    @Test
    public void keepsThePlayedAtTime() {
        long t = SpotifyItem.parse(TRACK).get(0).playedAtMs;
        assertTrue("expected a parsed epoch time, got " + t, t > 1_700_000_000_000L);
    }

    /** Fractional seconds are optional, and both shapes must parse. */
    @Test
    public void playedAtWithoutFractionalSecondsStillParses() {
        assertTrue(SpotifyItem.parse(EPISODE).get(0).playedAtMs > 1_700_000_000_000L);
    }

    /** A missing uri is rebuilt from the id, so a click still has something to open. */
    @Test
    public void rebuildsAMissingUriFromTheId() {
        String json = "{\"items\":[{\"track\":{\"id\":\"abc\",\"name\":\"X\"}}]}";
        assertEquals("spotify:track:abc", SpotifyItem.parse(json).get(0).uri);
        String ep = "{\"items\":[{\"episode\":{\"id\":\"e1\",\"name\":\"X\"}}]}";
        assertEquals("spotify:episode:e1", SpotifyItem.parse(ep).get(0).uri);
    }

    /** An entry with no name is dropped rather than rendered blank. */
    @Test
    public void skipsEntriesWithoutAName() {
        String json = "{\"items\":[{\"track\":{\"id\":\"a\"}},"
                + "{\"track\":{\"name\":\"Kept\"}}]}";
        List<SpotifyItem> items = SpotifyItem.parse(json);
        assertEquals(1, items.size());
        assertEquals("Kept", items.get(0).title);
    }

    @Test
    public void keepsSpotifysOrder() {
        String json = "{\"items\":[{\"track\":{\"name\":\"First\"}},"
                + "{\"track\":{\"name\":\"Second\"}}]}";
        List<SpotifyItem> items = SpotifyItem.parse(json);
        assertEquals("First", items.get(0).title);
        assertEquals("Second", items.get(1).title);
    }

    /** An error body or junk must reach the UI as "no row", never as a crash. */
    @Test
    public void unparseableBodiesGiveAnEmptyList() {
        assertTrue(SpotifyItem.parse(null).isEmpty());
        assertTrue(SpotifyItem.parse("").isEmpty());
        assertTrue(SpotifyItem.parse("<html>not json</html>").isEmpty());
        assertTrue(SpotifyItem.parse("{}").isEmpty());
        assertTrue(SpotifyItem.parse("{\"error\":{\"status\":401}}").isEmpty());
    }
}
