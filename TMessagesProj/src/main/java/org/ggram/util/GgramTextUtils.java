package org.ggram.util;

import android.text.TextUtils;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.Collections;

/**
 * GgramTextUtils - Converts Telegram message entities to Markdown format.
 */
public class GgramTextUtils {

    public static CharSequence toMarkdown(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return "";
        }
        String text = messageObject.messageOwner.message;
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        ArrayList<TLRPC.MessageEntity> entities = messageObject.messageOwner.entities;
        if (entities == null || entities.isEmpty()) {
            return text;
        }

        // Sort entities in reverse order of offset so insertion doesn't invalidate earlier offsets
        ArrayList<TLRPC.MessageEntity> sorted = new ArrayList<>(entities);
        Collections.sort(sorted, (e1, e2) -> Integer.compare(e2.offset, e1.offset));

        StringBuilder sb = new StringBuilder(text);
        for (TLRPC.MessageEntity e : sorted) {
            int start = e.offset;
            int end = e.offset + e.length;
            if (start < 0 || end > sb.length() || start >= end) {
                continue;
            }

            if (e instanceof TLRPC.TL_messageEntityBold) {
                sb.insert(end, "**");
                sb.insert(start, "**");
            } else if (e instanceof TLRPC.TL_messageEntityItalic) {
                sb.insert(end, "*");
                sb.insert(start, "*");
            } else if (e instanceof TLRPC.TL_messageEntityCode) {
                sb.insert(end, "`");
                sb.insert(start, "`");
            } else if (e instanceof TLRPC.TL_messageEntityPre) {
                sb.insert(end, "```");
                sb.insert(start, "```\n");
            } else if (e instanceof TLRPC.TL_messageEntityStrike) {
                sb.insert(end, "~~");
                sb.insert(start, "~~");
            } else if (e instanceof TLRPC.TL_messageEntitySpoiler) {
                sb.insert(end, "||");
                sb.insert(start, "||");
            } else if (e instanceof TLRPC.TL_messageEntityTextUrl) {
                String url = ((TLRPC.TL_messageEntityTextUrl) e).url;
                if (!TextUtils.isEmpty(url)) {
                    sb.insert(end, "](" + url + ")");
                    sb.insert(start, "[");
                }
            }
        }
        return sb.toString();
    }
}
