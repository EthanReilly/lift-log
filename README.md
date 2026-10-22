# Lift Log

A workout tracker for Android and the web. It works fully offline, needs no account, and keeps your data on your phone.

- **Android app:** [download the latest APK](https://github.com/EthanReilly/lift-log/releases/latest/download/lift-log.apk)
- **Web version:** [ethanreilly.github.io/lift-log](https://ethanreilly.github.io/lift-log/). Install it from Chrome's menu with **Install app**.

## Features

### Logging
- Routines and empty workouts, with a set table showing what you did last time (tap it to copy)
- Set types: warm-up, working, drop and failure
- **Auto warm-up detection** marks light sets before your working sets as W
- **Warm-up calculator** adds warm-up sets worked out from your working weight (Short, Standard or Long steps)
- **Plate calculator** shows the plates for each side, for your bar and the plates your gym has
- **Supersets** link exercises, and the rest timer starts after the last one in the group
- **RPE** column to note how hard each set was
- **Hold and drag** an exercise name to reorder exercises in a workout or routine
- Rest timer with a **separate time per exercise**, adjustable while it runs
- **Stopwatch for timed sets** such as planks or runs. Tap to start, tap to stop, and the time is filled in and the set logged. You can still type times by hand.
- **Estimated finish time**, based on your own pace
- Personal records for heaviest weight, estimated 1RM and set volume
- **Built-in number pad** for weights, reps and times, with ±2.5 buttons, a plate calculator shortcut and **Log set** (can be turned off in Settings)
- **Replace exercise** opens a full list with the best matches first, search, muscle filters and a Create new exercise button. Your sets carry over.
- **Edit your own exercises** (name, muscles, equipment, logging type) from the exercise page or the ••• menu in a workout
- **Sticky notes** per exercise (seat height, grip) that show every time you do it
- **Edit past workouts**: change sets, names, start time and duration after the fact
- **Undo** after deleting a set, exercise, workout, routine, folder, goal or body entry

### Smart coaching (each can be switched off in Settings)
- **Progressive overload targets** suggest today's weight and reps from last time
- **Plateau alerts** flag lifts with no 1RM progress in 4 weeks and offer a deload week
- **Live PR banner** the moment a set beats your record
- **Goals**: a weekly workout target with a ring, plus lift (est. 1RM) and body weight goals

### Progress
- History list plus a **calendar** (week, month and year) with a weekly streak
- Progress chart and history for each exercise
- **Share card**: turn any workout into an image (dark, light or your own photo) to share or save
- **Muscle maps** (front and back body diagrams) for each exercise, each workout and each week, with a 10–20 sets per muscle guide
- Weekly charts of workouts, hours and volume
- **Body weight and measurements** (body fat, waist, chest, arms, thighs, hips, neck), each with a chart

### Organising
- **First-launch setup**: choose units, rest time and a starting program, or import from Strong or Hevy
- **Program templates:** Push Pull Legs, Upper / Lower, Full Body, Strength 5×5, PHUL, Bro Split and Beginner Full Body. Preview each day, then add a program as a folder of routines you can edit.
- **Routine folders**
- About 70 built-in exercises plus your own, each with primary and secondary muscles

### Android only
- **Now playing banner** for any music app, with play, pause and skip. Tap the song to open the app it's playing from.
- **Rest alarm** with volume, sound and vibration settings. It briefly pauses your music so you hear it, and it works with the screen off.
- **Weekly automatic backups** to a folder you choose, such as OneDrive, even when the app is closed. The last 8 are kept.
- **Lock-screen controls**: a notification with the current set, a rest countdown and **Done set**, **+15 s** and **Skip rest** buttons
- Share workout images straight to other apps
- Keeps the screen on during workouts, and the back button closes screens instead of quitting

## Your data

Everything stays on your device. There are no accounts, servers or tracking.

- **Back up now** (Profile → Backups) saves a `.json` file with your workouts, routines, settings and body measurements. **Restore** loads it back and replaces what's on the phone.
- **Import from Strong or Hevy** takes their CSV exports and adds the workouts to your history. Exercises are matched to the library. Strong files don't record whether weights are kg or lb, so the app asks.
- **Export workouts as CSV** for spreadsheets or other apps. Lift Log's own CSV can be imported again.

## Installing and updating on Android

1. Download `lift-log.apk` from the link above on your phone.
2. Open it and allow installs from your browser or file manager when Android asks.
3. Install new versions over the top. Your data is kept.

For lock-screen controls, allow notifications when Android asks (the first time you start a workout).

To use the now playing banner, allow **notification access** when asked. If Android says the setting is restricted, go to **Settings → Apps → Lift Log → ⋮ → Allow restricted settings** and try again. Android does this for apps installed outside the Play Store.

## Project layout

```
index.html              The whole app: layout, styles and code in one file
sw.js                   Offline support for the web version
manifest.webmanifest    Web app name and icons
icons/                  App icons
native/                 Android app (Capacitor wrapper around index.html)
  prepare-web.js        Copies index.html into the Android build
  android/app/src/main/java/com/ethanreilly/liftlog/
    LiftLogPlugin.java  Now playing, alarm, backups, workout notification, sharing, keep-awake and back button
    Alarm.java          Alarm sounds, vibration and pausing other audio
    BackupWriter.java   Writes backup files to the chosen folder
    BackupWorker.java   Weekly background backup
    MediaListener.java  Required for notification access (now playing)
    NotifReceiver.java  Handles the workout notification's buttons
  liftlog-release.p12   Signing key (encrypted)
.github/workflows/android.yml   Builds and publishes the APK
```

The web and Android versions share `index.html`. Android-only features switch themselves on when the app detects it is running natively.

## How builds work

- **Web:** GitHub Pages serves the repository root, so pushing to `main` updates the web version.
- **Android:** every push to `main` that changes `index.html`, `icons/`, `native/` or the workflow runs **Build Android app** in GitHub Actions. It:
  1. copies the web app into the Android project,
  2. builds a release APK signed with `native/liftlog-release.p12`,
  3. publishes it as a GitHub Release (`build-<number>`), keeping the 5 newest.

  The version number goes up automatically with each build, so updates always install over the previous one.

If a build fails, the last part of its log is saved to the `ci-logs` branch.

### Signing key

The key file is in the repo but encrypted. Its password is stored only in the Actions secret `LIFTLOG_KEYSTORE_PASSWORD` (Settings → Secrets and variables → Actions).

Keep a copy of that password somewhere safe. Every update must be signed with the same key, or Android will refuse to install it over the existing app, and the only way back is uninstalling, which deletes the data on the phone.

## Making changes

1. Edit `index.html`.
2. Open it in a browser to try it. Everything except the Android-only features works there.
3. Push to `main`. The web version updates and a new APK is built.

For native changes, edit the Java files under `native/android/app/src/main/java/com/ethanreilly/liftlog/` and push. GitHub builds it, so you don't need Android Studio.
