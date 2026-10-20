package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * The one part of the click path worth pinning down: which URL a card produces.
 *
 * <p>The shape checked here is exactly what was verified on the TV with
 * {@code cmd package query-activities} — every form below resolved to
 * {@code com.spotify.tv.android/.SpotifyTVActivity}. A change here is a change to what the device
 * will and will not open.
 */
public class SpotifyPageTest {

    private static SpotifyItem track() {
        return new SpotifyItem("4uLU6hMCjMI75M1A2tKUQC", "Never Gonna Give You Up", "Rick Astley",
                "spotify:track:4uLU6hMCjMI75M1A2tKUQC", "https://i.scdn.co/image/mid",
                213573L, 0L, 0L, false, "Whenever You Need Somebody");
    }

    private static SpotifyItem episode() {
        return new SpotifyItem("ep1", "Episode 42", "Some Podcast",
                "spotify:episode:ep1", "https://i.scdn.co/image/ep", 3600000L, 1800000L, 0L, true, null);
    }

    @Test
    public void aTrackLinksToItsTrackPath() {
        assertEquals("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", SpotifyPage.itemUrl(track()));
    }

    /** An episode must land on the episode path, not the track one; that segment is the whole point. */
    @Test
    public void anEpisodeLinksToItsEpisodePath() {
        assertEquals("https://open.spotify.com/episode/ep1", SpotifyPage.itemUrl(episode()));
    }

    /**
     * The id comes from the item's own {@code uri}, which is Spotify's authoritative form. Here the
     * {@code id} field disagrees, and the uri must win.
     */
    @Test
    public void theUriIdWinsOverTheBareId() {
        SpotifyItem wonky = new SpotifyItem("stale", "X", "Y",
                "spotify:track:authoritative", null, 0L, 0L, 0L, false, null);
        assertEquals("https://open.spotify.com/track/authoritative", SpotifyPage.itemUrl(wonky));
    }

    /** With no uri at all the bare id is used, so an item is still clickable. */
    @Test
    public void fallsBackToTheBareIdWhenThereIsNoUri() {
        SpotifyItem bare = new SpotifyItem("abc123", "X", "Y", "", null, 0L, 0L, 0L, false, null);
        assertEquals("https://open.spotify.com/track/abc123", SpotifyPage.itemUrl(bare));
    }

    /** No id anywhere means no link, which is what sends the click to the app's front screen instead. */
    @Test
    public void noIdMeansNoUrl() {
        SpotifyItem empty = new SpotifyItem("", "X", "Y", "", null, 0L, 0L, 0L, false, null);
        assertNull(SpotifyPage.itemUrl(empty));
        assertNull(SpotifyPage.itemUrl(null));
    }

    /** A malformed uri with a trailing colon must not produce a link with an empty id. */
    @Test
    public void aTrailingColonIsNotAnId() {
        SpotifyItem broken = new SpotifyItem("", "X", "Y", "spotify:track:", null, 0L, 0L, 0L, false, null);
        assertNull(SpotifyPage.itemUrl(broken));
    }
}
