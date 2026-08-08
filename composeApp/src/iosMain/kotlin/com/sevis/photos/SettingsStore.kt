package com.sevis.photos

import platform.Foundation.NSUserDefaults

/**
 * iOS's equivalent of MainActivity's "photos_prefs" SharedPreferences — same four
 * keys, same shape, just backed by NSUserDefaults instead.
 */
object SettingsStore {
    private val defaults get() = NSUserDefaults.standardUserDefaults

    private const val KEY_TOKEN = "token"
    private const val KEY_FOLDER_PASSWORD = "folder_password"
    private const val KEY_AUTO_UPLOAD = "auto_upload_enabled"
    private const val KEY_FAVORITES = "favorites"

    /** Restores persisted session state into AppState — call once at startup. */
    fun restore() {
        AppState.token = defaults.stringForKey(KEY_TOKEN)
        AppState.folderPassword = defaults.stringForKey(KEY_FOLDER_PASSWORD)
        AppState.autoUploadEnabled = defaults.boolForKey(KEY_AUTO_UPLOAD)
        val savedFavs = defaults.stringForKey(KEY_FAVORITES) ?: ""
        if (savedFavs.isNotBlank()) {
            savedFavs.split(",").mapNotNull { it.trim().toIntOrNull() }
                .forEach { AppState.favoriteIds.add(it) }
        }
    }

    fun setToken(token: String?) {
        if (token == null) defaults.removeObjectForKey(KEY_TOKEN) else defaults.setObject(token, KEY_TOKEN)
    }

    fun setFolderPassword(password: String?) {
        if (password == null) defaults.removeObjectForKey(KEY_FOLDER_PASSWORD) else defaults.setObject(password, KEY_FOLDER_PASSWORD)
    }

    fun setAutoUploadEnabled(enabled: Boolean) {
        defaults.setBool(enabled, KEY_AUTO_UPLOAD)
    }

    fun setFavorites(ids: Set<Int>) {
        defaults.setObject(ids.joinToString(","), KEY_FAVORITES)
    }
}
