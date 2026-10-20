package com.gathin.tvhome;

/**
 * One card in a hero shelf above the dock, whichever service it came from.
 *
 * <p>Plex's on-deck list and Spotify's recently-played list describe very different things — a
 * half-watched episode and a song you played this morning — but the shelf draws exactly the same
 * four things for both: a title, a second line, some artwork, and optionally a progress bar. This
 * interface is that overlap and nothing more, so {@link HeroRow} can drive either list
 * without knowing which service it is showing.
 *
 * <p>Deliberately narrow: no ids, no URIs, no timestamps. Anything a specific service needs for its
 * own click handling stays on the concrete type, because the row never looks at it. That keeps the
 * row honest — it can only draw, never reason about, what it is given.
 */
interface RowItem {
    /** The headline: the episode name, the movie title, the track name. */
    String rowTitle();

    /** The second line, or empty for none. Never null. */
    String rowSubtitle();

    /** How far in, 0..1, for the progress bar. */
    float rowProgress();

    /** True when {@link #rowProgress()} is meaningful, so a played-to-the-end song draws no bar. */
    boolean rowHasProgress();

    /**
     * The artwork URL, absolute and ready to fetch, or null when there is none. Each service hands
     * back whatever form it uses — Plex a server-relative path it must be asked for, Spotify a full
     * {@code https://i.scdn.co/...} link — and the row's loader is what knows the difference.
     */
    String rowImageUrl();
}
