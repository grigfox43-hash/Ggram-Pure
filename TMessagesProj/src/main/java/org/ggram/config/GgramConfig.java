package org.ggram.config;

import android.content.Context;
import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

/**
 * GgramConfig - Central configuration and persistent settings for Ggram Pure.
 */
public class GgramConfig {

    private static final String PREFS_NAME = "ggram_settings";
    private static SharedPreferences preferences;

    // Ghost Mode
    public static boolean isGhostDontSendRead = true;
    public static boolean isGhostDontSendTyping = true;
    public static boolean isGhostHideStoriesSeen = true;

    public static boolean isGhostMasterEnabled() {
        return isGhostDontSendRead && isGhostDontSendTyping && isGhostHideStoriesSeen;
    }

    public static void setGhostModeMaster(boolean enabled) {
        isGhostDontSendRead = enabled;
        isGhostDontSendTyping = enabled;
        isGhostHideStoriesSeen = enabled;
        SharedPreferences prefs = getPrefs();
        if (prefs != null) {
            prefs.edit()
                    .putBoolean("ghost_no_read", enabled)
                    .putBoolean("ghost_no_typing", enabled)
                    .putBoolean("ghost_no_stories", enabled)
                    .apply();
        }
    }

    // Forwarding & Text
    public static boolean isForwardNoAuthors = true;
    public static boolean isForwardNoCaptions = false;
    public static boolean isPartialSelectionEnabled = true;
    public static boolean isCopyMarkdown = true;

    // Media & Voice
    public static boolean isConfirmVoiceNotes = true;
    public static boolean isSaveRoundVideosAsMp4 = true;
    public static boolean isVoiceToTextEnabled = true;
    public static boolean isAutoProxyEnabled = true;

    // Protection & Bypass
    public static boolean isNoForwardsBypassEnabled = true;
    public static boolean isFlagSecureBypassEnabled = true;
    public static boolean isAntiRecallDeleted = true;
    public static boolean isAntiRecallEdits = true;
    public static boolean isAntiRecallMedia = true;

    // Chats & Ergonomics
    public static boolean isUnlimitedPins = true;
    public static boolean isConfirmDialogDelete = true;
    public static boolean isHideStories = false;
    public static boolean isAdBlockEnabled = true;
    public static boolean isShowMetadataDetails = true;
    public static boolean isHideBottomBar = false;

    static {
        loadConfig();
    }

    private static SharedPreferences getPrefs() {
        if (preferences == null && ApplicationLoader.applicationContext != null) {
            preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
        return preferences;
    }

    public static void loadConfig() {
        SharedPreferences prefs = getPrefs();
        if (prefs == null) return;

        isGhostDontSendRead = prefs.getBoolean("ghost_no_read", true);
        isGhostDontSendTyping = prefs.getBoolean("ghost_no_typing", true);
        isGhostHideStoriesSeen = prefs.getBoolean("ghost_no_stories", true);

        isForwardNoAuthors = prefs.getBoolean("fwd_no_authors", true);
        isForwardNoCaptions = prefs.getBoolean("fwd_no_captions", false);
        isPartialSelectionEnabled = prefs.getBoolean("partial_selection", true);
        isCopyMarkdown = prefs.getBoolean("copy_markdown", true);

        isConfirmVoiceNotes = prefs.getBoolean("confirm_voice", true);
        isSaveRoundVideosAsMp4 = prefs.getBoolean("save_round_mp4", true);
        isVoiceToTextEnabled = prefs.getBoolean("voice_to_text", true);
        isAutoProxyEnabled = prefs.getBoolean("auto_proxy", true);

        isNoForwardsBypassEnabled = prefs.getBoolean("no_forwards_bypass", true);
        isFlagSecureBypassEnabled = prefs.getBoolean("flag_secure_bypass", true);
        isAntiRecallDeleted = prefs.getBoolean("antirecall_deleted", true);
        isAntiRecallEdits = prefs.getBoolean("antirecall_edits", true);
        isAntiRecallMedia = prefs.getBoolean("antirecall_media", true);

        isUnlimitedPins = prefs.getBoolean("unlimited_pins", true);
        isConfirmDialogDelete = prefs.getBoolean("confirm_delete", true);
        isHideStories = prefs.getBoolean("hide_stories", false);
        isAdBlockEnabled = prefs.getBoolean("adblock_enabled", true);
        isShowMetadataDetails = prefs.getBoolean("show_metadata", true);
        isHideBottomBar = prefs.getBoolean("hide_bottom_bar", false);
    }

    public static void setHideBottomBar(boolean hide) {
        isHideBottomBar = hide;
        toggle("hide_bottom_bar", hide);
    }

    public static void toggle(String key, boolean val) {
        SharedPreferences prefs = getPrefs();
        if (prefs != null) {
            prefs.edit().putBoolean(key, val).apply();
        }
        loadConfig();
    }
}
