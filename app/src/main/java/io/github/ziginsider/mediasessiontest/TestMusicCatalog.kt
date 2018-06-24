package io.github.ziginsider.mediasessiontest

import android.net.Uri

class TestMusicCatalog {

    class Track(val title: String,
                val artist: String,
                val bitmap: Int,
                val uri: Uri,
                val duradtion: Long)

    val catalog = arrayOf(
            Track("Triangle",
                    "Jason Shaw",
                    R.drawable.image266680,
                    Uri.parse("https://freepd.com/Ballad/Triangle.mp3"),
                    (3 * 60 + 41) * 1000),
            Track("Rubix Cube",
                    "Jason Shaw",
                    R.drawable.image396168,
                    Uri.parse("https://freepd.com/Ballad/Rubix Cube.mp3"),
                    (3 * 60 + 44) * 1000),
            Track("MC Ballad S Early Eighties",
                    "Frank Nora",
                    R.drawable.image533998,
                    Uri.parse("https://freepd.com/Ballad/MC Ballad S Early Eighties.mp3"),
                    (2 * 60 + 50) * 1000),
            Track("Folk Song",
                    "Brian Boyko",
                    R.drawable.image544064,
                    Uri.parse("https://freepd.com/Acoustic/Folk Song.mp3"),
                    (3 * 60 + 5) * 1000),
            Track("Morning Snowflake",
                    "Kevin MacLeod",
                    R.drawable.image208815,
                    Uri.parse("https://freepd.com/Acoustic/Morning Snowflake.mp3"),
                    (2 * 60 + 0) * 1000)
    )
}