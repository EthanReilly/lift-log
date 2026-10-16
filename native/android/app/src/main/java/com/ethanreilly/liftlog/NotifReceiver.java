package com.ethanreilly.liftlog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives taps on the workout notification's buttons (Done set, +15 s, Skip rest). */
public class NotifReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getStringExtra("a");
        LiftLogPlugin p = LiftLogPlugin.instance;
        if (a == null) return;
        if (p != null) {
            p.handleNotifAction(a);
        } else {
            // the app was closed: open it so the workout can continue
            Intent open = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (open != null) {
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(open);
                } catch (Exception ignored) {}
            }
        }
    }
}
