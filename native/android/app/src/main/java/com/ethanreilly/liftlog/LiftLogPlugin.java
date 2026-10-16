package com.ethanreilly.liftlog;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.graphics.drawable.Icon;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Base64;
import android.view.WindowManager;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.core.app.NotificationManagerCompat;
import androidx.documentfile.provider.DocumentFile;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CapacitorPlugin(name = "LiftLog")
public class LiftLogPlugin extends Plugin {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable restRunnable;
    private PowerManager.WakeLock restLock;
    private String pendingExport;
    private boolean pendingExportB64;
    static LiftLogPlugin instance;
    private static final String WORKOUT_CHANNEL = "workout";
    static final int WORKOUT_NOTIF = 4101;

    private static final Map<String, String> KNOWN_APPS = new HashMap<>();

    static {
        KNOWN_APPS.put("com.spotify.music", "Spotify");
        KNOWN_APPS.put("com.google.android.apps.youtube.music", "YouTube Music");
        KNOWN_APPS.put("com.google.android.youtube", "YouTube");
        KNOWN_APPS.put("com.amazon.mp3", "Amazon Music");
        KNOWN_APPS.put("com.apple.android.music", "Apple Music");
        KNOWN_APPS.put("com.soundcloud.android", "SoundCloud");
        KNOWN_APPS.put("deezer.android.app", "Deezer");
        KNOWN_APPS.put("com.aspiro.tidal", "TIDAL");
        KNOWN_APPS.put("au.com.shiftyjelly.pocketcasts", "Pocket Casts");
    }

    @Override
    public void load() {
        instance = this;
        getActivity()
            .getOnBackPressedDispatcher()
            .addCallback(
                getActivity(),
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        notifyListeners("back", new JSObject());
                    }
                }
            );
        SharedPreferences p = BackupWriter.prefs(getContext());
        if (p.getString("folderUri", null) != null && p.getBoolean("weekly", true)) {
            try {
                BackupWorker.schedule(getContext(), true);
            } catch (Exception ignored) {}
        }
    }

    /* ---------------- app / window ---------------- */

    @PluginMethod
    public void minimize(PluginCall call) {
        getActivity().runOnUiThread(() -> getActivity().moveTaskToBack(true));
        call.resolve();
    }

    @PluginMethod
    public void keepAwake(PluginCall call) {
        final boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        getActivity()
            .runOnUiThread(() -> {
                if (on) getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        call.resolve();
    }

    /* ---------------- now playing ---------------- */

    private boolean hasMediaAccess() {
        Context ctx = getContext();
        return NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.getPackageName());
    }

    private MediaController currentController() {
        Context ctx = getContext();
        MediaSessionManager msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (msm == null) return null;
        List<MediaController> list = msm.getActiveSessions(new ComponentName(ctx, MediaListener.class));
        if (list == null || list.isEmpty()) return null;
        for (MediaController c : list) {
            PlaybackState ps = c.getPlaybackState();
            if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) return c;
        }
        return list.get(0);
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getContext().getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            String k = KNOWN_APPS.get(pkg);
            return k != null ? k : "";
        }
    }

    private String toDataUrl(Bitmap bm) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && bm.getConfig() == Bitmap.Config.HARDWARE) {
                bm = bm.copy(Bitmap.Config.ARGB_8888, false);
            }
            int max = 160;
            int w = bm.getWidth(), h = bm.getHeight();
            if (w > max || h > max) {
                float s = Math.min((float) max / w, (float) max / h);
                bm = Bitmap.createScaledBitmap(bm, Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s)), true);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 82, bos);
            return "data:image/jpeg;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonEmpty(String... v) {
        for (String s : v) if (s != null && !s.trim().isEmpty()) return s;
        return null;
    }

    @PluginMethod
    public void nowPlaying(PluginCall call) {
        JSObject r = new JSObject();
        boolean granted = hasMediaAccess();
        r.put("granted", granted);
        if (!granted) {
            call.resolve(r);
            return;
        }
        try {
            MediaController c = currentController();
            if (c != null) {
                PlaybackState ps = c.getPlaybackState();
                r.put("playing", ps != null && ps.getState() == PlaybackState.STATE_PLAYING);
                r.put("app", appLabel(c.getPackageName()));
                MediaMetadata md = c.getMetadata();
                if (md != null) {
                    String title = firstNonEmpty(md.getString(MediaMetadata.METADATA_KEY_TITLE), md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
                    String artist = firstNonEmpty(
                        md.getString(MediaMetadata.METADATA_KEY_ARTIST),
                        md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                        md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                    );
                    if (title != null) r.put("title", title);
                    if (artist != null) r.put("artist", artist);
                    String artKey = (title == null ? "" : title) + "|" + (artist == null ? "" : artist) + "|" + c.getPackageName();
                    r.put("artKey", artKey);
                    if (!artKey.equals(call.getString("artKey", ""))) {
                        Bitmap bm = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                        if (bm == null) bm = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
                        if (bm == null) bm = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                        if (bm != null) {
                            String url = toDataUrl(bm);
                            if (url != null) r.put("art", url);
                        }
                    }
                }
            }
        } catch (SecurityException e) {
            r.put("granted", false);
        } catch (Exception e) {
            r.put("error", String.valueOf(e.getMessage()));
        }
        call.resolve(r);
    }

    @PluginMethod
    public void mediaControl(PluginCall call) {
        String action = call.getString("action", "");
        try {
            MediaController c = currentController();
            if (c == null) {
                call.reject("Nothing is playing");
                return;
            }
            MediaController.TransportControls tc = c.getTransportControls();
            switch (action) {
                case "play":
                    tc.play();
                    break;
                case "pause":
                    tc.pause();
                    break;
                case "next":
                    tc.skipToNext();
                    break;
                case "previous":
                    tc.skipToPrevious();
                    break;
                default:
                    call.reject("Unknown action");
                    return;
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    /** Opens the app that is playing, at its player screen when the app provides one. */
    @PluginMethod
    public void openPlayer(PluginCall call) {
        try {
            MediaController c = currentController();
            if (c == null) {
                call.reject("Nothing is playing");
                return;
            }
            PendingIntent pi = c.getSessionActivity();
            if (pi != null) {
                try {
                    if (Build.VERSION.SDK_INT >= 34) {
                        ActivityOptions o = ActivityOptions.makeBasic();
                        o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                        pi.send(getContext(), 0, null, null, null, null, o.toBundle());
                    } else {
                        pi.send();
                    }
                    call.resolve();
                    return;
                } catch (Exception ignored) {}
            }
            Intent li = getContext().getPackageManager().getLaunchIntentForPackage(c.getPackageName());
            if (li == null) {
                call.reject("Could not open the music app");
                return;
            }
            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(li);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void openMediaSettings(PluginCall call) {
        Context ctx = getContext();
        Intent i;
        if (Build.VERSION.SDK_INT >= 30) {
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            i.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, new ComponentName(ctx, MediaListener.class).flattenToString());
        } else {
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getActivity().startActivity(i);
        } catch (Exception e) {
            Intent fallback = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(fallback);
        }
        call.resolve();
    }

    /* ---------------- rest alarm ---------------- */

    private void playFromCall(PluginCall call) {
        Alarm.play(
            getContext(),
            call.getString("sound", "beep"),
            call.getInt("volume", 80),
            Boolean.TRUE.equals(call.getBoolean("vibrate", true)),
            Boolean.TRUE.equals(call.getBoolean("duck", true)),
            Boolean.TRUE.equals(call.getBoolean("alarmStream", true))
        );
    }

    @PluginMethod
    public void playAlarm(PluginCall call) {
        playFromCall(call);
        call.resolve();
    }

    private void clearRest() {
        if (restRunnable != null) handler.removeCallbacks(restRunnable);
        restRunnable = null;
        if (restLock != null && restLock.isHeld()) {
            try {
                restLock.release();
            } catch (Exception ignored) {}
        }
        restLock = null;
    }

    @PluginMethod
    public void scheduleRest(PluginCall call) {
        clearRest();
        int seconds = Math.max(1, call.getInt("seconds", 90));
        final String sound = call.getString("sound", "beep");
        final int volume = call.getInt("volume", 80);
        final boolean vibrate = Boolean.TRUE.equals(call.getBoolean("vibrate", true));
        final boolean duck = Boolean.TRUE.equals(call.getBoolean("duck", true));
        final boolean alarmStream = Boolean.TRUE.equals(call.getBoolean("alarmStream", true));
        try {
            PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
            restLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LiftLog:rest");
            restLock.acquire((seconds + 20) * 1000L);
        } catch (Exception ignored) {}
        final PowerManager.WakeLock lock = restLock;
        restRunnable = () -> {
            Alarm.play(getContext(), sound, volume, vibrate, duck, alarmStream);
            // keep the CPU awake while the sound plays, then let go of this rest's lock only
            handler.postDelayed(() -> {
                try {
                    if (lock != null && lock.isHeld()) lock.release();
                } catch (Exception ignored) {}
            }, 4000);
        };
        handler.postDelayed(restRunnable, seconds * 1000L);
        call.resolve();
    }

    @PluginMethod
    public void cancelRest(PluginCall call) {
        handler.post(this::clearRest);
        call.resolve();
    }

    /* ---------------- backups ---------------- */

    @PluginMethod
    public void backupInfo(PluginCall call) {
        SharedPreferences p = BackupWriter.prefs(getContext());
        JSObject r = new JSObject();
        String name = p.getString("folderName", null);
        if (name != null && p.getString("folderUri", null) != null) r.put("folder", name);
        r.put("lastBackup", p.getLong("lastBackup", 0));
        r.put("weekly", p.getBoolean("weekly", true));
        call.resolve(r);
    }

    @PluginMethod
    public void pickBackupFolder(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        startActivityForResult(call, i, "folderPicked");
    }

    @ActivityCallback
    private void folderPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.resolve(new JSObject());
            return;
        }
        Uri uri = result.getData().getData();
        try {
            getContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            call.reject("Could not keep access to that folder");
            return;
        }
        DocumentFile dir = DocumentFile.fromTreeUri(getContext(), uri);
        String name = dir != null && dir.getName() != null ? dir.getName() : "Backup folder";
        BackupWriter.prefs(getContext()).edit().putString("folderUri", uri.toString()).putString("folderName", name).putBoolean("weekly", true).apply();
        BackupWorker.schedule(getContext(), true);
        JSObject r = new JSObject();
        r.put("name", name);
        call.resolve(r);
    }

    @PluginMethod
    public void setWeekly(PluginCall call) {
        boolean on = Boolean.TRUE.equals(call.getBoolean("on", true));
        SharedPreferences p = BackupWriter.prefs(getContext());
        p.edit().putBoolean("weekly", on).apply();
        BackupWorker.schedule(getContext(), on && p.getString("folderUri", null) != null);
        call.resolve();
    }

    @PluginMethod
    public void saveSnapshot(PluginCall call) {
        String json = call.getString("json");
        if (json == null) {
            call.reject("No data");
            return;
        }
        try {
            BackupWriter.saveSnapshot(getContext(), json);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void readSnapshot(PluginCall call) {
        JSObject r = new JSObject();
        try {
            String json = BackupWriter.readSnapshot(getContext());
            if (json != null) r.put("json", json);
        } catch (Exception ignored) {}
        call.resolve(r);
    }

    @PluginMethod
    public void backupNow(PluginCall call) {
        JSObject r = new JSObject();
        try {
            String json = call.getString("json");
            if (json != null) BackupWriter.saveSnapshot(getContext(), json);
            String name = BackupWriter.write(getContext());
            r.put("ok", true);
            r.put("name", name);
            r.put("time", BackupWriter.prefs(getContext()).getLong("lastBackup", System.currentTimeMillis()));
        } catch (Exception e) {
            r.put("ok", false);
            r.put("error", String.valueOf(e.getMessage()));
        }
        call.resolve(r);
    }

    @PluginMethod
    public void exportFile(PluginCall call) {
        pendingExport = call.getString("data", "");
        pendingExportB64 = Boolean.TRUE.equals(call.getBoolean("base64", false));
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(call.getString("mime", "application/octet-stream"));
        i.putExtra(Intent.EXTRA_TITLE, call.getString("name", "lift-log.txt"));
        startActivityForResult(call, i, "exportPicked");
    }

    @ActivityCallback
    private void exportPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject r = new JSObject();
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            r.put("saved", false);
            call.resolve(r);
            return;
        }
        try (OutputStream os = getContext().getContentResolver().openOutputStream(result.getData().getData())) {
            if (os == null) throw new Exception("Could not open file");
            String data = pendingExport == null ? "" : pendingExport;
            os.write(pendingExportB64 ? Base64.decode(data, Base64.DEFAULT) : data.getBytes(StandardCharsets.UTF_8));
            r.put("saved", true);
            call.resolve(r);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        } finally {
            pendingExport = null;
            pendingExportB64 = false;
        }
    }

    /* ---------------- share an image ---------------- */

    @PluginMethod
    public void shareImage(PluginCall call) {
        try {
            byte[] bytes = Base64.decode(call.getString("data", ""), Base64.DEFAULT);
            File dir = new File(getContext().getCacheDir(), "share");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, call.getString("name", "lift-log.png"));
            try (FileOutputStream fo = new FileOutputStream(f)) {
                fo.write(bytes);
            }
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newRawUri("", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(send, "Share workout");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getActivity().startActivity(chooser);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    /* ---------------- lock-screen workout notification ---------------- */

    private void ensureWorkoutChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(WORKOUT_CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(WORKOUT_CHANNEL, "Workout in progress", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Rest timer and Done set button while you train");
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    private PendingIntent actionIntent(String a, int code) {
        Intent i = new Intent(getContext(), NotifReceiver.class);
        i.setAction("com.ethanreilly.liftlog.NOTIF_" + a);
        i.putExtra("a", a);
        return PendingIntent.getBroadcast(getContext(), code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @PluginMethod
    public void workoutNotif(PluginCall call) {
        Context ctx = getContext();
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            SharedPreferences sp = ctx.getSharedPreferences("liftlog", Context.MODE_PRIVATE);
            if (!sp.getBoolean("askedNotif", false)) {
                sp.edit().putBoolean("askedNotif", true).apply();
                ActivityCompat.requestPermissions(getActivity(), new String[] { Manifest.permission.POST_NOTIFICATIONS }, 4102);
            }
            JSObject r = new JSObject();
            r.put("shown", false);
            call.resolve(r);
            return;
        }
        try {
            ensureWorkoutChannel();
            long start = (long) call.getData().optDouble("start", (double) System.currentTimeMillis());
            long restEnd = (long) call.getData().optDouble("restEnd", 0d);
            boolean resting = restEnd > System.currentTimeMillis();
            boolean hasNext = Boolean.TRUE.equals(call.getBoolean("hasNext", false));
            Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            if (open != null) open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent content = open == null ? null : PendingIntent.getActivity(ctx, 4103, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, WORKOUT_CHANNEL) : new Notification.Builder(ctx);
            b.setSmallIcon(R.drawable.ic_stat_liftlog)
                .setContentTitle(call.getString("title", "Workout in progress"))
                .setContentText(call.getString("text", ""))
                .setSubText(call.getString("sub", ""))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(0xFF4F7CFF);
            if (Build.VERSION.SDK_INT >= 31) b.setCategory(Notification.CATEGORY_WORKOUT);
            if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_LOW).setSound(null).setVibrate(null);
            if (resting) {
                b.setWhen(restEnd);
                b.setChronometerCountDown(true);
            } else {
                b.setWhen(start);
            }
            if (content != null) b.setContentIntent(content);
            if (hasNext) b.addAction(new Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_stat_liftlog), "Done set", actionIntent("done", 1)).build());
            if (resting) {
                b.addAction(new Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_stat_liftlog), "+15 s", actionIntent("plus15", 2)).build());
                b.addAction(new Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_stat_liftlog), "Skip rest", actionIntent("skip", 3)).build());
            }
            NotificationManagerCompat.from(ctx).notify(WORKOUT_NOTIF, b.build());
            JSObject r = new JSObject();
            r.put("shown", true);
            call.resolve(r);
        } catch (SecurityException e) {
            JSObject r = new JSObject();
            r.put("shown", false);
            call.resolve(r);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void clearWorkoutNotif(PluginCall call) {
        NotificationManagerCompat.from(getContext()).cancel(WORKOUT_NOTIF);
        call.resolve();
    }

    void handleNotifAction(String a) {
        JSObject o = new JSObject();
        o.put("action", a);
        notifyListeners("notifAction", o, true);
    }

    @Override
    protected void handleOnDestroy() {
        try {
            NotificationManagerCompat.from(getContext()).cancel(WORKOUT_NOTIF);
        } catch (Exception ignored) {}
        if (instance == this) instance = null;
    }
}
