package com.dgmltn.dpad.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path

/** Builds a Preferences DataStore at [path]. Platform code (Plan 3) supplies the path. */
fun createDataStore(path: Path): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path })
