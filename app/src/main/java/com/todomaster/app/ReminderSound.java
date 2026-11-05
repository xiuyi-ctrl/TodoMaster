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
    static final String MODE = "reminder_sound_mode";

    static String mode(Context context) {
        android.content.SharedPreferences preferences = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE);
        String saved = preferences.getString(MODE, null);
        return saved != null ? saved : preferences.getString(URI, "").isEmpty() ? "built_in" : "custom";
    }

    static Uri sound(Context context) {
        String mode = mode(context);
        if ("system".equals(mode)) return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if ("custom".equals(mode)) {
            String selected = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getString(URI, "");
            if (!selected.isEmpty()) return Uri.parse(selected);
        }
        return Uri.parse("android.resource://" + context.getPackageName() + "/raw/xiuyi_chime");
    }

    static String channel(Context context, NotificationManager manager) {
        String mode = mode(context);
        Uri sound = sound(context);
        String id = "custom".equals(mode) ? "task_reminders_custom_" + Integer.toHexString(sound.toString().hashCode())
                : "system".equals(mode) ? "task_reminders_system_v3" : "task_reminders_xiuyi_v1";
        if (manager.getNotificationChannel(id) == null) {
            NotificationChannel channel = new NotificationChannel(id, "待办提醒", NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(sound, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            manager.createNotificationChannel(channel);
        }
        return id;
    }
}
