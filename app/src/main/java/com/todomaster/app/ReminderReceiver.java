package com.todomaster.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "task_reminders";
    private static final String COMPLETE = "com.todomaster.app.COMPLETE";
    private static final String SNOOZE = "com.todomaster.app.SNOOZE";

    @Override public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra("task_id", -1);
        TaskStore store = new TaskStore(context);
        TaskStore.Task task = store.get(id);
        String action = intent.getAction();
        if (COMPLETE.equals(action) || SNOOZE.equals(action)) {
            if (task == null || task.done || task.deleted) return;
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel((int) id);
            if (COMPLETE.equals(action)) {
                TaskStore.Task next = store.completeAndAdvance(task);
                if (next != null) ReminderScheduler.update(context, next);
            } else {
                long later = System.currentTimeMillis() + 10 * 60000L;
                task.reminderTimes.add(later);
                task.remindAt = Math.min(task.remindAt > 0 ? task.remindAt : later, later);
                store.save(task);
            }
            ReminderScheduler.update(context, task);
            return;
        }
        long scheduled = intent.getLongExtra("reminder_time", -1);
        if (scheduled < 0 && task != null) scheduled = task.remindAt;
        if (task == null || task.done || task.deleted || !task.reminderTimes.contains(scheduled)
                || scheduled > System.currentTimeMillis() + 60000) return;
        ReminderScheduler.update(context, task);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "咻咿待办提醒", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(context, MainActivity.class).putExtra("task_id", id).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, (int) id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent complete = new Intent(context, ReminderReceiver.class).setAction(COMPLETE).putExtra("task_id", id);
        Intent snooze = new Intent(context, ReminderReceiver.class).setAction(SNOOZE).putExtra("task_id", id);
        PendingIntent completePending = PendingIntent.getBroadcast(context, (int) id, complete,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent snoozePending = PendingIntent.getBroadcast(context, (int) id, snooze,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification notification = new android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_cloud_notification).setContentTitle("咻咿提醒你：待办即将到期")
                .setContentText(task.title).setContentIntent(pending).setAutoCancel(true)
                .addAction(R.drawable.ic_cloud_notification, "完成", completePending)
                .addAction(R.drawable.ic_cloud_notification, "稍后10分钟", snoozePending).build();
        try { manager.notify((int) id, notification); } catch (SecurityException ignored) { }
    }
}
