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
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {
    override val coroutineContext: CoroutineContext = Job() + Dispatchers.Main
    private lateinit var player: Player
    private lateinit var playerView: PlayerView
    private lateinit var subtitleView: SubtitleView
    private val lockCollection = Mutex()
    private val subtitlesQueue: Queue<String> = LinkedList()
    private var subtitlesOffTrack = ""

    private var timeSetSubtitle = 0L
    private var totalTimeSetSubtitle = 0L
    private var timeStart = System.currentTimeMillis()

    private val client = OkHttpClient.Builder()
      .connectionPool(ConnectionPool(
        20, 5L, TimeUnit.MINUTES))
      .build()

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

                          totalTimeSetSubtitle +=
                            System.currentTimeMillis() -
                              timeSetSubtitle
                          Log.i(
                            this.javaClass.toString(),
                            "---partial sum of setSubtitle() duration: $totalTimeSetSubtitle, url: ${request.url}")

                          player.setSubtitle(subtitlesOffTrack)
                          var url = request.url
                          // test inaccessible url
                          if (url.indexOf("textstream_eng") != -1
                            || url.indexOf("textstream_spa") != -1)
                              url += "now-inaccessible"

                          launch {
                              lockSubtitlesQueue {
                                  if (this.size != 0){
                                      val id = this.remove()
                                      Log.i(this.javaClass.name.toString(),
                                        "---http request object received for subtitle id: $id, url: $url")


                                      // concurrent http request:
                                      launch {
                                          verifySubtitle(url, id)
                                      }

                                      if (this.size != 0)
                                      // `element()` is a read method
                                          runOnUiThread {
                                              timeSetSubtitle =
                                                System.currentTimeMillis()
                                              player.setSubtitle(subtitlesQueue.element())

                                          }
                                  }
                              }}
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

        player.load(SourceConfig("https://demo.unified-streaming.com/video/tears-of-steel/tears-of-steel-wvtt.ism/.mpd", SourceType.Dash))

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

    private suspend fun verifySubtitle(url: String, id: String) {
        Log.i(this.javaClass.toString(), "---verify $url $id")

        val code = async(Dispatchers.IO) { httpResponseCode(url) }
        if (isError(code.await())) {
            Log.i(this.javaClass.toString(),
              "---received error code $code for subtitle $url, id $id - track will be removed, time: ${System.currentTimeMillis() - timeStart}")
            runOnUiThread { player.removeSubtitle(id) }
        } else
            Log.i(this.javaClass.toString(),
              "---subtitle $url, id $id access: success, time: ${System.currentTimeMillis() - timeStart}")

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
                if (subtitlesQueue.size == 1) {
                    timeSetSubtitle = System.currentTimeMillis()
                    player.setSubtitle(id)
                }
                Log.i(
                  this.javaClass.name.toString(),
                  "---subtitle added: $id")
            }
        }
    }

    private fun isError(code: Int): Boolean
    {
        //todo: more accurate model of inaccessible URL
        return code >= 400
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
