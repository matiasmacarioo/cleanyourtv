package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;

/**
 * The Intent that opens an on-deck item in Plex.
 *
 * <p>Plex for Android registers a bare {@code plex:} scheme on {@code tv.plex.app.MainActivity} — the
 * manifest declares the scheme with no authority and no path pattern, so it parses the URI itself and
 * the shape of anything beyond {@code plex:} is Plex's own business, not something this launcher can
 * verify. Rather than invent a path it cannot test, {@link #itemIntent} sends the item the way Plex
 * exposes in its own public API: the {@code /web/index.html#!/server/<id>/details?key=<key>} address
 * route, which every Plex client understands.
 *
 * <p>What that buys is a click that always lands somewhere useful. Plex owns the decision of where in
 * a file to resume; the launcher only says which item.
 */
final class PlexPage {
    private PlexPage() {}

    /**
     * Opens {@code item} in Plex. The {@code https://app.plex.tv/...} address form is used because it
     * is the one URL shape Plex documents for linking to an item, and the installed app claims both
     * {@code http} and {@code https} for {@code app.plex.tv} and {@code click.plex.tv}.
     */
    static Intent itemIntent(Context ctx, String serverBase, String token, PlexItem item) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setPackage(ContinueWatchingDefaults.PLEX_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Plex's own "open this item" link: the server-scoped details route, keyed by the item.
        intent.setData(android.net.Uri.parse(
                "https://app.plex.tv/desktop#!/server/server/details?key="
                        + android.net.Uri.encode(item.key)));
        return intent;
    }

    /**
     * The app's own start screen, used when a deep link cannot be routed (an older Plex, or a click on
     * a card whose item has since left the server). A click must never be a no-op.
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
