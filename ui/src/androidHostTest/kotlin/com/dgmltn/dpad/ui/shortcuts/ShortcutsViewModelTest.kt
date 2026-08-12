package com.dgmltn.dpad.ui.shortcuts

import app.cash.turbine.test
import com.dgmltn.dpad.domain.CatalogApp
import com.dgmltn.dpad.domain.Shortcut
import com.dgmltn.dpad.domain.ShortcutCatalog
import com.dgmltn.dpad.domain.ShortcutRepository
import com.dgmltn.dpad.ui.remote.awaitItemUntil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShortcutsViewModelTest {
    // Hand-written fake — the contract is a small interface.
    private class FakeShortcutRepository(initial: List<Shortcut> = emptyList()) : ShortcutRepository {
        private val _shortcuts = MutableStateFlow(initial)
        override val shortcuts: Flow<List<Shortcut>> = _shortcuts.asStateFlow()
        override suspend fun add(shortcut: Shortcut) {
            _shortcuts.value = _shortcuts.value + shortcut
        }
        override suspend fun remove(id: String) {
            _shortcuts.value = _shortcuts.value.filterNot { it.id == id }
        }
        override suspend fun reorder(orderedIds: List<String>) {
            val byId = _shortcuts.value.associateBy { it.id }
            _shortcuts.value = orderedIds.mapNotNull { byId[it] }
        }
    }

    // Deterministic, incrementing id generator so add-assertions are stable.
    private fun fakeIdGenerator(): () -> String {
        var counter = 0
        return { "id-${++counter}" }
    }

    @Test fun catalogExcludesAlreadyAddedApps() = runTest {
        val netflix = ShortcutCatalog.apps.first { it.key == "netflix" }
        val youtube = ShortcutCatalog.apps.first { it.key == "youtube" }
        val repo = FakeShortcutRepository(listOf(Shortcut("existing", "Netflix", netflix.appLinkUrl)))
        val vm = ShortcutsViewModel(repo, newId = fakeIdGenerator())

        vm.state.test {
            awaitItem() // initial default
            val s = awaitItemUntil { it.shortcuts.isNotEmpty() }
            assertFalse(s.catalog.any { it.appLinkUrl == netflix.appLinkUrl })
            assertTrue(s.catalog.any { it.appLinkUrl == youtube.appLinkUrl })
        }
    }

    @Test fun onAddFromCatalogAddsShortcutWithCatalogLabelUrlAndInjectedId() = runTest {
        val repo = FakeShortcutRepository()
        val vm = ShortcutsViewModel(repo, newId = fakeIdGenerator())
        val app: CatalogApp = ShortcutCatalog.apps.first { it.key == "youtube" }

        vm.state.test {
            awaitItem() // initial default
            awaitItemUntil { it.catalog.isNotEmpty() } // upstream started, nothing saved yet

            vm.onAddFromCatalog(app)

            val s = awaitItemUntil { it.shortcuts.isNotEmpty() }
            val added = s.shortcuts.single()
            assertEquals("id-1", added.id)
            assertEquals(app.label, added.label)
            assertEquals(app.appLinkUrl, added.appLinkUrl)
        }
    }

    @Test fun onRemoveRemovesById() = runTest {
        val repo = FakeShortcutRepository(listOf(Shortcut("a", "A", "https://a"), Shortcut("b", "B", "https://b")))
        val vm = ShortcutsViewModel(repo, newId = fakeIdGenerator())

        vm.state.test {
            awaitItem() // initial default
            awaitItemUntil { it.shortcuts.size == 2 }

            vm.onRemove("a")

            val s = awaitItemUntil { it.shortcuts.size == 1 }
            assertEquals("b", s.shortcuts.single().id)
        }
    }
}
