package com.feiniu.tv.cast.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Single cast session observed by both the DLNA control plane and the UI.
 *
 * <p>This is the one piece of state the two sides share: the HTTP thread
 * drives it from SOAP actions, the UI thread reads it and the player reports
 * playback progress back. It owns no threads, timers or Android objects, so
 * it runs unchanged on the JVM.
 *
 * <p>Listeners are notified on whatever thread produced the change, once per
 * observable change. A listener must not block: the UI one is expected to
 * post to its own handler.
 */
public final class CastSession {

    public enum State { IDLE, PREPARING, PLAYING, PAUSED, STOPPED }

    public interface Listener {
        void onSessionChanged(CastSession session);
    }

    /**
     * Fired by {@link #seekTo} only. A seek is the one change the state
     * machine cannot express (the transport state is unchanged), so the player
     * gets it as its own event instead of inferring it from position jumps.
     */
    public interface SeekListener {
        void onSeek(long positionMs);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private final List<SeekListener> seekListeners = new CopyOnWriteArrayList<SeekListener>();

    // Written from the control thread, read from the UI thread.
    private volatile State state = State.IDLE;
    private volatile String uri = "";
    private volatile String title = "";
    private volatile long positionMs;
    private volatile long durationMs;
    private volatile boolean pendingPlay;
    private volatile int volume = 50;
    private volatile VolumeController volumeController;

    /**
     * Generation counter for prepare requests. Bumped by every uri change and
     * by {@link #stop()}: the player layer captures it before starting a
     * prepare and drops its callback if the value moved in the meantime, so a
     * slow MediaPlayer callback can never resurrect an abandoned cast.
     */
    private volatile long prepareToken;

    public void addListener(Listener l) {
        if (l != null) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public void addSeekListener(SeekListener l) {
        if (l != null) {
            seekListeners.add(l);
        }
    }

    public void removeSeekListener(SeekListener l) {
        seekListeners.remove(l);
    }

    public void setVolumeController(VolumeController c) {
        this.volumeController = c;
    }

    /**
     * 让手机滑条对齐电视的真实音量。只在会话开始时调用一次:
     * 直接写 volume 字段,不经过输出设备,也不触发 NOTIFY 风暴。
     */
    public void syncVolume(int percent) {
        volume = Math.max(0, Math.min(100, percent));
    }

    /**
     * Accepts a new track. The player must prepare it; the session waits in
     * {@link State#PREPARING} until {@link #onPrepared(long)} or {@link #play()}.
     */
    public void setUri(String newUri, String newTitle) {
        this.uri = newUri == null ? "" : newUri;
        this.title = newTitle == null ? "" : newTitle;
        this.positionMs = 0;
        this.durationMs = 0;
        this.pendingPlay = false;
        this.prepareToken++;
        state = State.PREPARING;
        notifyListeners();
    }

    /**
     * Reports that the current track is ready. Ignored once the session has
     * been stopped or abandoned, so a late callback cannot move it back to a
     * playable state; callers should also compare {@link #prepareToken()}
     * before invoking this.
     *
     * @param duration total length in milliseconds, 0 when unknown
     */
    public void onPrepared(long duration) {
        if (!hasMedia() || state == State.IDLE || state == State.STOPPED) {
            return;
        }
        long d = duration > 0 ? duration : 0;
        boolean changed = false;
        if (d != durationMs) {
            durationMs = d;
            changed = true;
        }
        if (state == State.PREPARING) {
            state = pendingPlay ? State.PLAYING : State.PAUSED;
            pendingPlay = false;
            changed = true;
        }
        if (changed) {
            notifyListeners();
        }
    }

    /**
     * Starts playback. A Play that arrives while the media is still preparing
     * is remembered and applied once {@link #onPrepared(long)} lands, so the
     * device never reports PLAYING before it can actually render.
     */
    public void play() {
        if (!hasMedia() || state == State.PLAYING) {
            return;
        }
        if (state == State.PREPARING) {
            pendingPlay = true;
            return;
        }
        state = State.PLAYING;
        notifyListeners();
    }

    public void pause() {
        if (state == State.PREPARING) {
            pendingPlay = false;
            return;
        }
        if (state != State.PLAYING) {
            return;
        }
        state = State.PAUSED;
        notifyListeners();
    }

    /** Ends the cast: drops the media and invalidates any pending prepare. */
    public void stop() {
        if (state == State.STOPPED) {
            return;
        }
        uri = "";
        title = "";
        positionMs = 0;
        durationMs = 0;
        pendingPlay = false;
        prepareToken++;
        state = State.STOPPED;
        notifyListeners();
    }

    /**
     * Moves the playhead. Negative targets become 0; a target beyond a known
     * duration is clamped to it. Notifies: seeks are rare and user-visible.
     */
    public void seekTo(long ms) {
        if (!hasMedia()) {
            return;
        }
        long p = ms < 0 ? 0 : ms;
        if (durationMs > 0 && p > durationMs) {
            p = durationMs;
        }
        if (p == positionMs) {
            return;
        }
        positionMs = p;
        notifySeekListeners(p);
        notifyListeners();
    }

    /**
     * Periodic progress from the player. Deliberately silent: this runs about
     * once a second and must not wake the UI or the eventing code.
     */
    public void updatePosition(long position, long duration) {
        if (!hasMedia()) {
            return;
        }
        positionMs = position < 0 ? 0 : position;
        if (duration > 0) {
            durationMs = duration;
        }
    }

    /**
     * Clamps to 0..100, forwards to the audio output and records the result
     * even if the output refused it, so the reported volume matches what the
     * user asked for. Failures are swallowed: some TVs have a fixed volume.
     */
    public void setVolume(int percent) {
        int v = Math.max(0, Math.min(100, percent));
        VolumeController c = volumeController;
        if (c != null) {
            try {
                c.setVolume(v);
            } catch (RuntimeException ignored) {
                // Fixed-volume output: keep the requested value for reporting.
            }
        }
        volume = v;
        notifyListeners();
    }

    public boolean hasMedia() {
        return !uri.isEmpty();
    }

    public State state() {
        return state;
    }

    public String uri() {
        return uri;
    }

    public String title() {
        return title;
    }

    public long positionMs() {
        return positionMs;
    }

    public long durationMs() {
        return durationMs;
    }

    public int volume() {
        return volume;
    }

    public long prepareToken() {
        return prepareToken;
    }

    private void notifyListeners() {
        for (Listener l : listeners) {
            try {
                l.onSessionChanged(this);
            } catch (RuntimeException ignored) {
                // A broken observer (usually UI) must not break the control plane.
            }
        }
    }

    private void notifySeekListeners(long position) {
        for (SeekListener l : seekListeners) {
            try {
                l.onSeek(position);
            } catch (RuntimeException ignored) {
                // Same policy as above: one bad player must not break seeks.
            }
        }
    }
}
