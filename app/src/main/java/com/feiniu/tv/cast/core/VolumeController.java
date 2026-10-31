package com.feiniu.tv.cast.core;

/**
 * Audio output controlled by a {@link CastSession}.
 *
 * <p>Implemented by the player layer; kept as a narrow interface so the
 * session stays free of {@code android.media.*} and testable on the JVM.
 * Some TVs report a fixed volume and refuse changes, hence the boolean
 * result and the defensive call site in {@link CastSession#setVolume(int)}.
 */
public interface VolumeController {

    /** Current output volume, 0..100. */
    int getVolume();

    /**
     * Sets the output volume.
     *
     * @param percent 0..100, already clamped by the caller
     * @return whether the change reached the audio output
     */
    boolean setVolume(int percent);
}
