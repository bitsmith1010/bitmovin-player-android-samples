package com.bitmovin.player.samples.custom.ui.subtitleview

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.bitmovin.player.PlayerView
import com.bitmovin.player.SubtitleView
import com.bitmovin.player.api.Player
import com.bitmovin.player.api.PlayerConfig
import com.bitmovin.player.api.event.SourceEvent
import com.bitmovin.player.api.network.*
import com.bitmovin.player.api.source.SourceConfig
import com.bitmovin.player.api.source.SourceType
import com.bitmovin.player.api.ui.StyleConfig
import kotlinx.android.synthetic.main.activity_main.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.util.Queue
import java.util.LinkedList
import java.util.concurrent.Future

class MainActivity : AppCompatActivity() {
    private lateinit var player: Player
    private lateinit var playerView: PlayerView
    private lateinit var subtitleView: SubtitleView
    private val subtitlesQueue: Queue<String> = LinkedList()

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
                preprocessHttpRequestCallback =
                        PreprocessHttpRequestCallback { type, request ->
                            if (type == HttpRequestType.MediaSubtitles) {
                                var subtitleUrl = request.url
                                Log.i(this.javaClass.name.toString(),
                                        "---http request prepared, subtitle " +
                                        "id: ${subtitlesQueue.poll()}, url: ${request.url}")

                                // todo: async http request to subtitle url

                                player.setSubtitle(
                                        subtitlesQueue.peek())
                            }/* else if (type == HttpRequestType.Unknown) {
                                Log.i(this.javaClass.name.toString(),
                                "http request, type:$type url:${request.url}")
                            } else {
                                Log.i(this.javaClass.name.toString(),
                                "http request, type:$type")
                            }*/

                            //todo: cancel original http request?
                            return@PreprocessHttpRequestCallback null
                        }
                preprocessHttpResponseCallback = object :
                        PreprocessHttpResponseCallback {
                    override fun preprocessHttpResponse(
                            type: HttpRequestType,
                            response: HttpResponse
                    ): Future<HttpResponse>? {
                        if (type == HttpRequestType.MediaSubtitles)
                            Log.i(
                                    this.javaClass.name.toString(),
                                    "---response type:$type status:${response.status} url:${response.url}"
                            )
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

        player.on(SourceEvent.SubtitleAdded::class, ::onSubtitleAdded)

        //player.load(SourceConfig("https://bitmovin-amer-public.s3.amazonaws.com/internal/dani/Wed_Apr_21_17%3A48%3A00_EDT_2021/zd7929-test1.m3u8", SourceType.Hls))

        player.load(SourceConfig("https://bitmovin-amer-public.s3.amazonaws.com/internal/dani/Wed_Apr_21_17%3A48%3A00_EDT_2021/siden-13.mpd", SourceType.Dash))

        // Creating a SubtitleView and assign the current player instance.
        subtitleView = SubtitleView(this)
        subtitleView.setPlayer(player)

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

    private fun onSubtitleAdded(event: SourceEvent.SubtitleAdded? = null)
    {
        subtitlesQueue.add(event?.subtitleTrack?.id)
        if (subtitlesQueue.size.equals(1)) {
            player.setSubtitle(event?.subtitleTrack?.id)
        }
        Log.i(this.javaClass.name.toString(),
                "--- on subtitle added: " +
                        event?.subtitleTrack?.id)
    }
}
