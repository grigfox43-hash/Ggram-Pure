package org.ggram.ui.menu;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CallLogActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.GroupCreateActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;
import org.ggram.ui.settings.GgramSettingsActivity;

/**
 * GgramSideMenu - Classic Telegram slide-in drawer side menu for Ggram Pure.
 * Restores intuitive ergonomics when bottom floating bar is hidden.
 */
public class GgramSideMenu {

    private static Dialog currentDialog;

    public static void show(BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        if (currentDialog != null && currentDialog.isShowing()) {
            return;
        }

        final Context context = fragment.getParentActivity();
        final int currentAccount = fragment.getCurrentAccount();
        final TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();

        final Dialog dialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);
        currentDialog = dialog;

        final int drawerWidth = Math.min(AndroidUtilities.dp(300), (int) (AndroidUtilities.displaySize.x * 0.82f));

        final FrameLayout root = new FrameLayout(context);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Dark scrim
        final View scrim = new View(context);
        scrim.setBackgroundColor(0x99000000);
        scrim.setAlpha(0f);
        root.addView(scrim, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Drawer container
        final FrameLayout drawer = new FrameLayout(context);
        drawer.setBackgroundColor(0xFF18191D); // Ggram Dark Charcoal
        drawer.setTranslationX(-drawerWidth);
        drawer.setClickable(true);
        root.addView(drawer, LayoutHelper.createFrame(drawerWidth, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        final LinearLayout drawerContent = new LinearLayout(context);
        drawerContent.setOrientation(LinearLayout.VERTICAL);
        drawer.addView(drawerContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // 1. Profile Header
        final FrameLayout header = new FrameLayout(context);
        header.setBackgroundColor(0xFF22242A); // Elevated slate
        int topPadding = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(16);
        header.setPadding(AndroidUtilities.dp(16), topPadding, AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        final AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (currentUser != null) {
            avatarDrawable.setInfo(currentAccount, currentUser);
        }

        final BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(28));
        if (currentUser != null) {
            avatarView.setForUserOrChat(currentUser, avatarDrawable);
        } else {
            avatarView.setImageDrawable(avatarDrawable);
        }
        header.addView(avatarView, LayoutHelper.createFrame(56, 56, Gravity.LEFT | Gravity.TOP));

        final LinearLayout userInfoLayout = new LinearLayout(context);
        userInfoLayout.setOrientation(LinearLayout.VERTICAL);

        final TextView nameView = new TextView(context);
        nameView.setTextColor(0xFFFFFFFF);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"), Typeface.BOLD);
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        if (currentUser != null) {
            nameView.setText(UserObject.getUserName(currentUser));
        } else {
            nameView.setText("Ggram User");
        }
        userInfoLayout.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final TextView phoneView = new TextView(context);
        phoneView.setTextColor(0xFF10D067); // Emerald text
        phoneView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        phoneView.setSingleLine(true);
        phoneView.setEllipsize(TextUtils.TruncateAt.END);
        if (currentUser != null) {
            if (!TextUtils.isEmpty(currentUser.username)) {
                phoneView.setText("@" + currentUser.username);
            } else if (!TextUtils.isEmpty(currentUser.phone)) {
                phoneView.setText("+" + currentUser.phone);
            } else {
                phoneView.setText("Ggram Pure");
            }
        } else {
            phoneView.setText("Ggram Pure");
        }
        userInfoLayout.addView(phoneView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        header.addView(userInfoLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 0));

        header.setOnClickListener(v -> dismissMenu(dialog, scrim, drawer, drawerWidth, () -> {
            if (currentUser != null) {
                Bundle args = new Bundle();
                args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                args.putBoolean("my_profile", true);
                fragment.presentFragment(new ProfileActivity(args));
            }
        }));

        drawerContent.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // 2. Menu Items List
        final ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        final LinearLayout itemsList = new LinearLayout(context);
        itemsList.setOrientation(LinearLayout.VERTICAL);
        itemsList.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(40));

        // Item: My Profile
        addItem(context, itemsList, R.drawable.msg_contact, "Мой профиль", () -> {
            dismissMenu(dialog, scrim, drawer, drawerWidth, () -> {
                Bundle args = new Bundle();
                args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                args.putBoolean("my_profile", true);
                fragment.presentFragment(new ProfileActivity(args));
            });
        });

        // Item: New Group
        addItem(context, itemsList, R.drawable.msg_groups_create, LocaleController.getString("NewGroup", R.string.NewGroup), () -> {
            dismissMenu(dialog, scrim, drawer, drawerWidth, () -> {
                Bundle args = new Bundle();
                fragment.presentFragment(new GroupCreateActivity(args));
            });
        });

        // Item: Calls
        addItem(context, itemsList, R.drawable.msg_calls, LocaleController.getString("Calls", R.string.Calls), () -> {
            dismissMenu(dialog, scrim, drawer, drawerWidth, () -> {
                Bundle args = new Bundle();
                args.putBoolean("needFinishFragment", true);
                fragment.presentFragment(new CallLogActivity(args));
            });
        });

        // Item: Contacts
        addItem(context, itemsList, R.drawable.msg_contacts, LocaleController.getString("Contacts", R.string.Contacts), () -> {
            dismissMenu(dialog, scrim, drawer, drawerWidth, () -> {
                Bundle args = new Bundle();
                args.putBoolean("needPhonebook", true);
                args.putBoolean("needFinishFragment", true);
                fragment.presentFragment(new ContactsActivity(args));
            });
        });

        // Item: Saved Messages
        addItem(context, itemsList, R.drawable.msg_saved, LocaleController.getString("SavedMessages", R.string.SavedMessages), () -> {
            dismissMenu(dialog, scrim, drawer, drawerWidth, () -> {
                Bundle args = new Bundle();
                args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                fragment.presentFragment(new ChatActivity(args));
            });
        });

        // Item: General Settings
        addItem(context, itemsList, R.drawable.msg_settings, "Общие настройки", () -> {
            dismissMenu(dialog, scrim, drawer, drawerWidth, () -> {
                fragment.presentFragment(new SettingsActivity());
            });
        });

        // Separator
        View divider = new View(context);
        divider.setBackgroundColor(0xFF2C2F36);
        itemsList.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 16, 6, 16, 6));

        // Item: Ggram Settings
        addItem(context, itemsList, R.drawable.msg_secret, "Настройки Ggram Pure", () -> {
            dismissMenu(dialog, scrim, drawer, drawerWidth, () -> {
                fragment.presentFragment(new GgramSettingsActivity());
            });
        });

        scrollView.addView(itemsList, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        drawerContent.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        // Footer
        TextView footer = new TextView(context);
        footer.setText("Ggram Pure v1.3.0");
        footer.setTextColor(0xFF555555);
        footer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(14));
        drawerContent.addView(footer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Dismiss on scrim click
        scrim.setOnClickListener(v -> dismissMenu(dialog, scrim, drawer, drawerWidth, null));

        // Dismiss on back press
        dialog.setOnKeyListener((dialogInterface, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                dismissMenu(dialog, scrim, drawer, drawerWidth, null);
                return true;
            }
            return false;
        });

        dialog.setContentView(root);
        dialog.show();

        // Slide in animation
        ValueAnimator enterAnim = ValueAnimator.ofFloat(0f, 1f);
        enterAnim.setDuration(220);
        enterAnim.setInterpolator(new DecelerateInterpolator(1.5f));
        enterAnim.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            scrim.setAlpha(val);
            drawer.setTranslationX(-drawerWidth * (1f - val));
        });
        enterAnim.start();
    }

    private static void addItem(Context context, LinearLayout parent, int iconRes, String title, Runnable onClick) {
        final LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setBackground(Theme.createSelectorDrawable(0x22FFFFFF, 2));
        item.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        item.setOnClickListener(v -> {
            if (onClick != null) {
                onClick.run();
            }
        });

        final ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(0xFF01BA53, PorterDuff.Mode.SRC_IN); // Emerald accent
        item.addView(icon, LayoutHelper.createLinear(24, 24));

        final TextView textView = new TextView(context);
        textView.setText(title);
        textView.setTextColor(0xFFFFFFFF);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setSingleLine(true);
        item.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 0, 0, 0));

        parent.addView(item, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
    }

    private static void dismissMenu(Dialog dialog, View scrim, View drawer, int drawerWidth, Runnable onEnd) {
        if (dialog == null || !dialog.isShowing()) return;

        ValueAnimator exitAnim = ValueAnimator.ofFloat(1f, 0f);
        exitAnim.setDuration(180);
        exitAnim.setInterpolator(new AccelerateInterpolator(1.5f));
        exitAnim.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            scrim.setAlpha(val);
            drawer.setTranslationX(-drawerWidth * (1f - val));
        });
        exitAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                try {
                    dialog.dismiss();
                } catch (Exception ignored) {
                }
                currentDialog = null;
                if (onEnd != null) {
                    onEnd.run();
                }
            }
        });
        exitAnim.start();
    }
}
