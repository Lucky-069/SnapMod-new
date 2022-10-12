package xyz.rodit.snapmod.features.chatmenu.shared

import android.app.AlertDialog
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import xyz.rodit.snapmod.createDummyProxy
import xyz.rodit.snapmod.features.FeatureContext
import xyz.rodit.snapmod.mappings.*
import xyz.rodit.snapmod.util.toSnapUUID
import xyz.rodit.snapmod.util.toUUIDString
import xyz.rodit.snapmod.arroyo.followProtoString
import xyz.rodit.snapmod.logging.log

fun previewChat(context: FeatureContext, key: String) {
    val uuid = key.toSnapUUID()
    val proxy =
        ConversationDummyInterface.wrap(
            ConversationDummyInterface.getMappedClass().createDummyProxy(context.classLoader)
        )

    context.callbacks.on(
        DefaultFetchConversationCallback::class,
        DefaultFetchConversationCallback.onFetchConversationWithMessagesComplete
    ) { displayPreview(context, it) }

    context.instances.conversationManager.fetchConversationWithMessages(
        uuid,
        DefaultFetchConversationCallback(proxy, uuid, false)
    )
}

private fun displayPreview(context: FeatureContext, param: XC_MethodHook.MethodHookParam): Boolean {
    val conversation = Conversation.wrap(param.args[0])

    val userIds = conversation.participants.map(Participant::wrap)
        .map { p -> (p.participantId.id as ByteArray).toUUIDString() }
    val friendData = context.instances.friendsRepository.selectFriendsByUserIds(userIds)
    val userMap = friendData.map(SelectFriendsByUserIds::wrap).associateBy { u -> u.userId }

    val messageList = param.args[1] as List<*>
    val previewText = StringBuilder()
    if (messageList.isEmpty()) previewText.append("No messages available.")
    else {
        val numMessages =
            Integer.min(context.config.getInt("preview_messages_count", 20), messageList.size)
        previewText.append("Last ").append(numMessages).append(" messages:")
        messageList.takeLast(numMessages)
            .map(Message::wrap).forEach { m ->
                run {
                    val uuidString = m.senderId.toUUIDString()
                    val displayName = userMap[uuidString]?.displayName ?: "Unknown"
                    previewText.append('\n').append(displayName).append(": ")
                    if (m.messageContent.contentType.instance == ContentType.CHAT().instance) {
                        previewText.append(followProtoString(m.messageContent.content as ByteArray, 2, 1))
                    } else {
                        previewText.append(m.messageContent.contentType.instance)
                    }
                }
            }
    }

    /*
    userMap.values.find { f -> (f.streakExpiration ?: 0L) > 0L }?.let { f ->
        val hourDiff =
            (f.streakExpiration - System.currentTimeMillis()).toDouble() / 3600000.0
        previewText.append("\n\nStreak Expires in ")
            .append(String.format("%.1f", hourDiff))
            .append(" hours")
    }

     */

    context.activity?.runOnUiThread {
        AlertDialog.Builder(context.activity)
            .setTitle(if (conversation.title.isNullOrBlank()) "Chat Preview" else conversation.title)
            .setMessage(previewText)
            .setPositiveButton("Ok") { _, _ -> }
            .show()
    }

    return true
}