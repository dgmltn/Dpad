package com.dgmltn.dpad.domain

/** A configurable app-launch button. [appLinkUrl] is what the TV's RemoteAppLinkLaunchRequest receives. */
data class Shortcut(val id: String, val label: String, val appLinkUrl: String)
