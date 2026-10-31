package com.feiniu.tv.cast.service;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import com.feiniu.tv.cast.core.VolumeController;

/**
 * Maps the DLNA volume (0..100) onto the TV's music stream.
 *
 * <p>The volume lives in the service, not the player activity: a control point
 * often sends SetVolume before anything is playing, and the level has to be
 * right the moment the first frame appears.
 *
 * <p>Every call is defensive. TV firmware reports odd stream counts, some
 * outputs are fixed, and a failing audio HAL must never turn a 200 response
 * into a fault the sender retries forever.
 */
public final class AudioVolumeController implements VolumeController {

    private static final String TAG = "FeiniuCastVolume";

    private final AudioManager audio;

    public AudioVolumeController(Context context) {
        AudioManager manager = null;
        if (context != null) {
            Context app = context.getApplicationContext();
            manager = (AudioManager) (app != null ? app : context)
                    .getSystemService(Context.AUDIO_SERVICE);
        }
        this.audio = manager;
    }

    @Override
    public int getVolume() {
        if (audio == null) {
            return 0;
        }
        try {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            return toPercent(current, max);
        } catch (RuntimeException e) {
            Log.w(TAG, "cannot read music volume", e);
            return 0;
        }
    }

    @Override
    public boolean setVolume(int percent) {
        if (audio == null) {
            return false;
        }
        if (isFixedVolume()) {
            // Reporting the requested value while the output refuses is the
            // best available answer; the cast itself must keep working.
            Log.i(TAG, "music volume is fixed on this output");
            return false;
        }
        try {
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            if (max <= 0) {
                return false;
            }
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, toIndex(percent, max), 0);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "cannot set music volume", e);
            return false;
        }
    }

    /** "Fixed volume" is an API 21 concept; older platforms cannot report it. */
    private boolean isFixedVolume() {
        if (Build.VERSION.SDK_INT < 21) {
            return false;
        }
        try {
            return audio.isVolumeFixed();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Stream index to 0..100. Package-private for the JVM tests. */
    static int toPercent(int index, int max) {
        if (max <= 0) {
            return 0;
        }
        int percent = Math.round(index * 100f / max);
        return Math.max(0, Math.min(100, percent));
    }

    /** 0..100 to a stream index, clamped to the stream's range. */
    static int toIndex(int percent, int max) {
        if (max <= 0) {
            return 0;
        }
        int clamped = Math.max(0, Math.min(100, percent));
        return Math.max(0, Math.min(max, Math.round(clamped * max / 100f)));
    }
}
