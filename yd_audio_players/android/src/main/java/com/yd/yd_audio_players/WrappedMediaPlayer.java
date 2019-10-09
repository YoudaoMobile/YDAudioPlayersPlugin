package com.yd.yd_audio_players;

import android.content.Context;
import android.media.AudioManager;
import android.os.PowerManager;
import android.text.TextUtils;

import java.io.IOException;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class WrappedMediaPlayer extends Player implements IMediaPlayer.OnPreparedListener, IMediaPlayer.OnCompletionListener {

    private String playerId;

    private String url;
    private double volume = 1.0;
    private boolean respectSilence;
    private boolean stayAwake;
    private ReleaseMode releaseMode = ReleaseMode.RELEASE;

    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;

    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;

    private int mSeekWhenPrepared = -1;

    private IMediaPlayer player;
    private YDAudioPlayersPlugin ref;

    WrappedMediaPlayer(YDAudioPlayersPlugin ref, String playerId) {
        this.ref = ref;
        this.playerId = playerId;
    }

    /**
     * Setter methods
     */

    @Override
    void setUrl(String url, boolean isLocal) {
        if (isInPlaybackState() && TextUtils.equals(this.url, url)) {
            //重用
        } else {
            release();
            this.player = createPlayer();
            this.setSource(url);
            this.player.setVolume((float) volume, (float) volume);
            this.player.setLooping(this.releaseMode == ReleaseMode.LOOP);
            this.player.prepareAsync();
            mCurrentState = STATE_PREPARING;
        }
    }

    @Override
    void setVolume(double volume) {
        if (this.volume != volume) {
            this.volume = volume;
            if (isInPlaybackState()) {
                this.player.setVolume((float) volume, (float) volume);
            }
        }
    }

    @Override
    void configAttributes(boolean respectSilence, boolean stayAwake, Context context) {
        if (this.respectSilence != respectSilence) {
            this.respectSilence = respectSilence;
            if (isInPlaybackState()) {
                setAttributes(player);
            }
        }
        if (this.stayAwake != stayAwake) {
            this.stayAwake = stayAwake;
            if (isInPlaybackState() && this.stayAwake) {
                this.player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
            }
        }
    }

    @Override
    void setReleaseMode(ReleaseMode releaseMode) {
        if (this.releaseMode != releaseMode) {
            this.releaseMode = releaseMode;
            if (isInPlaybackState()) {
                this.player.setLooping(releaseMode == ReleaseMode.LOOP);
            }
        }
    }

    /**
     * Getter methods
     */

    @Override
    int getDuration() {
        if (isInPlaybackState()) {
            return (int) this.player.getDuration();
        }
        return 0;
    }

    @Override
    int getCurrentPosition() {
        if (isInPlaybackState()) {
            return (int) player.getCurrentPosition();
        }
        return 0;
    }

    @Override
    String getPlayerId() {
        return this.playerId;
    }

    @Override
    boolean isActuallyPlaying() {
        return isInPlaybackState() && player.isPlaying();
    }

    /**
     * Playback handling methods
     */
    @Override
    void play() {
        if (isInPlaybackState()) {
            this.ref.handleIsPlaying(this);
            player.start();
            mCurrentState = STATE_PLAYING;
        }
        mTargetState = STATE_PLAYING;
    }

    @Override
    void stop() {
        if (releaseMode != ReleaseMode.RELEASE) {
            if (player != null && player.isPlaying()) {
                player.seekTo(0);
            }
            pause();
        } else {
            this.release();
        }
    }

    @Override
    void release() {
        final IMediaPlayer tempPlayer = this.player;
        if (tempPlayer != null) {
            tempPlayer.stop();
            tempPlayer.reset();
            tempPlayer.release();
        }
        this.player = null;
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;
    }

    @Override
    void pause() {
        if (isInPlaybackState()) {
            if (player.isPlaying()) {
                player.pause();
                mCurrentState = STATE_PAUSED;
            }
        }
        mTargetState = STATE_PAUSED;
    }

    // seek operations cannot be called until after
    // the player is ready.
    @Override
    void seek(int position) {
        if (isInPlaybackState()) {
            player.seekTo(position);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = position;
        }
    }

    /**
     * IMediaPlayer callbacks
     */

    @Override
    public void onPrepared(final IMediaPlayer player) {
        mCurrentState = STATE_PREPARED;
        ref.handleDuration(this);

        if (mSeekWhenPrepared >= 0) {
            player.seekTo(mSeekWhenPrepared);
            mSeekWhenPrepared = -1;
        }
        if (mTargetState == STATE_PLAYING) {
            play();
        }
    }

    @Override
    public void onCompletion(final IMediaPlayer IMediaPlayer) {
        mCurrentState = STATE_PLAYBACK_COMPLETED;
        mTargetState = STATE_PLAYBACK_COMPLETED;
        ref.handleCompletion(this);
    }

    /**
     * Internal logic. Private methods
     */

    private IMediaPlayer createPlayer() {
        IjkMediaPlayer player = new IjkMediaPlayer();
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        setAttributes(player);
        player.setVolume((float) volume, (float) volume);
        player.setLooping(this.releaseMode == ReleaseMode.LOOP);
        return player;
    }

    private void setSource(String url) {
        try {
            this.url = url;
            this.player.setDataSource(url);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to access resource", ex);
        }
    }

    private void setAttributes(IMediaPlayer player) {
        player.setAudioStreamType(respectSilence ? AudioManager.STREAM_RING : AudioManager.STREAM_MUSIC);
    }

    private boolean isInPlaybackState() {
        return (player != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING);
    }

}
