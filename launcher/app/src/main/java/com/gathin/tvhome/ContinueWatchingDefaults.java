package com.gathin.tvhome;

/**
 * The defaults the Continue Watching row needs from the app rather than the theme.
 *
 * <p>Keeping the Plex package name here (instead of inline in MainActivity) means the one thing that
 * would change if a fork targeted Plex's other builds is a single constant.
 */
final class ContinueWatchingDefaults {
    /** Plex for Android on TV, the package the dock lists in every shipped theme. */
    static final String PLEX_PACKAGE = "com.plexapp.android";

    private ContinueWatchingDefaults() {}
}
