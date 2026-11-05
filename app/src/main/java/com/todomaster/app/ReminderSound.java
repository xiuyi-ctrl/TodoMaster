package com.todomaster.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;

final class ReminderSound {
    static final String SETTINGS = "xiuyi_settings";
    static final String URI = "reminder_sound_uri";
    static final String NAME = "reminder_sound_name";

    static String channel(Context context, NotificationManager manager) {
        String selected = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getString(URI, "");
        String id = selected.isEmpty() ? "task_reminders_default_v2" : "task_reminders_custom_" + Integer.toHexString(selected.hashCode());
        if (manager.getNotificationChannel(id) == null) {
            NotificationChannel channel = new NotificationChannel(id, "待办提醒", NotificationManager.IMPORTANCE_HIGH);
            Uri sound = selected.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) : Uri.parse(selected);
            channel.setSound(sound, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            manager.createNotificationChannel(channel);
        }
        return id;
    }
}
