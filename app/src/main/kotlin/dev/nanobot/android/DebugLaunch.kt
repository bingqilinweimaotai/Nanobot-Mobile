package dev.nanobot.android

object DebugLaunch {
    @Volatile
    var endpoint: String? = null

    @Volatile
    var secret: String? = null

    @Volatile
    var chatId: String? = null

    @Volatile
    var draft: String? = null

    @Volatile
    var openDrawer: Boolean = false
}
