package com.todomaster.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Build;
import android.content.Context;
import android.content.Intent;

final class ReminderScheduler {
    static boolean canScheduleExactly(Context context) {
        return Build.VERSION.SDK_INT < 31 || ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
    }

    static boolean preciseEnabled(Context context) {
        return context.getSharedPreferences("xiuyi_settings", Context.MODE_PRIVATE).getBoolean("precise_reminders", true);
    }

    static void update(Context context, TaskStore.Task task) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class).putExtra("task_id", task.id);
        PendingIntent pending = PendingIntent.getBroadcast(context, (int) task.id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(pending);
        if (!task.done && !task.deleted) {
            long next = Long.MAX_VALUE;
            long now = System.currentTimeMillis();
            for (long time : task.reminderTimes) if (time > now && time < next) next = time;
            if (task.snoozeAt > now && task.snoozeAt < next) next = task.snoozeAt;
            if (next != Long.MAX_VALUE) {
                intent.putExtra("reminder_time", next);
                pending = PendingIntent.getBroadcast(context, (int) task.id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                try {
                    if (preciseEnabled(context) && canScheduleExactly(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending);
                    else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending);
                } catch (SecurityException denied) {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending);
                }
            }
        }
    }
}
