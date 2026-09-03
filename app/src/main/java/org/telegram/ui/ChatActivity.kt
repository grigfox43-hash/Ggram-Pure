package org.telegram.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.telegram.messenger.MessagesController
import org.telegram.messenger.PureChat
import org.telegram.messenger.PureMessage
import org.telegram.messenger.R
import org.telegram.messenger.translate.TelegramTranslator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ChatActivity - Full Telegram conversation screen in Ggram-Pure.
 * Features 1-tap message translation, 100% AdBlock, and clean Obsidian Emerald theme.
 */
class ChatActivity : AppCompatActivity() {

    private var chatId: Long = 0L
    private var chat: PureChat? = null
    private lateinit var recyclerMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var adapter: MessagesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatId = intent.getLongExtra("chat_id", 0L)
        chat = MessagesController.getChat(chatId)

        setupToolbar()
        setupRecycler()
        setupInput()
    }

    private fun setupToolbar() {
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        val currentChat = chat
        if (currentChat != null) {
            findViewById<TextView>(R.id.chat_title).text = currentChat.title
            findViewById<TextView>(R.id.chat_avatar).text = currentChat.avatarInitials
        }
    }

    private fun setupRecycler() {
        recyclerMessages = findViewById(R.id.recycler_messages)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerMessages.layoutManager = layoutManager

        val messages = MessagesController.getMessages(chatId)
        adapter = MessagesAdapter(messages.toMutableList()) { msg, position ->
            translateMessage(msg, position)
        }
        recyclerMessages.adapter = adapter
    }

    private fun setupInput() {
        etMessage = findViewById(R.id.et_message)
        findViewById<View>(R.id.btn_send).setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                val sent = MessagesController.sendMessage(chatId, text)
                adapter.addMessage(sent)
                recyclerMessages.scrollToPosition(adapter.itemCount - 1)
                etMessage.setText("")
            }
        }
    }

    private fun translateMessage(msg: PureMessage, position: Int) {
        if (msg.translatedText != null) {
            // Toggle off translation
            msg.translatedText = null
            adapter.notifyItemChanged(position)
            return
        }

        lifecycleScope.launch {
            val translated = TelegramTranslator.translateMessage(msg.text, "ru")
            msg.translatedText = translated
            adapter.notifyItemChanged(position)
        }
    }

    class MessagesAdapter(
        private val items: MutableList<PureMessage>,
        private val onTranslateClick: (PureMessage, Int) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_IN = 1
            private const val TYPE_OUT = 2
        }

        override fun getItemViewType(position: Int): Int {
            return if (items[position].isOutgoing) TYPE_OUT else TYPE_IN
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_OUT) {
                val v = inflater.inflate(R.layout.item_message_out, parent, false)
                OutViewHolder(v)
            } else {
                val v = inflater.inflate(R.layout.item_message_in, parent, false)
                InViewHolder(v)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val msg = items[position]
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))

            if (holder is OutViewHolder) {
                holder.tvText.text = msg.text
                holder.tvTime.text = timeStr
            } else if (holder is InViewHolder) {
                holder.tvSender.text = msg.senderName
                holder.tvText.text = msg.text
                holder.tvTime.text = timeStr

                if (msg.translatedText != null) {
                    holder.layoutTranslate.visibility = View.VISIBLE
                    holder.tvTranslated.text = msg.translatedText
                    holder.btnTranslate.text = "✖ Скрыть перевод"
                } else {
                    holder.layoutTranslate.visibility = View.GONE
                    holder.btnTranslate.text = "🌐 Перевести"
                }

                holder.btnTranslate.setOnClickListener {
                    onTranslateClick(msg, holder.bindingAdapterPosition)
                }
            }
        }

        override fun getItemCount(): Int = items.size

        fun addMessage(msg: PureMessage) {
            items.add(msg)
            notifyItemInserted(items.size - 1)
        }

        class OutViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvText: TextView = v.findViewById(R.id.msg_text)
            val tvTime: TextView = v.findViewById(R.id.msg_time)
        }

        class InViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvSender: TextView = v.findViewById(R.id.msg_sender)
            val tvText: TextView = v.findViewById(R.id.msg_text)
            val tvTime: TextView = v.findViewById(R.id.msg_time)
            val btnTranslate: TextView = v.findViewById(R.id.btn_translate)
            val layoutTranslate: LinearLayout = v.findViewById(R.id.layout_translation)
            val tvTranslated: TextView = v.findViewById(R.id.tv_translated_text)
        }
    }
}
