package io.github.ziginsider.mediasessiontest

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Mock music catalog
 */
class TestMusicCatalog(context: Context) {

    init {
        setCatalogFromJson(context)
    }

    @JsonClass(generateAdapter = true)
    data class JsonTrack(
        val title: String,
        val artist: String,
        val bitmapUri: String,
        val trackUri: String,
        val duration: Long
    )

    val bitmaps = HashMap<String, Bitmap>(5)

    private var _catalog: List<JsonTrack>? = null
    private val catalog: List<JsonTrack> get() = requireNotNull(_catalog)

    private fun setCatalogFromJson(context: Context) {
        val moshi = Moshi.Builder()
            .build()

        val arrayType = Types.newParameterizedType(List::class.java, JsonTrack::class.java)
        val adapter: JsonAdapter<List<JsonTrack>> = moshi.adapter(arrayType)

        val file = "playlist.json"

        val myJson = context.assets.open(file).bufferedReader().use { it.readText() }

        _catalog = adapter.fromJson(myJson)

        GlobalScope.launch(Dispatchers.Default) {
            try {
                _catalog?.forEach {
                    val bitmap = Glide.with(context).asBitmap().load(it.bitmapUri).into(200, 200).get()
                    bitmaps[it.bitmapUri] = bitmap
                }
            }
            catch (e: Exception) {}
        }
    }

    val maxTrackIndex = catalog.size - 1
    var currentTrackIndex = 0
    val countTracks = catalog.size

    var currentTrack = catalog[0]
        get() = catalog[currentTrackIndex]
        private set

    fun next(): JsonTrack {
        if (currentTrackIndex == maxTrackIndex) {
            currentTrackIndex = 0
        } else {
            currentTrackIndex++
        }
        return currentTrack
    }

    fun previous(): JsonTrack {
        if (currentTrackIndex == 0) {
            currentTrackIndex = maxTrackIndex
        } else {
            currentTrackIndex--
        }
        return currentTrack
    }

    fun getTrackByIndex(index: Int) = catalog[index]
    fun getTrackCatalog() = catalog
}
