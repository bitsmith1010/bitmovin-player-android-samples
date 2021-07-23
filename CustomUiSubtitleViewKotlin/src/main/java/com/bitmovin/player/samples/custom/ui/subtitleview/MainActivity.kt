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
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.*
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {
    private lateinit var job0: Job
    override val coroutineContext: CoroutineContext
        get() = job0 + Dispatchers.Main
    private lateinit var player: Player
    private lateinit var playerView: PlayerView
    private lateinit var subtitleView: SubtitleView
    private val lockCollection = Mutex()
    private val subtitlesQueue: Queue<String> = LinkedList()
    private var subtitlesOffTrack = ""

    var client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        job0 = Job()

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
                                player.setSubtitle(subtitlesOffTrack)
                                val url = request.url

                                launch {
                                    lockSubtitlesQueue {
                                        val id = this.remove()
                                        Log.i(this.javaClass.name.toString(),
                                                "---http request object received for subtitle id: $id, url: $url")

                                        // concurrent http request:
                                        verifySubtitle(url, id)

                                        if (this.size != 0)
                                        // `element()` is a read method
                                            runOnUiThread {player.setSubtitle(subtitlesQueue.element())}
                                    }
                                }
                            }

                            //todo: cancel original http request?
                            return@PreprocessHttpRequestCallback null
                        }
            }
        }

        // Creating a PlayerView and get it's Player instance.
        playerView = PlayerView(this, Player.create(this, playerConfig)).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        player = playerView.player!!

        subtitlesOffTrack = player.availableSubtitles[0].id
        player.setSubtitle(subtitlesOffTrack)

        player.on(SourceEvent.SubtitleAdded::class, ::onSubtitleAdded)

        player.load(SourceConfig("https://bitmovin-amer-public.s3.amazonaws.com/internal/dani/Wed_Apr_21_17%3A48%3A00_EDT_2021/zd7929-test1.mpd", SourceType.Dash))

        //player.load(SourceConfig("https://bitmovin-amer-public.s3.amazonaws.com/internal/dani/Wed_Apr_21_17%3A48%3A00_EDT_2021/zd7929-test1.m3u8", SourceType.Hls))

        //player.load(SourceConfig("https://bitmovin-amer-public.s3.amazonaws.com/internal/dani/Wed_Apr_21_17%3A48%3A00_EDT_2021/siden-13.mpd", SourceType.Dash))

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

    private suspend fun lockSubtitlesQueue(block: Queue<String>.() -> Unit) {
        lockCollection.withLock { subtitlesQueue.block() }
    }

    private fun verifySubtitle(url: String, id: String) {
        Log.i(this.javaClass.toString(), "---verify $url $id")
        launch {
            val code = async(Dispatchers.IO) { httpResponseCode(url) }
            if (code.await() >= 400) {
                Log.i(this.javaClass.toString(),
                        "---received error code $code for subtitle $url, id $id - track will be removed")
                runOnUiThread { player.removeSubtitle(id) }
            } else
                Log.i(this.javaClass.toString(),
                        "---subtitle $url, id $id access: success")
        }
    }

    // todo: follow redirects
    private fun httpResponseCode(url: String): Int {
        val request = Request.Builder()
                .url(url)
                .build()
        return try {
            val response = client.newCall(request).execute()
            response.code
        } catch (err: IOException) {
            throw IOException(err.message)
        }
    }

    private fun onSubtitleAdded(event: SourceEvent.SubtitleAdded? = null) {
        val id = event?.subtitleTrack?.id
        /* todo: protect the queue from concurrent access by the http
         * request event handler
         */
        launch {
            lockSubtitlesQueue {
                subtitlesQueue.add(id)
                if (subtitlesQueue.size == 1) player.setSubtitle(id)
                Log.i(
                        this.javaClass.name.toString(),
                        "---subtitle added: $id")
            }
        }
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
}
