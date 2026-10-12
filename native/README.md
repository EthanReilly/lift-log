# Lift Log for Android

This folder wraps the web app in `../index.html` as an Android app using Capacitor.

- `prepare-web.js` copies the web app into `www/` and adds `capacitor.js`.
- `android/app/src/main/java/com/ethanreilly/liftlog/` holds the native code:
  now playing for any music app, the rest alarm (volume, vibration, pausing other audio),
  weekly backups to a chosen folder, keep-screen-on and the back button.
- GitHub Actions (`.github/workflows/android.yml`) builds a signed APK on every push to `main`
  and publishes it at https://github.com/EthanReilly/lift-log/releases/latest/download/lift-log.apk

The signing key `liftlog-release.p12` is encrypted. Its password lives only in the
`LIFTLOG_KEYSTORE_PASSWORD` Actions secret. Keep a copy of that password: every update
must be signed with the same key or Android will refuse to install it over the old version.

