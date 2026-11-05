package com.todomaster.app;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class ReminderReceiver extends BroadcastReceiver {
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
            if (COMPLETE.equals(action)) {
                manager.cancel((int) id);
                TaskStore.Task next = store.completeAndAdvance(task);
                if (next != null) ReminderScheduler.update(context, next);
            } else {
                long later = System.currentTimeMillis() + 10 * 60000L;
                if (task.dueAt <= later) {
                    showNotification(context, manager, task, "无法稍后10分钟", remaining(task.dueAt), false);
                    return;
                }
                manager.cancel((int) id);
                task.snoozeAt = later;
                store.save(task);
            }
            ReminderScheduler.update(context, task);
            return;
        }
        long scheduled = intent.getLongExtra("reminder_time", -1);
        if (scheduled < 0 && task != null) scheduled = task.remindAt;
        if (task == null || task.done || task.deleted ||
                (!task.reminderTimes.contains(scheduled) && task.snoozeAt != scheduled)
                || scheduled > System.currentTimeMillis() + 60000) return;
        if (task.snoozeAt == scheduled) {
            task.snoozeAt = 0;
            store.save(task);
        }
        ReminderScheduler.update(context, task);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        showNotification(context, manager, task, "咻咿提醒你：待办即将到期",
                task.title + " · " + remaining(task.dueAt), task.dueAt - System.currentTimeMillis() > 10 * 60000L);
    }

    private String remaining(long dueAt) {
        long seconds = Math.max(0, (dueAt - System.currentTimeMillis() + 999) / 1000);
        if (seconds == 0) return "已到截止时间";
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        if (hours > 0) return "距截止还有" + hours + "小时" + minutes + "分钟";
        return "距截止还有" + minutes + "分" + seconds % 60 + "秒";
    }

    private void showNotification(Context context, NotificationManager manager, TaskStore.Task task,
                                  String title, String message, boolean canSnooze) {
        String channel = ReminderSound.channel(context, manager);
        Intent open = new Intent(context, MainActivity.class).putExtra("task_id", task.id).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, (int) task.id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent complete = new Intent(context, ReminderReceiver.class).setAction(COMPLETE).putExtra("task_id", task.id);
        PendingIntent completePending = PendingIntent.getBroadcast(context, (int) task.id, complete,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder builder = new android.app.Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_cloud_notification).setContentTitle(title)
                .setContentText(message).setContentIntent(pending).setAutoCancel(true)
                .addAction(R.drawable.ic_cloud_notification, "完成", completePending);
        if (canSnooze) {
            Intent snooze = new Intent(context, ReminderReceiver.class).setAction(SNOOZE).putExtra("task_id", task.id);
            PendingIntent snoozePending = PendingIntent.getBroadcast(context, (int) task.id, snooze,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(R.drawable.ic_cloud_notification, "稍后10分钟", snoozePending);
        }
        try { manager.notify((int) task.id, builder.build()); } catch (SecurityException ignored) { }
    }
}
