package com.iptv.player.data

import android.content.Context

/**
 * Stores favorite channel keys and a small recently-watched list.
 * Uses a delimited string instead of a Set<String> so insertion order is preserved
 * (SharedPreferences string sets do NOT guarantee order).
 */
class FavoritesManager(context: Context) {
    private val prefs = context.getSharedPreferences("iptv_favorites", Context.MODE_PRIVATE)

    fun isFavorite(key: String): Boolean = readList(KEY_FAVORITES).contains(key)

    fun toggleFavorite(key: String): Boolean {
        val list = readList(KEY_FAVORITES).toMutableList()
        val nowFavorite: Boolean
        if (list.contains(key)) {
            list.remove(key)
            nowFavorite = false
        } else {
            list.add(0, key)
            nowFavorite = true
        }
        writeList(KEY_FAVORITES, list)
        return nowFavorite
    }

    fun favoriteKeys(): List<String> = readList(KEY_FAVORITES)

    fun addRecent(key: String) {
        val list = readList(KEY_RECENT).toMutableList()
        list.remove(key)
        list.add(0, key)
        while (list.size > MAX_RECENT) list.removeAt(list.lastIndex)
        writeList(KEY_RECENT, list)
    }

    fun recentKeys(): List<String> = readList(KEY_RECENT)

    private fun readList(key: String): List<String> {
        val raw = prefs.getString(key, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(DELIM)
    }

    private fun writeList(key: String, list: List<String>) {
        prefs.edit().putString(key, list.joinToString(DELIM)).apply()
    }

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENT = "recent"
        private const val DELIM = "\u0001"
        private const val MAX_RECENT = 20
    }
}
