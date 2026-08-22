package com.iptv.player.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.iptv.player.IptvApp
import com.iptv.player.adapter.CategoryAdapter
import com.iptv.player.adapter.ChannelAdapter
import com.iptv.player.databinding.ActivityMainBinding
import com.iptv.player.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var app: IptvApp
    private lateinit var channelAdapter: ChannelAdapter

    private var allChannels: List<Channel> = emptyList()
    private var channelNumbers: Map<String, Int> = emptyMap()
    private var currentCategory: String = CATEGORY_ALL
    private var currentQuery: String = ""

    companion object {
        private const val CATEGORY_ALL = "All"
        private const val CATEGORY_FAVORITES = "Favorites"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        app = IptvApp.from(this)

        title = getString(com.iptv.player.R.string.app_name)

        channelAdapter = ChannelAdapter(
            isFavorite = { app.favorites.isFavorite(it.key) },
            onClick = { openPlayer(it) },
            onLongClick = { toggleFavorite(it) },
            numberFor = { channelNumbers[it.key] }
        )
        binding.channelGrid.layoutManager = GridLayoutManager(this, spanCountForWidth())
        binding.channelGrid.adapter = channelAdapter

        binding.searchInput.doAfterTextChangedCompat { text ->
            currentQuery = text
            applyFilter()
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.browserButton.setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java))
        }
        binding.recordingsButton.setOnClickListener {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }
        binding.epgButton.setOnClickListener {
            startActivity(Intent(this, EpgActivity::class.java))
        }
        binding.reloadButton.setOnClickListener { loadPlaylist() }

        loadPlaylist()
    }

    override fun onResume() {
        super.onResume()
        // Reflect favorite toggles made elsewhere, and honor a freshly-saved playlist URL.
        if (allChannels.isEmpty()) loadPlaylist() else applyFilter()
    }

    private fun spanCountForWidth(): Int {
        val widthDp = resources.configuration.screenWidthDp
        return (widthDp / 160).coerceIn(2, 8)
    }

    private fun loadPlaylist() {
        val url = app.settings.playlistUrl
        val localUriString = app.settings.playlistLocalUri
        val usingBundledDefault = url.isNullOrBlank() && localUriString.isNullOrBlank()

        binding.progress.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            try {
                val channels = withContext(Dispatchers.IO) {
                    when {
                        !url.isNullOrBlank() -> app.playlistRepository.loadFromUrl(url)
                        !localUriString.isNullOrBlank() ->
                            app.playlistRepository.loadFromLocalUri(this@MainActivity, android.net.Uri.parse(localUriString))
                        // No custom playlist configured yet - fall back to the
                        // channel list bundled with the app so there's something
                        // to watch immediately. Users can still add their own
                        // playlist (URL or file) from Settings at any time.
                        else -> app.playlistRepository.loadDefaultChannels(this@MainActivity)
                    }
                }
                if (usingBundledDefault) {
                    Toast.makeText(
                        this@MainActivity,
                        "Showing the built-in channel list. Add your own playlist in Settings.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                allChannels = channels
                channelNumbers = channels.mapIndexed { i, c -> c.key to (i + 1) }.toMap()
                setupCategories()
                applyFilter()

                // EPG is optional; load it quietly in the background if configured.
                val epgUrl = app.settings.epgUrl
                if (!epgUrl.isNullOrBlank() && !app.epgRepository.isLoaded) {
                    launch(Dispatchers.IO) {
                        runCatching { app.epgRepository.loadFromUrl(epgUrl) }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Failed to load playlist: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progress.visibility = android.view.View.GONE
            }
        }
    }

    private fun setupCategories() {
        val groups = app.playlistRepository.groupedByCategory(allChannels).keys.toList()
        val categories = listOf(CATEGORY_ALL, CATEGORY_FAVORITES) + groups
        binding.categoryList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.categoryList.adapter = CategoryAdapter(categories) { selected ->
            currentCategory = selected
            applyFilter()
        }
    }

    private fun applyFilter() {
        var list = when (currentCategory) {
            CATEGORY_ALL -> allChannels
            CATEGORY_FAVORITES -> {
                val favKeys = app.favorites.favoriteKeys().toSet()
                allChannels.filter { it.key in favKeys }
            }
            else -> allChannels.filter { it.group == currentCategory }
        }
        if (currentQuery.isNotBlank()) {
            list = list.filter { it.name.contains(currentQuery, ignoreCase = true) }
        }
        channelAdapter.submit(list)
        binding.emptyState.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun toggleFavorite(channel: Channel) {
        val nowFav = app.favorites.toggleFavorite(channel.key)
        Toast.makeText(
            this,
            if (nowFav) "Added ${channel.name} to Favorites" else "Removed ${channel.name} from Favorites",
            Toast.LENGTH_SHORT
        ).show()
        applyFilter()
    }

    private fun openPlayer(channel: Channel) {
        app.favorites.addRecent(channel.key)
        app.settings.lastChannelKey = channel.key
        val currentList = channelAdapter.let { allCurrentlyShown() }
        val index = currentList.indexOfFirst { it.key == channel.key }.coerceAtLeast(0)
        startActivity(
            Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_CHANNEL_LIST, ArrayList(currentList))
                putExtra(PlayerActivity.EXTRA_START_INDEX, index)
            }
        )
    }

    /** The channel list currently on screen (post category+search filter), used for ch+/ch- in the player. */
    private fun allCurrentlyShown(): List<Channel> {
        var list = when (currentCategory) {
            CATEGORY_ALL -> allChannels
            CATEGORY_FAVORITES -> {
                val favKeys = app.favorites.favoriteKeys().toSet()
                allChannels.filter { it.key in favKeys }
            }
            else -> allChannels.filter { it.group == currentCategory }
        }
        if (currentQuery.isNotBlank()) list = list.filter { it.name.contains(currentQuery, ignoreCase = true) }
        return list
    }
}
