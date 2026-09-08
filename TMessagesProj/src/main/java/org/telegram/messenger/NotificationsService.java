/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.LaunchActivity;

public class NotificationsService extends Service {

    public static final String ACTION_PUSH_PING = "org.ggram.PUSH_PING";
    private static final long PING_INTERVAL_MS = 120 * 1000L; // 2 minutes keep-alive: ultra-low power consumption

    private static final int NOTIFICATION_ID = 9999;
    private static final String CHANNEL_ID = "ggram_push_channel";

    private AlarmManager alarmManager;
    private PendingIntent pingPendingIntent;
    private PowerManager.WakeLock pingWakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundInternal();
        ApplicationLoader.postInitApplication();
        initPingAlarm();
        ensurePushConnectionsActive();
        scheduleNextPing();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundInternal();
        if (intent != null && ACTION_PUSH_PING.equals(intent.getAction())) {
            if (!ApplicationLoader.isScreenOn) {
                acquirePingWakeLock(2000);
                try {
                    ensurePushConnectionsActive();
                } finally {
                    AndroidUtilities.runOnUIThread(this::releasePingWakeLock, 1000);
                    scheduleNextPing();
                }
            } else {
                scheduleNextPing();
            }
            return START_NOT_STICKY;
        }
        ensurePushConnectionsActive();
        scheduleNextPing();
        return START_NOT_STICKY;
    }

    private void initPingAlarm() {
        try {
            alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent pingIntent = new Intent(this, NotificationsService.class);
            pingIntent.setAction(ACTION_PUSH_PING);
            int pflags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) {
                pflags |= PendingIntent.FLAG_IMMUTABLE;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                pingPendingIntent = PendingIntent.getForegroundService(this, 1001, pingIntent, pflags);
            } else {
                pingPendingIntent = PendingIntent.getService(this, 1001, pingIntent, pflags);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void scheduleNextPing() {
        if (alarmManager == null || pingPendingIntent == null) {
            return;
        }
        long triggerAt = SystemClock.elapsedRealtime() + PING_INTERVAL_MS;
        try {
            boolean canExact = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                canExact = alarmManager.canScheduleExactAlarms();
            }
            if (canExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pingPendingIntent);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pingPendingIntent);
                } else {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pingPendingIntent);
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pingPendingIntent);
                } else {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pingPendingIntent);
                }
            }
        } catch (SecurityException se) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pingPendingIntent);
                } else {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pingPendingIntent);
                }
            } catch (Throwable ignore) {
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void cancelPingAlarm() {
        try {
            if (alarmManager != null && pingPendingIntent != null) {
                alarmManager.cancel(pingPendingIntent);
            }
        } catch (Throwable ignore) {
        }
    }

    private void acquirePingWakeLock(long timeoutMs) {
        try {
            if (pingWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    pingWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ggram:push_ping_wakelock");
                    pingWakeLock.setReferenceCounted(false);
                }
            }
            if (pingWakeLock != null) {
                pingWakeLock.acquire(timeoutMs);
            }
        } catch (Throwable ignore) {
        }
    }

    private void releasePingWakeLock() {
        try {
            if (pingWakeLock != null && pingWakeLock.isHeld()) {
                pingWakeLock.release();
            }
        } catch (Throwable ignore) {
        }
    }

    private void ensurePushConnectionsActive() {
        try {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    ConnectionsManager.getInstance(a).setPushConnectionEnabled(true);
                    ConnectionsManager.getInstance(a).resumeNetworkMaybe();
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private void startForegroundInternal() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    NotificationChannel channel = nm.getNotificationChannel(CHANNEL_ID);
                    if (channel == null) {
                        channel = new NotificationChannel(
                                CHANNEL_ID,
                                "Служба уведомлений Ggram",
                                NotificationManager.IMPORTANCE_LOW
                        );
                        channel.setDescription("Фоновая доставка сообщений и звонков");
                        channel.setSound(null, null);
                        channel.enableVibration(false);
                        channel.enableLights(false);
                        channel.setShowBadge(false);
                        nm.createNotificationChannel(channel);
                    }
                }
            }

            Intent launchIntent = new Intent(this, LaunchActivity.class);
            launchIntent.setAction(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle("Ggram")
                    .setContentText("Служба фоновых уведомлений активна")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setSilent(true)
                    .setShowWhen(false)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setContentIntent(pendingIntent);

            Notification notification = builder.build();

            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        cancelPingAlarm();
        releasePingWakeLock();
        try {
            stopForeground(true);
        } catch (Throwable ignore) {
        }
        stopSelf();
        try {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                ConnectionsManager.getInstance(a).setAppPaused(true, false);
            }
        } catch (Throwable ignore) {
        }
        // App removed from Recents: terminate process cleanly so it never hangs in background
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelPingAlarm();
        releasePingWakeLock();
        try {
            stopForeground(true);
        } catch (Throwable ignore) {
        }
    }
}
