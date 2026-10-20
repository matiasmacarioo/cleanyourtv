package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class PlexItemTest {

    /**
     * The shape Plex 1.43 sends for an episode: the show's name is {@code grandparentTitle} and the
     * season/episode numbers live in {@code parentIndex}/{@code index}. Trimmed from a real reply.
     */
    private static final String EPISODE = "{\"MediaContainer\":{\"size\":1,\"Metadata\":[{"
            + "\"ratingKey\":\"228464\",\"type\":\"episode\",\"title\":\"Energia igual a poder\","
            + "\"grandparentTitle\":\"The Walking Dead: Dead City\",\"parentIndex\":2,\"index\":1,"
            + "\"duration\":3040352,\"viewOffset\":1029782,\"year\":2023,"
            + "\"thumb\":\"/library/metadata/228464/thumb/1792444653\","
            + "\"grandparentThumb\":\"/library/metadata/228446/thumb/1792444653\"}]}}";

    /** A movie: no show fields at all. */
    private static final String MOVIE = "{\"MediaContainer\":{\"size\":1,\"Metadata\":[{"
            + "\"ratingKey\":\"100\",\"type\":\"movie\",\"title\":\"Colony\","
            + "\"duration\":1000000,\"viewOffset\":14000,\"year\":2015,"
            + "\"thumb\":\"/library/metadata/100/thumb/1\"}]}}";

    @Test
    public void parsesAnEpisode() {
        List<PlexItem> items = PlexItem.parse(EPISODE);
        assertEquals(1, items.size());
        PlexItem e = items.get(0);
        assertEquals("228464", e.ratingKey);
        assertTrue(e.isEpisode());
        assertEquals("Energia igual a poder", e.title);
        assertEquals("The Walking Dead: Dead City", e.showTitle);
        assertEquals(2, e.season);
        assertEquals(1, e.episode);
        assertEquals(3040352L, e.duration);
        assertEquals(1029782L, e.viewOffset);
        assertEquals(2023, e.year);
    }

    @Test
    public void parsesAMovie() {
        List<PlexItem> items = PlexItem.parse(MOVIE);
        PlexItem m = items.get(0);
        assertTrue(!m.isEpisode());
        assertEquals("Colony", m.title);
        assertNull(m.showTitle);
        assertEquals("2015", m.subtitle());
    }

    /** An episode's second line is the show plus the season and episode, not a year. */
    @Test
    public void episodeSubtitleNamesTheShowAndEpisode() {
        assertEquals("The Walking Dead: Dead City · S2E1", PlexItem.parse(EPISODE).get(0).subtitle());
    }

    /** A special or an item Plex did not number still shows its show, just without S/E. */
    @Test
    public void episodeWithoutNumbersKeepsTheShowName() {
        String json = "{\"MediaContainer\":{\"Metadata\":[{"
                + "\"type\":\"episode\",\"title\":\"Pilot\",\"grandparentTitle\":\"Some Show\"}]}}";
        assertEquals("Some Show", PlexItem.parse(json).get(0).subtitle());
    }

    @Test
    public void progressIsTheFractionWatched() {
        // 1 029 782 of 3 040 352 ms.
        assertEquals(0.339f, PlexItem.parse(EPISODE).get(0).progress(), 0.001f);
    }

    /** No duration from Plex means no bar rather than a division by zero. */
    @Test
    public void progressIsZeroWithoutADuration() {
        String json = "{\"MediaContainer\":{\"Metadata\":[{"
                + "\"type\":\"movie\",\"title\":\"X\",\"viewOffset\":5000}]}}";
        assertEquals(0f, PlexItem.parse(json).get(0).progress(), 0f);
    }

    /** Finished or oddly-flagged items must not draw a bar past its end. */
    @Test
    public void progressIsClampedToOne() {
        String json = "{\"MediaContainer\":{\"Metadata\":[{"
                + "\"type\":\"movie\",\"title\":\"X\",\"duration\":1000,\"viewOffset\":999999}]}}";
        assertEquals(1f, PlexItem.parse(json).get(0).progress(), 0f);
    }

    @Test
    public void posterFallsBackToTheShowsArtwork() {
        assertEquals("/library/metadata/228464/thumb/1792444653",
                PlexItem.parse(EPISODE).get(0).posterPath());
        String showOnly = "{\"MediaContainer\":{\"Metadata\":[{"
                + "\"type\":\"episode\",\"title\":\"P\",\"grandparentThumb\":\"/show/thumb\"}]}}";
        assertEquals("/show/thumb", PlexItem.parse(showOnly).get(0).posterPath());
    }

    @Test
    public void anItemWithNoArtworkHasNoPoster() {
        String json = "{\"MediaContainer\":{\"Metadata\":[{\"type\":\"movie\",\"title\":\"Bare\"}]}}";
        assertNull(PlexItem.parse(json).get(0).posterPath());
    }

    /** Order is Plex's own: most recently watched first. */
    @Test
    public void keepsTheServersOrder() {
        String json = "{\"MediaContainer\":{\"Metadata\":["
                + "{\"type\":\"movie\",\"title\":\"First\"},"
                + "{\"type\":\"movie\",\"title\":\"Second\"}]}}";
        List<PlexItem> items = PlexItem.parse(json);
        assertEquals("First", items.get(0).title);
        assertEquals("Second", items.get(1).title);
    }

    /** An item with no name is dropped rather than rendered as a blank tile. */
    @Test
    public void skipsItemsWithoutATitle() {
        String json = "{\"MediaContainer\":{\"Metadata\":["
                + "{\"type\":\"movie\"},{\"type\":\"movie\",\"title\":\"Kept\"}]}}";
        List<PlexItem> items = PlexItem.parse(json);
        assertEquals(1, items.size());
        assertEquals("Kept", items.get(0).title);
    }

    /** The item's own key is kept, so a click can link back to it. */
    @Test
    public void keepsTheItemsKey() {
        String json = "{\"MediaContainer\":{\"Metadata\":[{"
                + "\"type\":\"movie\",\"title\":\"X\",\"key\":\"/library/metadata/100\"}]}}";
        assertEquals("/library/metadata/100", PlexItem.parse(json).get(0).key);
    }

    @Test
    public void missingKeyIsEmptyRatherThanNull() {
        String json = "{\"MediaContainer\":{\"Metadata\":[{\"type\":\"movie\",\"title\":\"X\"}]}}";
        assertEquals("", PlexItem.parse(json).get(0).key);
    }

    /** A sleeping server's error page, or any other junk, must not reach the UI as a crash. */
    @Test
    public void unparseableBodiesGiveAnEmptyList() {
        assertTrue(PlexItem.parse(null).isEmpty());
        assertTrue(PlexItem.parse("").isEmpty());
        assertTrue(PlexItem.parse("<html>not json</html>").isEmpty());
        assertTrue(PlexItem.parse("{}").isEmpty());
        assertTrue(PlexItem.parse("{\"MediaContainer\":{}}").isEmpty());
    }
}
