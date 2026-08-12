package com.dgmltn.dpad.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.dgmltn.dpad.ui.devices.DevicesScreen
import com.dgmltn.dpad.ui.remote.RemoteScreen
import com.dgmltn.dpad.ui.shortcuts.ShortcutsScreen

/**
 * Owns the Navigation-3 back stack for the app. [Route.Remote] is the root; [Route.Devices] and
 * [Route.Shortcuts] are pushed on top and popped via their own `onBack`.
 */
@Composable
fun AppNavHost() {
    val backStack = remember { NavBackStack<Route>(Route.Remote) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Route.Remote> {
                RemoteScreen(
                    onOpenDevices = { backStack.add(Route.Devices) },
                    onEditShortcuts = { backStack.add(Route.Shortcuts) },
                )
            }
            entry<Route.Devices> {
                DevicesScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<Route.Shortcuts> {
                ShortcutsScreen(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
