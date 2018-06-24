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
            Track("Triangle",
                    "Jason Shaw",
                    R.drawable.image1,
                    Uri.parse("https://freepd.com/Ballad/Triangle.mp3"),
                    (3 * 60 + 41) * 1000),
            Track("Rubix Cube",
                    "Jason Shaw",
                    R.drawable.image2,
                    Uri.parse("https://freepd.com/Ballad/Rubix Cube.mp3"),
                    (3 * 60 + 44) * 1000),
            Track("MC Ballad S Early Eighties",
                    "Frank Nora",
                    R.drawable.image3,
                    Uri.parse("https://freepd.com/Ballad/MC Ballad S Early Eighties.mp3"),
                    (2 * 60 + 50) * 1000),
            Track("Folk Song",
                    "Brian Boyko",
                    R.drawable.image4,
                    Uri.parse("https://freepd.com/Acoustic/Folk Song.mp3"),
                    (3 * 60 + 5) * 1000),
            Track("Morning Snowflake",
                    "Kevin MacLeod",
                    R.drawable.image5,
                    Uri.parse("https://freepd.com/Acoustic/Morning Snowflake.mp3"),
                    (2 * 60 + 0) * 1000)
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
