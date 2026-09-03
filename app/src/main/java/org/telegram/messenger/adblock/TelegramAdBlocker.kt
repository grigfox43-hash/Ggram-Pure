package org.telegram.messenger.adblock

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

/**
 * TelegramAdBlocker - Built-in AdBlock engine for clean Telegram client.
 * 1. Blocks official MTProto sponsored messages.
 * 2. Filters promotional / sponsored channel posts via heuristic regex.
 * 3. Suppresses Telegram Premium upsell dialogs and star popups.
 */
object TelegramAdBlocker {

    private const val TAG = "TelegramAdBlocker"
    val blockedAdsCount = AtomicInteger(0)

    // Using Unicode escape sequences for 100% charset safety across all devices
    // \u0440\u0435\u043a\u043b\u0430\u043c\u0430 = реклама, \u043f\u0430\u0440\u0442\u043d\u0435\u0440\u0441\u043a\u0438\u0439 = партнерский
    // \u0441\u043a\u0438\u0434\u043a = скидк, \u043f\u0440\u043e\u043c\u043e\u043a\u043e\u0434 = промокод, \u0430\u043a\u0446\u0438\u044f = акция
    private val AD_HEURISTIC_PATTERNS = listOf(
        Pattern.compile("(?i)#(?:\u0440\u0435\u043a\u043b\u0430\u043c\u0430|ad|sponsored|\u043f\u0430\u0440\u0442\u043d\u0435\u0440\u0441\u043a\u0438\u0439)"),
        Pattern.compile("(?i)(?:erid:|erid\\s*=)"),
        Pattern.compile("(?i)(?:\u0441\u043a\u0438\u0434\u043a[\u0430-\u044f]|\u043f\u0440\u043e\u043c\u043e\u043a\u043e\u0434|\u0430\u043a\u0446\u0438\u044f|\u0432\u044b\u0433\u043e\u0434\u043d[\u0430-\u044f])"),
        Pattern.compile("(?i)t\\.me/\\+[A-Za-z0-9_]{10,}")
    )

    fun init(context: Context) {
        Log.i(TAG, "TelegramAdBlocker initialized with 100% Anti-Ad engine")
    }

    /**
     * Intercepts TL_messages_sponsoredMessages responses from MTProto.
     * Always returns true in this clean fork to drop sponsored messages.
     */
    fun shouldBlockSponsoredMessages(): Boolean {
        blockedAdsCount.incrementAndGet()
        Log.d(TAG, "Blocked native Telegram sponsored message")
        return true
    }

    /**
     * Heuristic analysis for channel posts.
     * Returns true if post contains promotional markers.
     */
    fun isPromotionalPost(messageText: String?): Boolean {
        if (messageText.isNullOrEmpty()) return false

        for (pattern in AD_HEURISTIC_PATTERNS) {
            if (pattern.matcher(messageText).find()) {
                blockedAdsCount.incrementAndGet()
                Log.d(TAG, "Filtered promotional channel post: ${messageText.take(30)}...")
                return true
            }
        }
        return false
    }

    /**
     * Suppresses Telegram Premium popups and Stars upsell dialogs.
     */
    fun shouldSuppressUpsell(dialogTag: String): Boolean {
        val isUpsell = dialogTag.contains("premium", ignoreCase = true) ||
                       dialogTag.contains("stars", ignoreCase = true) ||
                       dialogTag.contains("gift", ignoreCase = true)
        if (isUpsell) {
            blockedAdsCount.incrementAndGet()
            Log.d(TAG, "Suppressed Telegram upsell dialog: $dialogTag")
        }
        return isUpsell
    }
}
