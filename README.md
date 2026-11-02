# Album Art Lock Screen

Brings the iPhone (iOS 16+) "full-screen album art on the lock screen" feature to Android.
While music plays in YouTube Music (or any other player), the song's cover becomes your
lock screen wallpaper. When the music stops, your normal lock screen comes back.

Made for a Samsung Galaxy S23, but should work on any phone running Android 10 or newer.

## Install

1. On your phone, open
   **https://github.com/Carmonavirus/album-art-lockscreen/releases/download/latest/AlbumArtLockScreen.apk**
2. Open the downloaded file. Android will ask you to allow installs from your browser (or
   My Files): allow it, go back, and tap **Install**.
3. Open **Album Art Lock Screen** and tap **Open notification access settings**. Turn on
   *Album Art Lock Screen* and confirm. (This is how Android lets apps see what's playing;
   the app does not read your notifications.)
4. Optional: pick the image you want as your normal lock screen when no music is playing.
5. Play a song, lock your phone, and wake it.

To update, download and install the APK from the same link again. Your settings are kept.

## Quick on/off

Add the **Album art** tile to your quick settings panel: tap **Add on/off tile to quick
settings** in the app, or swipe down twice, tap the pencil (edit) button and drag the tile in.
It works from the lock screen too. Turning it off puts your normal lock screen back right away.

## Settings

The app has a customization screen with a live preview (using the last song that played):

- **Layout**: a rounded cover you can move and resize, or the cover cropped to fill the screen.
- **Background**: the cover's colour on a dark gradient (like Samsung's expanded player),
  a blurred copy of the cover, or black.
- **Cover**: position from the top, size and corner roundness.
- **Text under the cover**: song title, artist and album, each on or off, plus text size.
- **Darken**: dims the background so the clock stays readable.
- **Reset to Samsung look** puts everything back to the defaults, which match the expanded
  lock screen player on a Galaxy S23.
- **Timing**: how long after the music stops before your normal lock screen comes back.

Music controls aren't drawn on the wallpaper because a wallpaper is a still image and
can't be tapped. Samsung's own lock screen player keeps working on top of it.

## Good to know

- Android doesn't let apps read your current lock screen image. If you don't pick one in
  the app, the lock screen falls back to your home screen wallpaper when the music stops.
- If covers stop updating after a while, set the app's battery usage to **Unrestricted**
  (Settings > Apps > Album Art Lock Screen > Battery).
- If Samsung's lock screen shows a wallpaper slideshow or a "Dynamic lock screen", turn
  that off, since it will replace the cover.

## Building

GitHub Actions builds the APK on every push to `main` (see `.github/workflows/build.yml`)
and publishes it to the `latest` release. Locally: `./gradlew assembleRelease`.
