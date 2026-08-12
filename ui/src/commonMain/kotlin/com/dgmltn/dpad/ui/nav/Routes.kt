package com.dgmltn.dpad.ui.nav

import androidx.navigation3.runtime.NavKey

/**
 * Navigation-3 route keys for [AppNavHost].
 *
 * [NavKey] is a plain marker interface (see `androidx.navigation3.runtime.NavKey`, pinned
 * navigation3-runtime 1.1.5) — it does not itself require `@Serializable`. Serialization is only
 * needed by the `rememberNavBackStack(SavedStateConfiguration, vararg NavKey)` overload, which
 * saves the stack across process death. [AppNavHost] doesn't use that overload (it builds the
 * back stack directly via `NavBackStack(Route.Remote)`), so these routes are left unannotated.
 */
sealed interface Route : NavKey {
    data object Remote : Route
    data object Devices : Route
    data object Shortcuts : Route
}
