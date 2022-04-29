package xyz.rodit.snapmod.features

import xyz.rodit.xposed.client.ConfigurationClient
import xyz.rodit.xposed.client.FileClient
import xyz.rodit.xposed.client.http.StreamServer
import android.app.Activity
import android.content.Context
import xyz.rodit.snapmod.features.FeatureContext
import xyz.rodit.snapmod.util.ConversationManager

class FeatureContext(
    val appContext: Context,
    val classLoader: ClassLoader,
    val config: ConfigurationClient,
    val files: FileClient,
    val server: StreamServer
) {
    val pinned: ConversationManager = ConversationManager(appContext.filesDir, PINNED_CONVERSATIONS_FILE)
    val stealth: ConversationManager = ConversationManager(appContext.filesDir, STEALTH_CONVERSATIONS_FILE)

    var activity: Activity? = null

    companion object {
        private const val PINNED_CONVERSATIONS_FILE = "pinned.list"
        private const val STEALTH_CONVERSATIONS_FILE = "stealth.list"
    }
}