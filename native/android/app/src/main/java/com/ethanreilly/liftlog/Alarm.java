package com.ethanreilly.liftlog;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Rest alarm: synthesised tones, optional vibration, and a short audio-focus grab so other apps pause. */
public class Alarm {
    private static final int SR = 44100;

    // {start s, duration s, frequency Hz, square? 1 : 0, amplitude}
    private static double[][] pattern(String sound) {
        if ("chime".equals(sound)) {
            return new double[][] { {0, .4, 660, 0, .9}, {.3, .8, 990, 0, .9}, {1.2, .4, 660, 0, .9}, {1.5, .9, 990, 0, .9} };
        }
        if ("bell".equals(sound)) {
            return new double[][] { {0, 1.3, 1320, 0, .9}, {0, .9, 2640, 0, .3}, {1.4, 1.3, 1320, 0, .9}, {1.4, .9, 2640, 0, .3} };
        }
        double[] starts = {0, .25, .5, 1.05, 1.3, 1.55};
        double[][] p = new double[starts.length][];
        for (int i = 0; i < starts.length; i++) p[i] = new double[] { starts[i], .16, 880, 1, .55 };
        return p;
    }

    private static short[] synth(String sound) {
        double[][] notes = pattern(sound);
        double end = 0;
        for (double[] n : notes) end = Math.max(end, n[0] + n[1]);
        int len = (int) ((end + .08) * SR);
        float[] buf = new float[len];
        for (double[] n : notes) {
            int s0 = (int) (n[0] * SR), cnt = (int) (n[1] * SR);
            double a = n[4];
            for (int i = 0; i < cnt && s0 + i < len; i++) {
                double t = (double) i / SR;
                double env = t < .01 ? a * (t / .01) : a * Math.pow(0.0001 / a, (t - .01) / Math.max(.02, n[1] - .01));
                double ph = Math.sin(2 * Math.PI * n[2] * t);
                double v = n[3] > 0 ? Math.signum(ph) * .6 : ph;
                buf[s0 + i] += (float) (v * env);
            }
        }
        short[] out = new short[len];
        for (int i = 0; i < len; i++) {
            float v = Math.max(-1f, Math.min(1f, buf[i]));
            out[i] = (short) (v * 32767 * .9f);
        }
        return out;
    }

    public static void play(final Context ctx, final String sound, final int volume, final boolean vibrate, final boolean duck, final boolean alarmStream) {
        new Thread(() -> {
            try {
                playBlocking(ctx.getApplicationContext(), sound, volume, vibrate, duck, alarmStream);
            } catch (Exception ignored) {}
        }).start();
    }

    private static void playBlocking(Context ctx, String sound, int volume, boolean vibrate, boolean duck, boolean alarmStream) throws InterruptedException {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(alarmStream ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
        AudioFocusRequest focus = null;
        if (duck && am != null) {
            if (Build.VERSION.SDK_INT >= 26) {
                focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build();
                am.requestAudioFocus(focus);
            } else {
                am.requestAudioFocus(null, alarmStream ? AudioManager.STREAM_ALARM : AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }
            Thread.sleep(350); // give the music app a moment to pause
        }
        if (vibrate) vibrate(ctx);
        float vol = Math.max(0f, Math.min(1f, volume / 100f));
        if (vol > 0f) {
            short[] pcm = synth(sound);
            AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
            track.write(pcm, 0, pcm.length);
            track.setVolume(vol);
            track.play();
            Thread.sleep(pcm.length * 1000L / SR + 120);
            try { track.stop(); } catch (IllegalStateException ignored) {}
            track.release();
        } else if (vibrate) {
            Thread.sleep(1200);
        }
        if (duck && am != null) {
            Thread.sleep(150);
            if (focus != null && Build.VERSION.SDK_INT >= 26) am.abandonAudioFocusRequest(focus);
            else am.abandonAudioFocus(null);
        }
    }

    @SuppressWarnings("deprecation")
    private static void vibrate(Context ctx) {
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v == null) return;
            long[] pattern = {0, 250, 120, 250, 120, 400};
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            else v.vibrate(pattern, -1);
        } catch (Exception ignored) {}
    }
}
