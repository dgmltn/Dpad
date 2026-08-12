package com.dgmltn.dpad.data

import com.dgmltn.dpad.data.store.tempDataStore
import com.dgmltn.dpad.domain.Shortcut
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ShortcutRepositoryImplTest {
    private fun repo() = ShortcutRepositoryImpl(tempDataStore())
    private fun sc(id: String, label: String = id) = Shortcut(id = id, label = label, appLinkUrl = "https://x/$id")

    @Test fun startsEmpty() = runTest { assertEquals(emptyList(), repo().shortcuts.first()) }

    @Test fun addAppendsInOrder() = runTest {
        val r = repo()
        r.add(sc("a")); r.add(sc("b"))
        assertEquals(listOf("a", "b"), r.shortcuts.first().map { it.id })
    }

    @Test fun removeDeletesById() = runTest {
        val r = repo()
        r.add(sc("a")); r.add(sc("b"))
        r.remove("a")
        assertEquals(listOf("b"), r.shortcuts.first().map { it.id })
    }

    @Test fun reorderAppliesNewOrder() = runTest {
        val r = repo()
        r.add(sc("a")); r.add(sc("b")); r.add(sc("c"))
        r.reorder(listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), r.shortcuts.first().map { it.id })
    }

    @Test fun reorderIgnoresUnknownIdsAndKeepsOmittedAtEnd() = runTest {
        val r = repo()
        r.add(sc("a")); r.add(sc("b"))
        r.reorder(listOf("b", "ghost"))   // ghost isn't a shortcut; a is omitted from the order
        assertEquals(listOf("b", "a"), r.shortcuts.first().map { it.id })  // omitted 'a' retained, appended
    }
}
