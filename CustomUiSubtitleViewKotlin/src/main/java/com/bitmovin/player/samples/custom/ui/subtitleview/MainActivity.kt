package com.bitmovin.player.samples.custom.ui.subtitleview

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.bitmovin.player.PlayerView
import com.bitmovin.player.SubtitleView
import com.bitmovin.player.api.Player
import com.bitmovin.player.api.PlayerConfig
import com.bitmovin.player.api.event.SourceEvent
import com.bitmovin.player.api.network.*
import com.bitmovin.player.api.source.Source
import com.bitmovin.player.api.source.SourceConfig
import com.bitmovin.player.api.source.SourceType
import com.bitmovin.player.api.ui.StyleConfig
import kotlinx.android.synthetic.main.activity_main.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.util.concurrent.Future

class MainActivity : AppCompatActivity() {
    private lateinit var player: Player
    private lateinit var playerView: PlayerView
    private lateinit var subtitleView: SubtitleView
    private val gson: Gson = Gson()
    private var subtitleResponseRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Create new StyleConfig
        val styleConfig = StyleConfig()
        // Disable default Bitmovin UI
        styleConfig.isUiEnabled = false

        // Creating a new PlayerConfig
        // Assign created StyleConfig to the PlayerConfig

        var playerConfig = PlayerConfig().apply {
            networkConfig = NetworkConfig().apply {
                preprocessHttpResponseCallback = object :
                        PreprocessHttpResponseCallback {
                    override fun preprocessHttpResponse(
                            type: HttpRequestType,
                            response: HttpResponse
                    ): Future<HttpResponse>? {
                        if (type == HttpRequestType.MediaSubtitles) {
                            Log.d(
                                    "BasicPlayback",
                                    "--- RESPONSE type:$type status:${response.status} url:${response.url}"
                            )
                            subtitleResponseRegistered = true
                        }
                        return null
                    }
                }
            }
        }

        // Creating a PlayerView and get it's Player instance.
        playerView = PlayerView(this, Player.create(this, playerConfig)).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        player = playerView.player!!

        player.load(SourceConfig("https://bitmovin-amer-public.s3.amazonaws.com/internal/dani/Wed_Apr_21_17%3A48%3A00_EDT_2021/zd7929-test1.m3u8", SourceType.Hls))

        //player.load(SourceConfig("https://bitmovin-amer-public.s3.amazonaws.com/internal/dani/Wed_Apr_21_17%3A48%3A00_EDT_2021/siden-13.mpd", SourceType.Dash))

        // Creating a SubtitleView and assign the current player instance.
        subtitleView = SubtitleView(this)
        subtitleView.setPlayer(player)

        // todo - in place of delay, find an event that signals
        //   that availableSubtitles is ready
        Handler().postDelayed({
            Log.i(
                    this.javaClass.name.toString(),
                    "--- After delay: " +
                            "number of subtitleId tracks: " +
                            player.availableSubtitles.size.toString())
            var intervalCounter = 0
            var timeout = 1000
            var subtitleIndex = 1
            var subtitleId: String
            var intervalMs: Long = 100
            setInterval(intervalMs) {
                if (subtitleIndex < player.availableSubtitles.size) {
                    subtitleId = player.availableSubtitles[subtitleIndex].id
                    when {
                        intervalCounter == 1 -> {
                            player.setSubtitle(subtitleId)
                        }
                        subtitleResponseRegistered -> {
                            Log.i(
                                    this.javaClass.name.toString(),
                                    "--- Request sent for: $subtitleId")
                            intervalCounter = 0
                            subtitleResponseRegistered = false
                            subtitleIndex++
                        }
                        intervalCounter > timeout / intervalMs -> {
                            Log.i(
                                    this.javaClass.name.toString(),
                                    "--- NO RESPONSE: REMOVE $subtitleId")
                            player.removeSubtitle(subtitleId)
                            subtitleResponseRegistered = false
                            intervalCounter = 0
                        }
                    }
                    intervalCounter++
                }
            }
        }, 2000)

        // Setup minimalistic controls for the player
        playerControls.setPlayer(player)

        // Add the SubtitleView to the layout
        playerContainer.addView(subtitleView)

        // Add the PlayerView to the layout as first position (so it is the behind the SubtitleView)
        playerContainer.addView(playerView, 0)
    }

    override fun onStart() {
        super.onStart()
        playerView.onStart()
    }

    override fun onResume() {
        super.onResume()
        playerView.onResume()
    }

    override fun onPause() {
        playerView.onPause()
        super.onPause()
    }

    override fun onStop() {
        playerView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        playerView.onDestroy()
        super.onDestroy()
    }

    private fun onSubtitleAdded(event: SourceEvent.SubtitleAdded)
    {
        Log.i(this.javaClass.name.toString(),
                "--- on subtitle added: " +
                        event.subtitleTrack.id)
    }

    private fun onLoadedEvent(event: SourceEvent.Loaded)
    {
        Log.i(this.javaClass.name.toString(),
                "--- on source load, number of subtitles: " +
                        player.availableSubtitles.size.toString() +
                " " + event.source.loadingState.toString())
    }

    fun setInterval(timeMillis: Long, handler: () -> Unit) = GlobalScope.launch {
        while (true) {
            delay(timeMillis)
            handler()
        }
    }
}
