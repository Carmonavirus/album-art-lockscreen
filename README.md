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

## Settings

- **Look**: the cover centred over a blurred copy of itself (closest to the iPhone look),
  or the cover cropped to fill the whole screen.
- **Darken**: dims the image so the clock and notifications stay readable.
- **Go back to normal after**: how long after the music stops before your normal lock
  screen comes back, so short pauses don't make it flash.

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
