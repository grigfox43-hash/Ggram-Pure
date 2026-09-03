package org.telegram.messenger

import android.app.Application

/**
 * ApplicationLoader - Standard Telegram application entry point.
 */
class ApplicationLoader : Application() {

    companion object {
        lateinit var instance: ApplicationLoader
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        MessagesController.init(this)
    }
}
