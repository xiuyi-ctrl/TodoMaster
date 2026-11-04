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

    @Override public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra("task_id", -1);
        long scheduled = intent.getLongExtra("reminder_time", -1);
        TaskStore.Task task = new TaskStore(context).get(id);
        if (scheduled < 0 && task != null) scheduled = task.remindAt;
        if (task == null || task.done || task.deleted || !task.reminderTimes.contains(scheduled)
                || scheduled > System.currentTimeMillis() + 60000) return;
        ReminderScheduler.update(context, task);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "咻咿待办提醒", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(context, MainActivity.class).putExtra("task_id", id).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, (int) id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification notification = new android.app.Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_cloud_notification).setContentTitle("咻咿提醒你：待办即将到期")
                .setContentText(task.title).setContentIntent(pending).setAutoCancel(true).build();
        try { manager.notify((int) id, notification); } catch (SecurityException ignored) { }
    }
}
