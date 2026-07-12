package jp.mitsuyasu.player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;

public final class MainActivity extends Activity {
    private static final long SEEK_10_SECONDS = 10_000L;
    private static final long SEEK_1_MINUTE = 60_000L;
    private static final long SEEK_5_MINUTES = 300_000L;

    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private SurfaceView videoView;
    private boolean released;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        videoView = new SurfaceView(this);
        videoView.setBackgroundColor(Color.BLACK);
        setContentView(videoView);
        enterImmersiveMode();

        final Uri videoUri = getIntent().getData();
        if (!Intent.ACTION_VIEW.equals(getIntent().getAction()) || videoUri == null) {
            finishAndRemoveTask();
            return;
        }

        startPlayer(videoUri);
    }

    private void startPlayer(Uri videoUri) {
        final ArrayList<String> options = new ArrayList<>();
        libVLC = new LibVLC(this, options);
        mediaPlayer = new MediaPlayer(libVLC);

        mediaPlayer.getVLCVout().setVideoView(videoView);
        mediaPlayer.getVLCVout().attachViews();

        final Media media = new Media(libVLC, videoUri);
        media.setHWDecoderEnabled(true, false);
        mediaPlayer.setMedia(media);
        media.release();

        mediaPlayer.play();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }

        final int keyCode = event.getKeyCode();

        if (event.isCtrlPressed() && keyCode == KeyEvent.KEYCODE_W) {
            if (event.getRepeatCount() == 0) {
                closePlayer();
            }
            return true;
        }

        if (mediaPlayer == null || released) {
            return super.dispatchKeyEvent(event);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_SPACE:
                if (event.getRepeatCount() == 0) {
                    togglePlayPause();
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                seekBy(-SEEK_10_SECONDS);
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                seekBy(SEEK_10_SECONDS);
                return true;

            case KeyEvent.KEYCODE_DPAD_UP:
                seekBy(SEEK_1_MINUTE);
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                seekBy(SEEK_5_MINUTES);
                return true;

            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void togglePlayPause() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.play();
        }
    }

    private void seekBy(long deltaMs) {
        final long current = mediaPlayer.getTime();
        final long length = mediaPlayer.getLength();

        long target = Math.max(0L, current + deltaMs);
        if (length > 0L) {
            target = Math.min(target, length);
        }

        mediaPlayer.setTime(target);
    }

    private void closePlayer() {
        releasePlayer();
        finishAndRemoveTask();
    }

    private void releasePlayer() {
        if (released) {
            return;
        }
        released = true;

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.getVLCVout().detachViews();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
        }
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
        }
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
