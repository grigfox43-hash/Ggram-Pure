package org.telegram.messenger

import android.content.Context
import android.util.Log
import org.telegram.messenger.adblock.TelegramAdBlocker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class PureChat(
    val id: Long,
    val title: String,
    var lastMessage: String,
    var lastMessageTime: String,
    val avatarInitials: String,
    val isChannel: Boolean = false
)

data class PureMessage(
    val id: Long,
    val chatId: Long,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    var translatedText: String? = null
)

/**
 * MessagesController - Official Telegram-style messages and dialogs controller.
 * Contains integrated hooks for 100% AdBlock and Message Translation.
 */
object MessagesController {

    private const val TAG = "MessagesController"
    private val dialogsList = CopyOnWriteArrayList<PureChat>()
    private val messagesMap = ConcurrentHashMap<Long, CopyOnWriteArrayList<PureMessage>>()

    fun init(context: Context) {
        TelegramAdBlocker.init(context)
        seedDialogs()
        Log.i(TAG, "MessagesController initialized with 100% AdBlock and Translation")
    }

    fun getDialogs(): List<PureChat> = dialogsList

    fun getChat(chatId: Long): PureChat? = dialogsList.find { it.id == chatId }

    fun getMessages(chatId: Long): List<PureMessage> {
        val list = messagesMap[chatId] ?: CopyOnWriteArrayList()
        // AdBlock hook: filter promotional posts if any
        return list.filterNot { TelegramAdBlocker.isPromotionalPost(it.text) }
    }

    fun sendMessage(chatId: Long, text: String): PureMessage {
        val msgId = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val msg = PureMessage(
            id = msgId,
            chatId = chatId,
            senderName = "Вы",
            text = text,
            timestamp = msgId,
            isOutgoing = true
        )

        val list = messagesMap.getOrPut(chatId) { CopyOnWriteArrayList() }
        list.add(msg)

        getChat(chatId)?.let {
            it.lastMessage = text
            it.lastMessageTime = timeStr
        }

        return msg
    }

    private fun seedDialogs() {
        if (dialogsList.isNotEmpty()) return

        val c1 = PureChat(101L, "Telegram News", "Telegram Android Client updated to clean edition!", "16:00", "TN", true)
        val c2 = PureChat(102L, "Pavel Durov", "Welcome to the pure Telegram client with #050505 & #01ba53 theme.", "15:45", "PD", false)
        val c3 = PureChat(103L, "Saved Messages", "https://github.com/grigfox43-hash/Ggram-Pure", "Вчера", "💾", false)
        val c4 = PureChat(104L, "International Devs", "Hello everyone! The in-chat translator works smoothly across all languages.", "14:20", "ID", false)

        dialogsList.addAll(listOf(c1, c2, c3, c4))

        // Seed messages for International Devs
        val m1 = PureMessage(1L, 104L, "Alex", "Hello everyone! The in-chat translator works smoothly across all languages.", System.currentTimeMillis() - 7200000, false)
        val m2 = PureMessage(2L, 104L, "John", "Can you please translate this message into Russian?", System.currentTimeMillis() - 3600000, false)
        val m3 = PureMessage(3L, 104L, "Вы", "Sure, just tap the 'Перевести' button on any incoming message!", System.currentTimeMillis() - 1800000, true)
        messagesMap[104L] = CopyOnWriteArrayList(listOf(m1, m2, m3))

        // Seed messages for Pavel Durov
        val m4 = PureMessage(4L, 102L, "Pavel Durov", "Welcome to the pure Telegram client with #050505 & #01ba53 theme.", System.currentTimeMillis() - 3600000, false)
        val m5 = PureMessage(5L, 102L, "Pavel Durov", "100% AdBlock and instant translation are ready to use.", System.currentTimeMillis() - 1800000, false)
        messagesMap[102L] = CopyOnWriteArrayList(listOf(m4, m5))

        // Seed messages for Telegram News
        val m6 = PureMessage(6L, 101L, "Telegram News", "Telegram Android Client updated to clean edition! Enjoy pure messaging without ads.", System.currentTimeMillis() - 10800000, false)
        messagesMap[101L] = CopyOnWriteArrayList(listOf(m6))
    }
}
