package com.ethanreilly.liftlog;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Keeps a snapshot of the app data on the phone and copies it into the user's chosen backup folder. */
public class BackupWriter {
    static final int KEEP = 8;

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("liftlog", Context.MODE_PRIVATE);
    }

    static File snapshotFile(Context ctx) {
        return new File(ctx.getFilesDir(), "snapshot.json");
    }

    static void saveSnapshot(Context ctx, String json) throws Exception {
        File tmp = new File(ctx.getFilesDir(), "snapshot.json.tmp");
        try (OutputStream os = new FileOutputStream(tmp)) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        File dest = snapshotFile(ctx);
        if (!tmp.renameTo(dest)) throw new Exception("could not save snapshot");
    }

    static String readSnapshot(Context ctx) throws Exception {
        File f = snapshotFile(ctx);
        if (!f.exists()) return null;
        try (InputStream is = new FileInputStream(f)) {
            return new String(readAll(is), StandardCharsets.UTF_8);
        }
    }

    static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = is.read(b)) > 0) bos.write(b, 0, n);
        return bos.toByteArray();
    }

    /** Writes today's backup file. Returns its name. */
    static String write(Context ctx) throws Exception {
        SharedPreferences p = prefs(ctx);
        String u = p.getString("folderUri", null);
        if (u == null) throw new Exception("no_folder");
        String json = readSnapshot(ctx);
        if (json == null) throw new Exception("no_data");
        DocumentFile dir = DocumentFile.fromTreeUri(ctx, Uri.parse(u));
        if (dir == null || !dir.canWrite()) throw new Exception("folder_unavailable");
        String name = "lift-log-backup-" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json";
        DocumentFile f = dir.findFile(name);
        if (f == null) f = dir.createFile("application/json", name);
        if (f == null) throw new Exception("could_not_create");
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        OutputStream os;
        try {
            os = ctx.getContentResolver().openOutputStream(f.getUri(), "wt");
        } catch (Exception e) {
            os = ctx.getContentResolver().openOutputStream(f.getUri(), "w");
        }
        if (os == null) throw new Exception("could_not_open");
        try {
            os.write(data);
        } finally {
            os.close();
        }
        prune(dir);
        p.edit().putLong("lastBackup", System.currentTimeMillis()).apply();
        return name;
    }

    private static void prune(DocumentFile dir) {
        try {
            List<DocumentFile> backups = new ArrayList<>();
            for (DocumentFile f : dir.listFiles()) {
                String n = f.getName();
                if (n != null && n.startsWith("lift-log-backup-") && n.endsWith(".json")) backups.add(f);
            }
            Collections.sort(backups, (a, b) -> String.valueOf(b.getName()).compareTo(String.valueOf(a.getName())));
            for (int i = KEEP; i < backups.size(); i++) backups.get(i).delete();
        } catch (Exception ignored) {}
    }
}
