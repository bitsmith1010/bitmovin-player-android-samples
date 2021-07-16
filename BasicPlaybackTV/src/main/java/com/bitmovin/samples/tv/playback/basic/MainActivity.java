package com.bitmovin.samples.tv.playback.basic;


import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import androidx.appcompat.app.AppCompatActivity;

import com.bitmovin.player.PlayerView;
import com.bitmovin.player.api.PlaybackConfig;
import com.bitmovin.player.api.Player;
import com.bitmovin.player.api.PlayerConfig;
import com.bitmovin.player.api.drm.WidevineConfig;
import com.bitmovin.player.api.event.EventListener;
import com.bitmovin.player.api.event.PlayerEvent;
import com.bitmovin.player.api.event.SourceEvent;
import com.bitmovin.player.api.source.SourceConfig;
import com.bitmovin.player.api.source.SourceType;
import com.bitmovin.player.api.ui.StyleConfig;
import com.bitmovin.player.samples.tv.playback.basic.R;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int SEEKING_OFFSET = 10;

    private PlayerView playerView;
    private Player player;
    private int count;
    private String[] drm_url = {
            "https://wv.test.expressplay.com/hms/wv/rights/?ExpressPlayToken=BQAAAw72aTsAAAAAAGCbzv_RJAtz7_X6yhdMdQgRqywZdzNIExydDfWnJtkIgP6acgHFUbnileD1fdiY_4z5pe-WYT267aSMQnyc9HF9-M3LMY5I_uVoFrJidPOBv0_oxWM4xSW1XFS2Ms6Zf3vq5DhxhfdWoUteX1VJjQtGgtR3Mw",
            "https://wv.test.expressplay.com/hms/wv/rights/?ExpressPlayToken=BQAAAw72aYEAAAAAAGCkDn8XRO-c1qrrcIW-gXJ0GWVDsjFlQZNpDPH0LH3lgVp1j5T8PJIdKIodV7Z_1NrrMVuFa3CBRrxwWB15zkLPEyftEoGRUBhUqpJ90R_-Y3R3T_lDNSMIemBeJ8A_ZDEzLf7BfE-AY6UrOMtzHr6ILrt1Mg",
            "https://wv.test.expressplay.com/hms/wv/rights/?ExpressPlayToken=BQAAAw72aUgAAAAAAGDX09Eo-h8oavVxzofu9sYzs2gjHS0V9QWG_KGIVBMbrv6c30hCZdGM3OCb2cbbwziE06g0tJpSCxNHxDED8qCMD24Irw1ruraUTA3NU1xCeaEuHRZ4TvtMTdOBPdn6YCOKJXOeYZWzQSGJimPmPO3T8pkiJA"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Switch from splash screen to main theme when we are done loading
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initializePlayer(1);

    }

    private void initializePlayer(int id) {
        // Initialize BitmovinPlayerView from layout
        playerView = findViewById(R.id.bitmovin_player_view);

        player = Player.create(this, createPlayerConfig());


        player.on(PlayerEvent.Paused.class, onPausedListener);
        //player.on(PlayerEvent.Destroy.class, onDestroyListener);

        playerView.setPlayer(player);

        loadNewSource(1);
    }
    private final EventListener<PlayerEvent.Paused> onPausedListener = paused -> loadNewSource((++count) % 3);


    private PlayerConfig createPlayerConfig() {
        // Creating a new PlayerConfig
        PlayerConfig playerConfig = new PlayerConfig();

        // Here a custom bitmovinplayer-ui.js is loaded which utilizes the cast-UI as this matches our needs here perfectly.
        // I.e. UI controls get shown / hidden whenever the Player API is called. This is needed due to the fact that on Android TV no touch events are received
        StyleConfig styleConfig = new StyleConfig();
        styleConfig.setPlayerUiJs("file:///android_asset/bitmovinplayer-ui.js");
        playerConfig.setStyleConfig(styleConfig);

        PlaybackConfig playbackConfig = new PlaybackConfig();
        playbackConfig.setAutoplayEnabled(true);
        playerConfig.setPlaybackConfig(playbackConfig);

        return playerConfig;
    }

    @Override
    protected void onResume() {
        super.onResume();

        playerView.onResume();
        addEventListener();
        player.play();
    }

    @Override
    protected void onStart() {
        super.onStart();
        playerView.onStart();
    }

    @Override
    protected void onPause() {
        removeEventListener();
        playerView.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        playerView.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        playerView.onDestroy();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // This method is called on key down and key up, so avoid being called twice
        if (playerView != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (handleUserInput(event.getKeyCode())) {
                return true;
            }
        }

        // Make sure to return super.dispatchKeyEvent(event) so that any key not handled yet will work as expected
        return super.dispatchKeyEvent(event);
    }

    private boolean handleUserInput(int keycode) {
        Log.d(TAG, "Keycode " + keycode);
        switch (keycode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                togglePlay();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                player.play();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                player.pause();
                return true;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                stopPlayback();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekForward();
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBackward();
                break;
            default:
        }

        return false;
    }

    private void togglePlay() {
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    private void stopPlayback() {
        player.pause();
        player.seek(0);
    }

    private void seekForward() {
        player.seek(player.getCurrentTime() + SEEKING_OFFSET);
    }

    private void seekBackward() {
        player.seek(player.getCurrentTime() - SEEKING_OFFSET);
    }


    private void addEventListener() {
        if (player == null) return;

        player.on(PlayerEvent.Error.class, onPlayerError);
        player.on(SourceEvent.Error.class, onSourceError);
    }

    private void removeEventListener() {
        if (player == null) return;

        player.off(onPlayerError);
        player.off(onSourceError);
    }

    private final EventListener<PlayerEvent.Error> onPlayerError = errorEvent ->
            Log.e(TAG, "A player error occurred (" + errorEvent.getCode() + "): " + errorEvent.getMessage());

    private final EventListener<SourceEvent.Error> onSourceError = errorEvent ->
            Log.e(TAG, "A source error occurred (" + errorEvent.getCode() + "): " + errorEvent.getMessage());

    private void loadNewSource(int id)
    {
        // Create a new SourceItem. In this case we are loading a DASH source.
        String sourceURL = String.format("https://bitmovin-amer-public.s3.amazonaws.com/internal/dani/tests-encoding/cenc%d/manifest.mpd", id + 1);

        SourceConfig sourceConfig = new SourceConfig(sourceURL, SourceType.Dash);

        // Attach DRM handling to the source config
        sourceConfig.setDrmConfig(new WidevineConfig(drm_url[id]));

        player.load(sourceConfig);
    }

}
