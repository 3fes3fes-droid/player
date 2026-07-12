package jp.mitsuyasu.player;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.IOException;
import java.util.ArrayList;

public final class MainActivity extends Activity {

    private static final String TAG = "MitsuyasuPlayer";

    private static final long SEEK_10_SECONDS = 10_000L;
    private static final long SEEK_1_MINUTE = 60_000L;
    private static final long SEEK_5_MINUTES = 300_000L;

    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private VLCVideoLayout videoLayout;
    private ParcelFileDescriptor parcelFileDescriptor;
    private boolean released;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        videoLayout = new VLCVideoLayout(this);
        videoLayout.setBackgroundColor(Color.BLACK);
        setContentView(videoLayout);

        hideSystemUi();
        getWindow().getDecorView().post(this::hideSystemUi);

        final Intent intent = getIntent();
        final Uri videoUri = intent.getData();

        if (!Intent.ACTION_VIEW.equals(intent.getAction()) || videoUri == null) {
            closePlayer();
            return;
        }

        startPlayer(videoUri);
    }

    private void startPlayer(Uri videoUri) {
        try {
            final ArrayList<String> options = new ArrayList<>();
            options.add("--no-video-title-show");

            libVLC = new LibVLC(this, options);
            mediaPlayer = new MediaPlayer(libVLC);

            mediaPlayer.attachViews(videoLayout, null, false, false);
            mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT);

            parcelFileDescriptor =
                    getContentResolver().openFileDescriptor(videoUri, "r");

            if (parcelFileDescriptor == null) {
                throw new IOException(
                        "openFileDescriptor returned null for " + videoUri
                );
            }

            final Media media = new Media(
                    libVLC,
                    parcelFileDescriptor.getFileDescriptor()
            );

            media.setHWDecoderEnabled(true, false);

            mediaPlayer.setMedia(media);
            media.release();

            mediaPlayer.play();

        } catch (Exception e) {
            Log.e(TAG, "Failed to open video: " + videoUri, e);
            closePlayer();
        }
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
                if (event.getRepeatCount() == 0) {
                    seekBy(-SEEK_10_SECONDS);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.getRepeatCount() == 0) {
                    seekBy(SEEK_10_SECONDS);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_UP:
                if (event.getRepeatCount() == 0) {
                    seekBy(SEEK_1_MINUTE);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (event.getRepeatCount() == 0) {
                    seekBy(SEEK_5_MINUTES);
                }
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
        final long current = Math.max(0L, mediaPlayer.getTime());
        final long length = mediaPlayer.getLength();

        long target = Math.max(0L, current + deltaMs);

        if (length > 0L) {
            target = Math.min(target, length);
        }

        mediaPlayer.setTime(target);
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);

            final WindowInsetsController controller =
                    getWindow().getInsetsController();

            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController
                                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            hideSystemUi();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        hideSystemUi();

        if (mediaPlayer != null && !released) {
            mediaPlayer.setVideoScale(
                    MediaPlayer.ScaleType.SURFACE_BEST_FIT
            );
        }
    }

    private void closePlayer() {
        releasePlayer();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
    }

    private void releasePlayer() {
        if (released) {
            return;
        }

        released = true;

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.detachViews();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (parcelFileDescriptor != null) {
            try {
                parcelFileDescriptor.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close file descriptor", e);
            }

            parcelFileDescriptor = null;
        }

        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
        }
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
