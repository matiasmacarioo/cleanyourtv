package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;

/**
 * The Intent that opens Plex.
 *
 * <p>It deliberately opens the app rather than an item. Plex for Android has no link shape this
 * launcher can use to open one:
 *
 * <ul>
 *   <li>Its {@code plex:} filter declares only the scheme, no authority and no path, and the shapes
 *       its own code looks for are {@code plex://watch/video?uri=...} and
 *       {@code plex://<serverId>/<type>/<key>}. Both reach Plex but land on its home screen instead
 *       of playing, so they look like they worked and did not.</li>
 *   <li>Its {@code http}/{@code https} filters list exactly {@code watch.plex.tv},
 *       {@code plex.smart.link}, {@code l.plex.tv}, {@code links.plex.tv} and {@code click.plex.tv}.
 *       {@code app.plex.tv}, the address Plex's own web app is served from, is not among them — and
 *       is not even owned by Plex here: on this TV it resolved to an unrelated downloader app.</li>
 * </ul>
 *
 * <p>So a card click opens Plex itself. That is a smaller promise than "resume this episode", but it
 * is one the app actually keeps, and the deck is already on the row above for choosing what to play.
 */
final class PlexPage {
    private PlexPage() {}

    /**
     * Opens the Plex app's own front screen. Null when Plex is not installed, which the caller treats
     * as a no-op rather than crashing on a card for an app that has been removed.
     */
    static Intent launchIntent(Context ctx) {
        Intent launch = ctx.getPackageManager()
                .getLeanbackLaunchIntentForPackage(ContinueWatchingDefaults.PLEX_PACKAGE);
        if (launch == null) {
            launch = ctx.getPackageManager()
                    .getLaunchIntentForPackage(ContinueWatchingDefaults.PLEX_PACKAGE);
        }
        if (launch != null) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return launch;
    }
}
