package com.todomaster.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.media.MediaPlayer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.UUID;

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

    static Uri importAudio(Context context, Uri source, String name) throws IOException {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        String extension = lower.endsWith(".wav") ? "wav" : lower.endsWith(".ogg") ? "ogg"
                : lower.endsWith(".m4a") ? "m4a" : lower.endsWith(".aac") ? "aac" : "mp3";
        File directory = new File(context.getFilesDir(), "reminder_sounds");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建音频目录");
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        File target = new File(directory, filename);
        try {
            try (InputStream input = context.getContentResolver().openInputStream(source);
                 FileOutputStream output = new FileOutputStream(target)) {
                if (input == null) throw new IOException("无法读取音频");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }
            Uri result = Uri.parse("content://com.todomaster.app.reminder-sound/sound/" + filename);
            MediaPlayer check = MediaPlayer.create(context, result);
            if (check == null) throw new IOException("音频无法解码");
            check.release();
            return result;
        } catch (IOException | RuntimeException error) {
            target.delete();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("音频无法播放", error);
        }
    }

    static void migrateOldCustom(Context context) {
        if (!"custom".equals(mode(context))) return;
        android.content.SharedPreferences preferences = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE);
        String old = preferences.getString(URI, "");
        if (old.isEmpty() || old.startsWith("content://com.todomaster.app.reminder-sound/")) return;
        try {
            Uri copied = importAudio(context, Uri.parse(old), preferences.getString(NAME, "reminder.mp3"));
            preferences.edit().putString(URI, copied.toString()).putString(MODE, "custom").apply();
        } catch (IOException ignored) { }
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
