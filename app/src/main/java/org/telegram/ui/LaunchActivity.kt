package org.telegram.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import org.telegram.messenger.R

/**
 * LaunchActivity - Main Telegram launch activity.
 */
class LaunchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, DialogsActivity::class.java))
            finish()
        }, 600)
    }
}
