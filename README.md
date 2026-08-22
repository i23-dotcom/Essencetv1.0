# IPTV Set-Top Player

A native Android M3U/IPTV player with set-top-box style features: live channel
grid, categories, favorites, search, EPG (XMLTV) "now/next" guide, remote
control (D-pad) friendly playback with channel up/down, Picture-in-Picture,
and Android TV / set-top box launcher support.

## Features

- **Playlist loading**: from a remote M3U/M3U8 URL, or a local file picked via
  the system file picker (Storage Access Framework).
- **Channel grid** grouped by `group-title`, with channel logos (`tvg-logo`).
- **Category tabs** (horizontal chip list) + **Favorites** tab + live search.
- **Live playback** via Media3 ExoPlayer — supports HLS (`.m3u8`), MPEG-TS,
  DASH, and most common IPTV stream formats.
- **Remote-control friendly player**: D-pad left/right/up/down and the
  hardware CHANNEL_UP / CHANNEL_DOWN keys cycle channels; center/enter toggles
  the on-screen overlay — built for set-top box / Android TV remotes as well
  as touch.
- **EPG (XMLTV)**: optional guide URL; shows "Now / Next" on the player
  overlay and a full TV Guide screen, matched via `tvg-id`.
- **Favorites**, long-press any channel tile (or tap the star in the player)
  to add/remove.
- **Aspect ratio toggle** (Fit / Zoom / Fill) and **Picture-in-Picture** mode.
- **Android TV / set-top box support**: the app appears on the Android TV
  home screen (`LEANBACK_LAUNCHER`) with a banner, and works fine on phones
  and tablets too — this is a universal Android app, not two separate builds.

## How to build the APK

### Option A — No PC, phone only (recommended if you don't have a computer)

This repo includes `.github/workflows/build-apk.yml`, which tells **GitHub's
free cloud servers** to compile the APK for you. You never install Android
Studio or an SDK — you just need to get these files into a GitHub repo from
your phone, then download the finished APK.

**1. Create a free GitHub account** (github.com, works fine in a phone
browser) if you don't have one.

**2. Get the project onto GitHub.** The easiest phone-only way is the
[Termux](https://f-droid.org/packages/com.termux/) app (Android; install it
from F-Droid, not the outdated Play Store version):

```bash
# Inside Termux, after installing:
pkg install git -y
termux-setup-storage
cd storage/downloads          # wherever you unzipped the project
cd m3u-iptv-player
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/<your-username>/<repo-name>.git
git push -u origin main
```

When `git push` asks for a password, use a **Personal Access Token**, not
your GitHub password: on github.com go to Settings → Developer settings →
Personal access tokens → Generate new token (classic), tick the `repo` scope,
and paste that token in as the password when prompted. Create the empty
repository first on github.com (New repository → don't initialize with a
README) so `origin` has somewhere to push to.

**3. Let it build.** As soon as you push, GitHub automatically starts the
build. On github.com, open your repo → the **Actions** tab → click the
running (or latest finished) workflow.

**4. Download the APK.** Once it finishes (a few minutes), scroll down to
**Artifacts** and tap `app-debug-apk` to download a zip containing
`app-debug.apk`. Unzip that on your phone and tap the APK to install it
(you may need to allow "install unknown apps" for your browser/file manager
in Android settings — Android will prompt you for this the first time).

*(If Termux feels like too much: any Android git client that can push a
local folder works the same way — e.g. MGit, or GitHub's own "Upload files"
web button, though that one struggles with nested folders on mobile
browsers, so Termux is the more reliable path.)*

### Option B — On a PC, with Android Studio

1. Unzip this project.
2. Open [Android Studio](https://developer.android.com/studio) → **Open** →
   select the unzipped `m3u-iptv-player` folder.
3. Let Gradle sync finish (first sync downloads dependencies).
4. To test on a device/emulator: click **Run ▶**.
5. To get an installable `.apk` file: **Build → Build Bundle(s) / APK(s) →
   Build APK(s)**. Find it at:
   `app/build/outputs/apk/debug/app-debug.apk`
6. For a signed release APK: **Build → Generate Signed Bundle / APK**,
   choose **APK**, and follow the wizard to create/select a keystore.

## First run

On first launch you'll be sent straight to **Settings**, where you enter:
- A playlist URL (e.g. `https://example.com/playlist.m3u`) **or** pick a
  local `.m3u`/`.m3u8` file, and
- Optionally, an XMLTV EPG URL (plain `.xml` or gzipped `.xml.gz`) to enable
  the program guide.

You can always get back to Settings from the gear icon on the main screen.

## Project structure

```
app/src/main/java/com/iptv/player/
  model/      Channel, EpgProgram data classes
  parser/     M3uParser, XmlTvParser
  data/       PlaylistRepository, EpgRepository, FavoritesManager, SettingsStore
  adapter/    RecyclerView adapters for the channel grid and category chips
  ui/         SplashActivity, MainActivity, PlayerActivity, SettingsActivity, EpgActivity
```

## Notes / things you may want to customize

- App id / package: `com.iptv.player` (change in `app/build.gradle.kts` →
  `namespace` / `applicationId` before publishing).
- Colors/branding live in `res/values/colors.xml`.
- The launcher icon and TV banner are simple placeholder vector graphics —
  swap in your own artwork via Android Studio's Image Asset tool
  (`res` → right-click → New → Image Asset) if you want a polished icon.
- `usesCleartextTraffic="true"` is enabled since many public IPTV playlists
  are served over plain `http://`. Remove it if all your streams use `https`.
