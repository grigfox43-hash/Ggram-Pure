package org.ggram.ui.diff;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.ggram.antirecall.GgramAntiRecallManager;
import org.ggram.antirecall.GgramAntiRecallManager.MessageEditRecord;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * GgramDiffActivity - Interactive viewer for message revision and edit history.
 */
public class GgramDiffActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF18191D);

        // Header / Title bar
        TextView title = new TextView(this);
        title.setText("История изменений сообщения [Изм.]");
        title.setTextSize(18f);
        title.setTextColor(0xFF01BA53);
        title.setPadding(40, 40, 40, 20);
        root.addView(title);

        long messageId = getIntent().getLongExtra("extra_message_id", 0L);
        List<MessageEditRecord> revisions = GgramAntiRecallManager.getEditHistory(messageId);

        ListView listView = new ListView(this);
        listView.setBackgroundColor(0xFF18191D);
        listView.setDivider(null);
        listView.setAdapter(new DiffAdapter(revisions));
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);
    }

    private class DiffAdapter extends BaseAdapter {
        private final List<MessageEditRecord> items;
        private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault());

        DiffAdapter(List<MessageEditRecord> items) {
            this.items = items;
        }

        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout itemLayout;
            TextView tvRev, tvText;
            if (convertView == null) {
                itemLayout = new LinearLayout(GgramDiffActivity.this);
                itemLayout.setOrientation(LinearLayout.VERTICAL);
                itemLayout.setPadding(40, 24, 40, 24);
                itemLayout.setBackgroundColor(0xFF0A0F0C);

                tvRev = new TextView(GgramDiffActivity.this);
                tvRev.setTextSize(14f);
                tvRev.setTextColor(0xFF01BA53);
                itemLayout.addView(tvRev);

                tvText = new TextView(GgramDiffActivity.this);
                tvText.setTextSize(16f);
                tvText.setTextColor(0xFFFFFFFF);
                tvText.setPadding(0, 10, 0, 0);
                itemLayout.addView(tvText);
            } else {
                itemLayout = (LinearLayout) convertView;
                tvRev = (TextView) itemLayout.getChildAt(0);
                tvText = (TextView) itemLayout.getChildAt(1);
            }

            MessageEditRecord rec = items.get(position);
            String timeStr = sdf.format(new Date(rec.timestamp));
            tvRev.setText("Ревизия #" + rec.revisionId + " • " + timeStr);
            tvText.setText("До:   \"" + rec.previousText + "\"\nПосле: \"" + rec.newText + "\"");

            return itemLayout;
        }
    }
}
