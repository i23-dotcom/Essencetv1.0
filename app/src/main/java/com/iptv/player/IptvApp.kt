package com.iptv.player

import android.app.Application
import com.iptv.player.data.EpgRepository
import com.iptv.player.data.FavoritesManager
import com.iptv.player.data.PlaylistRepository
import com.iptv.player.data.SettingsStore

class IptvApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var favorites: FavoritesManager
        private set
    lateinit var playlistRepository: PlaylistRepository
        private set
    lateinit var epgRepository: EpgRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        favorites = FavoritesManager(this)
        playlistRepository = PlaylistRepository()
        epgRepository = EpgRepository()
    }

    companion object {
        fun from(context: android.content.Context): IptvApp =
            context.applicationContext as IptvApp
    }
}
