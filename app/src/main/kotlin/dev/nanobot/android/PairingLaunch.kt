package dev.nanobot.android

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds a pairing link that arrived through a `nanobot://pair` intent until the ViewModel is ready.
 * A StateFlow is used so a cold-start intent is not lost when the ViewModel is created later.
 */
object PairingLaunch {
    private val _links = MutableStateFlow<String?>(null)
    val links: StateFlow<String?> = _links.asStateFlow()

    fun offer(link: String) {
        if (link.isNotBlank()) _links.value = link
    }

    fun consume() {
        _links.value = null
    }

    fun from(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "nanobot" && data.host == "pair") {
            offer(data.toString())
        }
    }
}
