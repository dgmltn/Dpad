package com.dgmltn.dpad.domain

data class CatalogApp(val key: String, val label: String, val appLinkUrl: String)

object ShortcutCatalog {
    val apps: List<CatalogApp> = listOf(
        CatalogApp("netflix", "Netflix", "https://www.netflix.com/title"),
        CatalogApp("youtube", "YouTube", "https://www.youtube.com"),
        CatalogApp("primevideo", "Prime Video", "https://app.primevideo.com"),
        CatalogApp("disneyplus", "Disney+", "https://www.disneyplus.com"),
        CatalogApp("max", "Max", "https://play.max.com"),
        CatalogApp("peacock", "Peacock", "https://www.peacocktv.com"),
        CatalogApp("paramountplus", "Paramount+", "https://www.paramountplus.com"),
        CatalogApp("plex", "Plex", "https://app.plex.tv"),
        CatalogApp("jellyfin", "Jellyfin", "https://jellyfin.org"),
        CatalogApp("spotify", "Spotify", "https://open.spotify.com"),
        CatalogApp("twitch", "Twitch", "https://www.twitch.tv"),
    )

    fun toShortcut(app: CatalogApp, id: String): Shortcut =
        Shortcut(id = id, label = app.label, appLinkUrl = app.appLinkUrl)
}
