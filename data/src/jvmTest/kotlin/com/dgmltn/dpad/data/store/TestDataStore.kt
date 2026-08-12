package com.dgmltn.dpad.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import java.nio.file.Files

/** A DataStore backed by a fresh temp file — one per call, so tests don't share state. */
fun tempDataStore(): DataStore<Preferences> {
    val file = Files.createTempFile("dpad-test", ".preferences_pb").toFile()
    file.delete()   // DataStore must create it itself
    return createDataStore(file.absolutePath.toPath())
}
