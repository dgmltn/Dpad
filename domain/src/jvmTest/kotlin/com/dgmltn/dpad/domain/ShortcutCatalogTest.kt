package com.dgmltn.dpad.domain

import kotlin.test.*

class ShortcutCatalogTest {
    @Test fun catalogContainsTheSpecifiedApps() {
        val labels = ShortcutCatalog.apps.map { it.label }.toSet()
        listOf("Netflix", "YouTube", "Prime Video", "Disney+", "Max", "Peacock",
                "Paramount+", "Plex", "Jellyfin", "Spotify", "Twitch").forEach {
            assertTrue(it in labels, "catalog missing $it")
        }
    }

    @Test fun keysAndUrlsAreUnique() {
        assertEquals(ShortcutCatalog.apps.size, ShortcutCatalog.apps.map { it.key }.toSet().size)
        assertEquals(ShortcutCatalog.apps.size, ShortcutCatalog.apps.map { it.appLinkUrl }.toSet().size)
    }

    @Test fun toShortcutCopiesLabelAndUrlAndAssignsId() {
        val app = ShortcutCatalog.apps.first { it.label == "Netflix" }
        val s = ShortcutCatalog.toShortcut(app, id = "sc-1")
        assertEquals("sc-1", s.id)
        assertEquals("Netflix", s.label)
        assertEquals(app.appLinkUrl, s.appLinkUrl)
    }
}
