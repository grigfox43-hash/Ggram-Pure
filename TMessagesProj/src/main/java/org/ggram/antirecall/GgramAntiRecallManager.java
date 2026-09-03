package org.ggram.antirecall;

import android.util.Log;

import org.ggram.config.GgramConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GgramAntiRecallManager - Manages retention of deleted messages, edit history, and disappearing media.
 */
public class GgramAntiRecallManager {

    private static final String TAG = "GgramAntiRecall";

    public static class DeletedMessageRecord {
        public long messageId;
        public long chatId;
        public String senderName;
        public String originalText;
        public long deletedTimestamp;

        public DeletedMessageRecord(long messageId, long chatId, String senderName, String originalText, long deletedTimestamp) {
            this.messageId = messageId;
            this.chatId = chatId;
            this.senderName = senderName;
            this.originalText = originalText;
            this.deletedTimestamp = deletedTimestamp;
        }
    }

    public static class MessageEditRecord {
        public int revisionId;
        public String previousText;
        public String newText;
        public long timestamp;

        public MessageEditRecord(int revisionId, String previousText, String newText, long timestamp) {
            this.revisionId = revisionId;
            this.previousText = previousText;
            this.newText = newText;
            this.timestamp = timestamp;
        }
    }

    private static final ConcurrentHashMap<Long, DeletedMessageRecord> deletedMessagesMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, List<MessageEditRecord>> messageEditsMap = new ConcurrentHashMap<>();

    public static void onMessageDeleted(long chatId, long messageId, String text, String sender) {
        if (!GgramConfig.isAntiRecallDeleted) return;

        DeletedMessageRecord record = new DeletedMessageRecord(
                messageId,
                chatId,
                sender != null ? sender : "Собеседник",
                text != null ? text : "",
                System.currentTimeMillis()
        );
        deletedMessagesMap.put(messageId, record);
        Log.d(TAG, "Preserved deleted message ID " + messageId + " in chat " + chatId);
    }

    public static void onMessageEdited(long messageId, String oldText, String newText) {
        if (!GgramConfig.isAntiRecallEdits) return;

        List<MessageEditRecord> edits = messageEditsMap.computeIfAbsent(messageId, k -> Collections.synchronizedList(new ArrayList<>()));
        MessageEditRecord revision = new MessageEditRecord(
                edits.size() + 1,
                oldText != null ? oldText : "",
                newText != null ? newText : "",
                System.currentTimeMillis()
        );
        edits.add(revision);
        Log.d(TAG, "Stored revision #" + revision.revisionId + " for message " + messageId);
    }

    public static boolean isMessageDeleted(long messageId) {
        return deletedMessagesMap.containsKey(messageId);
    }

    public static DeletedMessageRecord getDeletedRecord(long messageId) {
        return deletedMessagesMap.get(messageId);
    }

    public static List<MessageEditRecord> getEditHistory(long messageId) {
        List<MessageEditRecord> list = messageEditsMap.get(messageId);
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }
}
