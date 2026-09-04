package org.ggram.ui.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.ggram.config.GgramConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ProxyListActivity;

import java.util.ArrayList;

/**
 * GgramSettingsActivity - Native settings screen for all Ggram Pure capabilities.
 */
public class GgramSettingsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private static class Item {
        final int type; // 0: header, 1: check, 2: info, 3: action
        final String title;
        final String subtitle;
        final String key;
        final boolean checked;

        Item(int type, String title, String subtitle, String key, boolean checked) {
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.key = key;
            this.checked = checked;
        }

        static Item header(String title) {
            return new Item(0, title, null, null, false);
        }

        static Item check(String title, String subtitle, String key, boolean checked) {
            return new Item(1, title, subtitle, key, checked);
        }

        static Item info(String text) {
            return new Item(2, text, null, null, false);
        }

        static Item action(String title, String subtitle) {
            return new Item(3, title, subtitle, null, false);
        }
    }

    private final ArrayList<Item> items = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    private void updateRows() {
        items.clear();

        // 1. Ghost Mode
        items.add(Item.header("РЕЖИМ НЕВИДИМКИ (GHOST MODE)"));
        items.add(Item.check("Общий режим невидимки", "Включить/выключить все функции скрытности разом", "ghost_master", GgramConfig.isGhostMasterEnabled()));
        items.add(Item.check("Не отправлять статус «Прочитано»", "Собеседник не видит 2 галочки при чтении вами сообщений", "ghost_no_read", GgramConfig.isGhostDontSendRead));
        items.add(Item.check("Скрывать статус набора текста", "Не отправлять «Печатает...», «Записывает голосовое/видео»", "ghost_no_typing", GgramConfig.isGhostDontSendTyping));
        items.add(Item.check("Скрытый просмотр историй (Stories)", "Не отображаться в списке зрителей чужих историй", "ghost_no_stories", GgramConfig.isGhostHideStoriesSeen));
        items.add(Item.info("Позволяет незаметно просматривать сообщения, каналы и истории."));

        // 2. Forwarding & Text
        items.add(Item.header("ПЕРЕСЫЛКА И ТЕКСТ"));
        items.add(Item.check("Пересылка без автора", "Отправка сообщений без плашки «Переслано от...»", "fwd_no_authors", GgramConfig.isForwardNoAuthors));
        items.add(Item.check("Пересылка без подписи к медиа", "Удалять текст подписи при пересылке картинок и видео", "fwd_no_captions", GgramConfig.isForwardNoCaptions));
        items.add(Item.check("Частичное выделение текста", "Выделение и копирование любого слова или фрагмента сообщения", "partial_selection", GgramConfig.isPartialSelectionEnabled));
        items.add(Item.check("Копирование в Markdown", "Копировать форматированный текст (жирный, курсив, код, ссылки)", "copy_markdown", GgramConfig.isCopyMarkdown));
        items.add(Item.info("Удобное управление текстом и анонимное цитирование материалов."));

        // 3. Media & Audio
        items.add(Item.header("ГОЛОСОВЫЕ, ВИДЕО И МЕДИА"));
        items.add(Item.check("Подтверждение голосовых и кружочков", "Предпросмотр, прослушивание и подтверждение перед отправкой", "confirm_voice", GgramConfig.isConfirmVoiceNotes));
        items.add(Item.check("Сохранение кружочков как видео (.mp4)", "Скачивание видеокружков в системную галерею смартфона", "save_round_mp4", GgramConfig.isSaveRoundVideosAsMp4));
        items.add(Item.check("Бесплатная расшифровка речи", "Автономное офлайн-распознавание Vosk без интернета и Premium", "voice_to_text", GgramConfig.isVoiceToTextEnabled));
        items.add(Item.check("Бесшумный граббер медиа", "Сохранение самоуничтожающихся фото и видео в Vault", "antirecall_media", GgramConfig.isAntiRecallMedia));
        items.add(Item.info("Защита от случайной отправки оговорок и расширенные возможности медиа."));

        // 4. Protection & Censorship
        items.add(Item.header("АНТИ-ЦЕНЗУРА И ЗАЩИТА"));
        items.add(Item.check("Снятие защиты FLAG_SECURE", "Разрешить скриншоты и запись экрана в секретных чатах и Stories", "flag_secure_bypass", GgramConfig.isFlagSecureBypassEnabled));
        items.add(Item.check("Обход ограничений noforwards", "Разрешить копирование текста и медиа из защищенных каналов", "no_forwards_bypass", GgramConfig.isNoForwardsBypassEnabled));
        items.add(Item.check("Анти-удаление сообщений", "Сохранять удаленные сообщения с меткой [Удалено]", "antirecall_deleted", GgramConfig.isAntiRecallDeleted));
        items.add(Item.check("История редактирования", "Сохранять все промежуточные ревизии текста с меткой [Изм.]", "antirecall_edits", GgramConfig.isAntiRecallEdits));
        items.add(Item.info("Полный контроль над вашей перепиской и обход любых запретов копирования."));

        // 5. Chats & Ergonomics
        items.add(Item.header("ЧАТЫ И ЭРГОНОМИКА"));
        items.add(Item.check("Скрыть нижнюю плавающую панель", "Убрать плавающую панель и вернуть классическое боковое меню", "hide_bottom_bar", GgramConfig.isHideBottomBar));
        items.add(Item.check("Безлимитные закрепы чатов", "Снять ограничение на количество закрепленных диалогов", "unlimited_pins", GgramConfig.isUnlimitedPins));
        items.add(Item.check("Подтверждение удаления диалога", "Защита от случайного удаления переписки одним свайпом", "confirm_delete", GgramConfig.isConfirmDialogDelete));
        items.add(Item.check("Скрыть блок историй (Stories)", "Убрать кружочки историй с главного экрана диалогов", "hide_stories", GgramConfig.isHideStories));
        items.add(Item.check("Блокировка рекламы и спонсоров", "Отключение рекламных сообщений в каналах и поиске", "adblock_enabled", GgramConfig.isAdBlockEnabled));
        items.add(Item.check("Быстрый просмотр ID и метаданных", "Отображение User ID, Channel ID, Message ID и DC", "show_metadata", GgramConfig.isShowMetadataDetails));
        items.add(Item.info("Очистка экрана от лишних элементов и защита от случайных действий."));

        // 6. Proxy
        items.add(Item.header("СЕТЬ И ПРОКСИ"));
        items.add(Item.action("Настройки прокси Ggram", "MTProto, Shadowsocks, Socks5, V2Ray с авто-пином"));
        items.add(Item.info("Ggram Pure v1.3.0 • Emerald Obsidian Edition"));
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Настройки Ggram");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);

        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) return;
            Item item = items.get(position);
            if (item.type == 1) { // toggle check
                if ("ghost_master".equals(item.key)) {
                    boolean newVal = !item.checked;
                    GgramConfig.setGhostModeMaster(newVal);
                } else if ("hide_bottom_bar".equals(item.key)) {
                    boolean newVal = !item.checked;
                    GgramConfig.setHideBottomBar(newVal);
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, org.telegram.messenger.MessagesController.UPDATE_MASK_ALL);
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
                } else {
                    boolean newVal = !item.checked;
                    GgramConfig.toggle(item.key, newVal);
                }
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            } else if (item.type == 3) { // action
                presentFragment(new ProxyListActivity());
            }
        });

        return fragmentView;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 1 || type == 3;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                default:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            switch (holder.getItemViewType()) {
                case 0:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    headerCell.setText(item.title);
                    break;
                case 1:
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    checkCell.setTextAndValueAndCheck(item.title, item.subtitle, item.checked, true, true);
                    break;
                case 2:
                    TextInfoPrivacyCell infoCell = (TextInfoPrivacyCell) holder.itemView;
                    infoCell.setText(item.title);
                    break;
                case 3:
                    TextCell textCell = (TextCell) holder.itemView;
                    textCell.setTextAndValue(item.title, item.subtitle, false);
                    break;
            }
        }
    }
}
