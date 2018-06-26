package io.github.ziginsider.mediasessiontest

import android.net.Uri

/**
 * Mock music catalog
 */
class TestMusicCatalog {

    class Track(val title: String,
                val artist: String,
                val bitmap: Int,
                val uri: Uri,
                val duration: Long)

    val catalog = arrayOf(
            Track("Ice and Snow",
                    "Rafael Krux",
                    R.drawable.image1,
                    Uri.parse("https://freepd.com/music/Ice and Snow.mp3"),
                    (2 * 60 + 21) * 1000),
            Track("Desert Fox",
                    "Rafael Krux",
                    R.drawable.image2,
                    Uri.parse("https://freepd.com/music/Desert Fox.mp3"),
                    (2 * 60 + 13) * 1000),
            Track("Coy Koi",
                    "Frank Nora",
                    R.drawable.image3,
                    Uri.parse("https://freepd.com/music/Coy Koi.mp3"),
                    48 * 1000),
            Track("Tarantella",
                    "Kevin MacLeod",
                    R.drawable.image4,
                    Uri.parse("https://freepd.com/music/Village Tarantella.mp3"),
                    53 * 1000),
            Track("Bit Bit Loop",
                    "Kevin MacLeod",
                    R.drawable.image5,
                    Uri.parse("https://freepd.com/music/Bit Bit Loop.mp3"),
                    (1 * 60 + 17) * 1000)
    )

    val maxTrackIndex = catalog.size - 1
    var currentTrackIndex = 0

    var currentTrack = catalog[0]
        get() = catalog[currentTrackIndex]
        private set

    fun next(): Track {
        if (currentTrackIndex == maxTrackIndex) {
            currentTrackIndex = 0
        } else {
            currentTrackIndex++
        }
        return currentTrack
    }

    fun previous(): Track {
        if (currentTrackIndex == 0) {
            currentTrackIndex = maxTrackIndex
        } else {
            currentTrackIndex--
        }
        return currentTrack
    }
}
