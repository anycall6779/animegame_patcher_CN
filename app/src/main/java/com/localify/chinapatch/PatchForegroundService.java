package com.localify.chinapatch;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

public class PatchForegroundService extends Service {
    private static final String CHANNEL_ID = "patch_progress";
    private static final int NOTIFICATION_ID = 2001;
    private static final String ACTION_START = "com.localify.chinapatch.START";
    private static final String ACTION_STOP = "com.localify.chinapatch.STOP";
    private static final String ACTION_UPDATE = "com.localify.chinapatch.UPDATE";
    private static final String EXTRA_PROGRESS = "progress";
    private static final String EXTRA_TEXT = "text";

    static void start(Context context) {
        Intent intent = new Intent(context, PatchForegroundService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    static void update(Context context, int progress, String text) {
        Intent intent = new Intent(context, PatchForegroundService.class)
                .setAction(ACTION_UPDATE)
                .putExtra(EXTRA_PROGRESS, progress)
                .putExtra(EXTRA_TEXT, text);
        context.startService(intent);
    }

    static void stop(Context context) {
        context.startService(new Intent(context, PatchForegroundService.class).setAction(ACTION_STOP));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        int progress = intent == null ? 0 : intent.getIntExtra(EXTRA_PROGRESS, 0);
        String text = intent == null ? "작업 준비 중" : intent.getStringExtra(EXTRA_TEXT);
        if (text == null || text.isEmpty()) text = "작업 진행 중";
        startForeground(NOTIFICATION_ID, notification(progress, text).build());
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private NotificationCompat.Builder notification(int progress, String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("붕괴학원패치")
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, Math.max(0, Math.min(100, progress)), false);
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "패치 진행", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }
}
