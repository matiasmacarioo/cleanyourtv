# Continue Watching row

An optional shelf above the dock that shows the Plex server's on-deck list — what you were watching,
with a progress bar under each poster. It is off in the built-in configuration, so a launcher built
from this repository behaves exactly like before until a theme turns it on.

```json
{
  "continue_watching": true,
  "plex_app": "com.edde746.plezy",
  "plex_url": "192.168.120.230",
  "plex_token": "your-token",
  "continue_watching_max": 8
}
```

`plex_url` and `plex_token` are the server and its credential. `plex_token` is a secret: keep it in
the gitignored theme `config.json`, never in `config.xml`, because a token in a build is a token in
the repository. Revoke it under plex.tv → Settings → Authorized Devices if it ever leaks.

## Which app the row belongs to

The row hangs off a **dock tile**, so `plex_app` names the client it appears under. It is also the app
a card click opens. It defaults to Plex for Android; set it to `com.edde746.plezy` if you run
[Plezy](https://github.com/edde746/plezy) instead.

The row only shows while that tile has focus, and it disappears entirely if the package named is not
in `dock_apps`. Nothing else needs changing: both clients read the same server, so the same `plex_url`
and `plex_token` serve either.

## What a card click does

It depends on the client, because only one of them can be asked to open a specific item.

**Plezy** has a working deep link, so a click **opens that item and starts playing it**, resuming from
the saved position. The link is:

```
plezy://play?content_id=plezy_<machineIdentifier>_<ratingKey>
```

Plezy splits that back into the server and the item, fetches the item, and begins playback. The
launcher builds it from the item's `ratingKey` and the server's `machineIdentifier`, which it reads
from `/identity` and caches. If either is missing — the server was asleep, say — the click falls back
to opening the app rather than sending a link that cannot work.

**Plex for Android** has no equivalent link. Its `plex://` filters declare only the scheme, and the
shapes its own code matches (`plex://watch/video?uri=…`, `plex://<serverId>/<type>/<key>`) both land on
the app's home screen instead of playing. Its `http`/`https` filters cover only `watch.plex.tv`,
`plex.smart.link`, `l.plex.tv`, `links.plex.tv` and `click.plex.tv` — not `app.plex.tv`, which on one
TV here resolved to an unrelated downloader app. So under Plex a click opens the app itself: a
smaller promise, but one it actually keeps.

## How it fetches

`/library/onDeck`, over a plain `HttpURLConnection` — the launcher ships no networking library. Only
when the tile takes focus, with a one-minute throttle, and never on the main thread. The last good
reply is cached to the TV, so the row still fills in when the server is asleep; an empty result with
no cache leaves the row hidden.
