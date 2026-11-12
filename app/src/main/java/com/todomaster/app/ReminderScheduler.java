package com.todomaster.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Build;
import android.content.Context;
import android.content.Intent;

final class ReminderScheduler {
    static final long TEST_ID = 987655;

    static void scheduleTest(Context context) {
        long when = System.currentTimeMillis() + 60000;
        Intent intent = new Intent(context, ReminderReceiver.class).setAction("com.todomaster.app.TEST");
        PendingIntent pending = PendingIntent.getBroadcast(context, (int) TEST_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        schedule(context, alarms, when, pending);
        context.getSharedPreferences("xiuyi_settings", Context.MODE_PRIVATE).edit()
                .putLong("test_reminder_at", when)
                .putLong("test_last_scheduled_at", when)
                .putLong("test_last_triggered_at", 0)
                .putLong("test_last_posted_at", 0).apply();
    }

    static boolean canScheduleExactly(Context context) {
        return Build.VERSION.SDK_INT < 31 || ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
    }

    static boolean preciseEnabled(Context context) {
        return context.getSharedPreferences("xiuyi_settings", Context.MODE_PRIVATE).getBoolean("precise_reminders", true);
    }

    private static void schedule(Context context, AlarmManager alarms, long when, PendingIntent pending) {
        try {
            if (preciseEnabled(context) && canScheduleExactly(context)) {
                Intent show = new Intent(context, MainActivity.class);
                PendingIntent showPending = PendingIntent.getActivity(context, 987656, show,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(when, showPending), pending);
            } else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
        } catch (SecurityException denied) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
        }
    }

    static void update(Context context, TaskStore.Task task) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ReminderReceiver.class).putExtra("task_id", task.id);
        PendingIntent pending = PendingIntent.getBroadcast(context, (int) task.id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(pending);
        if (!task.deleted && !task.paused && (!task.done || task.timed && task.repeatDays > 0)) {
            long next = task.timed && task.repeatDays > 0 ? task.dueAt : Long.MAX_VALUE;
            long now = System.currentTimeMillis();
            if (!task.done) {
                for (long time : task.reminderTimes) if (time > now && time < next) next = time;
                if (task.snoozeAt > now && task.snoozeAt < next) next = task.snoozeAt;
            }
            if (next != Long.MAX_VALUE && next > now) {
                intent.putExtra("reminder_time", next);
                pending = PendingIntent.getBroadcast(context, (int) task.id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                schedule(context, alarms, next, pending);
            }
        }
    }
}
