# Spotify row

An optional shelf above the dock that shows what you played recently on Spotify, the same way the
Plex on-deck shelf shows what you were watching. It is off in the built-in configuration, so a
launcher built from this repository behaves exactly like before until a theme turns it on.

```json
{
  "spotify_row": true,
  "spotify_client_id": "your-client-id",
  "spotify_refresh_token": "your-refresh-token",
  "spotify_row_max": 8
}
```

Unlike the Plex row, it takes a one-time login on a computer first. Two of those four keys are a
credential; the same rule as the Plex token applies — keep them in the gitignored theme
`config.json`, never in `config.xml`, because a token in a build is a token in the repository.

## Why a login is needed

Spotify's recently-played list is an official, documented Web API endpoint
(`GET /me/player/recently-played`) and it needs the `user-read-recently-played` scope, which means an
access token for *your* account. Access tokens last an hour; refresh tokens last six months and are
what the TV keeps.

The launcher uses **Authorization Code with PKCE**, the flow Spotify documents for a client that
cannot hold a secret. A sideloaded TV app is exactly that case, so nothing here is a workaround and no
client secret is involved at any point.

## The one-time setup

**1. Register an app.** Go to [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard),
create an app (any name; "CleanYourTV" is fine), then open its settings.

**2. Add a redirect URI.** Any `http://127.0.0.1` value works, because the next step only reads it out
of the browser's address bar. Add:

```
http://127.0.0.1:8888/callback
```

**3. Do the login.** Open this URL in a browser, with your own client id substituted:

```
https://accounts.spotify.com/authorize?client_id=YOUR_CLIENT_ID&response_type=code&redirect_uri=http%3A%2F%2F127.0.0.1%3A8888%2Fcallback&scope=user-read-recently-played
```

Approve it. The browser lands on `127.0.0.1:8888` and fails to connect — that is expected and fine.
The address bar now reads:

```
http://127.0.0.1:8888/callback?code=AQD...xyz
```

Copy the `code` value.

> A code is single-use and expires in about ten minutes. If it is rejected, redo step 3; that is
> normal while getting the values right.

**4. Exchange it for a refresh token.** One command, with the code and your client id. This is the
only step that needs a terminal:

```bash
curl -X POST https://accounts.spotify.com/api/token \
  -d grant_type=authorization_code \
  -d code=THE_CODE_FROM_STEP_3 \
  -d redirect_uri=http://127.0.0.1:8888/callback \
  -d client_id=YOUR_CLIENT_ID
```

The reply contains `"refresh_token": "AQD..."`. That is the value for `spotify_refresh_token`.

**5. Push the theme:**

```bash
TV_HOST=192.168.1.50 tools/theme.sh themes/your-theme
```

Then focus the Spotify tile: the shelf appears above the dock, and D-pad Up enters it.

## What it draws

The same card as the Plex shelf — artwork, the track or show name under it, and a second line — with
one honest difference: **only podcast episodes get a progress bar.** Spotify does not report how far
into a *song* you were, so a track is shown as recently played rather than part-played. Drawing an
empty bar there would claim knowledge Spotify does not have.

Selecting a card opens the item in Spotify via `https://open.spotify.com/track/<id>` (or
`/episode/<id>`), a link shape the TV app's manifest declares explicitly. Verified on the device with
`cmd package query-activities`: every form resolves to `com.spotify.tv.android/.SpotifyTVActivity`.

## The six-month wall

Spotify expires refresh tokens **six months after they are issued**, and refreshing does not extend
that. When it happens the token endpoint answers `400 invalid_grant` and the row stops trying.

The row detects that specific reply rather than treating it as an outage, and the only fix is to
repeat the setup above. Budget for it roughly twice a year.

## Token rotation

Spotify may return a **new** refresh token on any refresh, and retires the old one when it does. The
launcher stores a replacement automatically, in the app's private data directory
(`files/spotify_refresh_token`), and prefers it over the theme's value from then on — the newer of the
two is the only one that works.

A rotation is a small frequent write, so it goes in its own file rather than being written back into
`config.json`. Rewriting the theme document from a background thread would risk half-writing the file
that describes the whole home screen, for no benefit.

## Cost

Two network calls at a time: one to refresh the access token (once an hour) and one for the list,
both on a single background thread shared with the cache. The access token is held in memory only and
is never written to disk. The sheet is cached the same way Plex's is, so an unreachable Spotify leaves
the row showing the last good list instead of nothing.

`docs/spotify.md` describes the only path that touches a credential; nothing in the launcher logs a
token, and `SpotifyUrls.redact` masks both the refresh token and the bearer header on any failure
line.

## Privacy and terms

One ToS note worth stating plainly. This uses your account, with your consent, through Spotify's own
documented API, for your own TV. Spotify's developer policy for streaming applications asks that
content not be commercialised, synchronised, or broadcast, and that audio stay in its original form —
none of which this does: the launcher draws a name, a picture and a link, and every stream is played
by the official Spotify app.
