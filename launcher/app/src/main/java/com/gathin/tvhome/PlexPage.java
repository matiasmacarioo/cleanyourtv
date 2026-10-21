package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/**
 * The Intents that open a Plex client, and that open one item directly where the client allows it.
 *
 * <p>Plex for Android has no link shape this launcher can use to open an item:
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
 * <p>Plezy does have one, so when the theme names it a card click opens the item itself and playback
 * begins. Under Plex the click still opens the app, which is a smaller promise but one it keeps; the
 * deck is on the row above either way for choosing what to play.
 */
final class PlexPage {
    private PlexPage() {}

    /** The Plezy client, the only one whose link shape opens a given item. */
    static final String PLEZY_PACKAGE = "com.edde746.plezy";

    /**
     * The Intent for a tap on a Continue Watching card: the item itself where {@code link} is
     * usable, otherwise the client's front screen.
     *
     * <p>The link is only sent when Plezy is the configured client and something on the device
     * resolves it, so a leftover link cannot fire an {@code ActivityNotFoundException}` after Plezy
     * is uninstalled. Every other client, and every failure to build a link, opens the app by
     * package instead — a smaller promise, but one Plex for Android actually keeps.
     */
    static Intent cardIntent(Context ctx, String pkg, String link) {
        if (link != null && !link.isEmpty() && PLEZY_PACKAGE.equals(pkg)) {
            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (view.resolveActivity(ctx.getPackageManager()) != null) return view;
        }
        return launchIntent(ctx, pkg);
    }

    /**
     * Opens the client's own front screen. Null when that app is not installed, which the caller
     * treats as a no-op rather than crashing on a card for an app that has been removed.
     *
     * <p>{@code pkg} is the client to open: Plex for Android by default, or whichever app the theme
     * set as {@code plex_app}. Plezy and Plex read the same server, so the row and the click follow
     * the same key.
     */
    static Intent launchIntent(Context ctx, String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        Intent launch = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) {
            launch = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        }
        if (launch != null) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return launch;
    }
}
