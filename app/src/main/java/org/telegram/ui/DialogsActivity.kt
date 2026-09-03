package org.telegram.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import org.telegram.messenger.MessagesController
import org.telegram.messenger.PureChat
import org.telegram.messenger.R

/**
 * DialogsActivity - Main Telegram chat list screen in Ggram-Pure.
 */
class DialogsActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var recyclerDialogs: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var adapter: DialogsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialogs)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)
        recyclerDialogs = findViewById(R.id.recycler_dialogs)
        swipeRefresh = findViewById(R.id.swipe_refresh)

        setupToolbar()
        setupDrawer()
        setupRecycler()
    }

    override fun onResume() {
        super.onResume()
        adapter.update(MessagesController.getDialogs())
    }

    private fun setupToolbar() {
        findViewById<ImageView>(R.id.btn_menu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        findViewById<View>(R.id.badge_adblock).setOnClickListener {
            showToast("100% AdBlock активен: вся реклама и спонсорские посты Telegram заблокированы.")
        }
    }

    private fun setupDrawer() {
        navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_adblock -> {
                    showToast("100% AdBlock включен по умолчанию.")
                    true
                }
                R.id.nav_translate -> {
                    showToast("Переводчик сообщений активен в каждом диалоге.")
                    true
                }
                R.id.nav_saved -> {
                    openChat(103L)
                    true
                }
                else -> {
                    showToast(item.title.toString())
                    true
                }
            }
        }
    }

    private fun setupRecycler() {
        recyclerDialogs.layoutManager = LinearLayoutManager(this)
        adapter = DialogsAdapter(MessagesController.getDialogs()) { chat ->
            openChat(chat.id)
        }
        recyclerDialogs.adapter = adapter

        swipeRefresh.setColorSchemeColors(0xFF01BA53.toInt())
        swipeRefresh.setOnRefreshListener {
            adapter.update(MessagesController.getDialogs())
            swipeRefresh.isRefreshing = false
        }
    }

    private fun openChat(chatId: Long) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chat_id", chatId)
        }
        startActivity(intent)
    }

    private fun showToast(text: String) {
        val view = findViewById<View>(android.R.id.content)
        Snackbar.make(view, text, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(0xFF01BA53.toInt())
            .setTextColor(0xFFFFFFFF.toInt())
            .show()
    }

    class DialogsAdapter(
        private var items: List<PureChat>,
        private val onClick: (PureChat) -> Unit
    ) : RecyclerView.Adapter<DialogsAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvAvatar: TextView = v.findViewById(R.id.dialog_avatar_text)
            val tvTitle: TextView = v.findViewById(R.id.dialog_title)
            val tvTime: TextView = v.findViewById(R.id.dialog_time)
            val tvLastMessage: TextView = v.findViewById(R.id.dialog_last_message)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_dialog, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val chat = items[position]
            holder.tvAvatar.text = chat.avatarInitials
            holder.tvTitle.text = chat.title
            holder.tvTime.text = chat.lastMessageTime
            holder.tvLastMessage.text = chat.lastMessage
            holder.itemView.setOnClickListener { onClick(chat) }
        }

        override fun getItemCount(): Int = items.size

        fun update(newItems: List<PureChat>) {
            items = newItems
            notifyDataSetChanged()
        }
    }
}
