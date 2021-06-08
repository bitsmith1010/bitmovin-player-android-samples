package com.bitmovin.player.samples.playback.basic

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bitmovin.player.api.Player
import com.bitmovin.player.api.source.SourceConfig
import kotlinx.android.synthetic.main.activity_main.*
import com.bitmovin.player.api.source.SourceType

private const val Sintel = "https://bitdash-a.akamaihd.net/content/sintel/sintel.mpd"

class MainActivity : AppCompatActivity() {

    private lateinit var player: Player

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializePlayer()
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

    private fun initializePlayer() {
        player = Player.create(this).also { playerView.player = it }

        var metadata : Map<String,String> = mapOf(
                "key1" to "value1",
                "key2" to "value2"
        )

        var sourceConfig = SourceConfig(
                url = Sintel,
                type = SourceType.Dash,
                metadata = metadata
        )

        player.load(sourceConfig)
    }
}
