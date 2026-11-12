package com.todomaster.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class LocalBackup {
    private static final int MAX_JSON = 5 * 1024 * 1024;
    private static final int MAX_SOUND = 20 * 1024 * 1024;
    private static final String FORMAT = "xiuyi-backup";

    static final class Data {
        final List<TaskStore.Task> tasks = new ArrayList<>();
        long exportedAt;
        String dateMode, soundMode, soundName, soundExtension;
        boolean precise;
        byte[] sound;

        int count(boolean timed, boolean deleted) {
            int count = 0;
            for (TaskStore.Task task : tasks) if (task.timed == timed && task.deleted == deleted) count++;
            return count;
        }

        int deletedCount() {
            int count = 0;
            for (TaskStore.Task task : tasks) if (task.deleted) count++;
            return count;
        }
    }

    static File create(Context context, TaskStore store) throws IOException {
        Data data = new Data();
        data.tasks.addAll(store.snapshot());
        data.exportedAt = System.currentTimeMillis();
        SharedPreferences settings = context.getSharedPreferences("xiuyi_settings", Context.MODE_PRIVATE);
        data.dateMode = settings.getString("date_picker_mode", "wheel");
        data.precise = settings.getBoolean("precise_reminders", true);
        data.soundMode = ReminderSound.mode(context);
        data.soundName = settings.getString(ReminderSound.NAME, "");
        if ("custom".equals(data.soundMode)) {
            Uri uri = ReminderSound.sound(context);
            String filename = uri.getLastPathSegment();
            if (filename == null || !filename.matches("[a-f0-9]{32}\\.(mp3|wav|ogg|m4a|aac)"))
                throw new IOException("自定义提示音无法读取，请重新选择提示音");
            data.soundExtension = filename.substring(filename.lastIndexOf('.') + 1);
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                data.sound = readLimited(input, MAX_SOUND);
            }
        }
        JSONObject root;
        try { root = toJson(data); }
        catch (JSONException error) { throw new IOException("无法生成备份内容", error); }
        byte[] json = root.toString().getBytes(StandardCharsets.UTF_8);
        if (json.length > MAX_JSON) throw new IOException("待办数据过多，无法生成备份");
        File file = File.createTempFile("xiuyi-backup-", ".xybak", context.getCacheDir());
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("backup.json"));
            zip.write(json);
            zip.closeEntry();
            if (data.sound != null) {
                zip.putNextEntry(new ZipEntry("sound/audio." + data.soundExtension));
                zip.write(data.sound);
                zip.closeEntry();
            }
            return file;
        } catch (IOException | RuntimeException error) {
            file.delete();
            throw error;
        }
    }

    static Data read(Context context, Uri uri) throws IOException {
        byte[] json = null, sound = null;
        String soundPath = null;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("无法读取备份文件");
            try (ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (entry.isDirectory() || (!"backup.json".equals(name) && !name.matches("sound/audio\\.(mp3|wav|ogg|m4a|aac)")))
                        throw new IOException("备份包含不支持的文件");
                    if ("backup.json".equals(name)) {
                        if (json != null) throw new IOException("备份内容重复");
                        json = readLimited(zip, MAX_JSON);
                    } else {
                        if (sound != null) throw new IOException("备份音频重复");
                        soundPath = name;
                        sound = readLimited(zip, MAX_SOUND);
                    }
                    zip.closeEntry();
                }
            }
        }
        if (json == null) throw new IOException("未找到备份数据");
        try {
            Data data = fromJson(new JSONObject(new String(json, StandardCharsets.UTF_8)));
            if ("custom".equals(data.soundMode)) {
                if (sound == null || !soundPath.equals("sound/audio." + data.soundExtension))
                    throw new IOException("备份缺少自定义提示音");
                data.sound = sound;
                verifyAudio(context, sound, data.soundExtension);
            } else if (sound != null) throw new IOException("备份音频与设置不一致");
            return data;
        } catch (JSONException error) {
            throw new IOException("备份内容格式错误", error);
        }
    }

    static boolean restore(Context context, TaskStore store, Data data) throws IOException {
        List<TaskStore.Task> oldTasks = store.snapshot();
        SharedPreferences settings = context.getSharedPreferences("xiuyi_settings", Context.MODE_PRIVATE);
        String oldDate = settings.getString("date_picker_mode", "wheel");
        boolean oldPrecise = settings.getBoolean("precise_reminders", true);
        String oldMode = settings.getString(ReminderSound.MODE, "built_in");
        String oldUri = settings.getString(ReminderSound.URI, "");
        String oldName = settings.getString(ReminderSound.NAME, "");
        Uri newUri = null;
        if (data.sound != null) {
            File staged = writeTemp(context, data.sound, data.soundExtension);
            try { newUri = ReminderSound.importAudio(context, Uri.fromFile(staged), "audio." + data.soundExtension); }
            finally { staged.delete(); }
        }
        String selectedUri = newUri == null ? "" : newUri.toString();
        try {
            if (!settings.edit().putString("date_picker_mode", data.dateMode)
                    .putBoolean("precise_reminders", data.precise)
                    .putString(ReminderSound.MODE, data.soundMode)
                    .putString(ReminderSound.URI, selectedUri)
                    .putString(ReminderSound.NAME, data.soundName).commit())
                throw new IOException("无法恢复应用设置");
            store.replaceAll(data.tasks);
        } catch (RuntimeException | IOException error) {
            settings.edit().putString("date_picker_mode", oldDate).putBoolean("precise_reminders", oldPrecise)
                    .putString(ReminderSound.MODE, oldMode).putString(ReminderSound.URI, oldUri)
                    .putString(ReminderSound.NAME, oldName).commit();
            if (newUri != null) new File(new File(context.getFilesDir(), "reminder_sounds"),
                    newUri.getLastPathSegment()).delete();
            throw new IOException("恢复失败，当前数据未更改", error);
        }
        boolean remindersScheduled = true;
        for (TaskStore.Task old : oldTasks) {
            try {
                ReminderScheduler.cancel(context, old.id);
                ((android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE))
                        .cancel((int) old.id);
            }
            catch (RuntimeException error) { remindersScheduled = false; }
        }
        for (TaskStore.Task task : store.all(false)) {
            try {
                store.advanceTimed(task);
                ReminderScheduler.update(context, task);
            } catch (RuntimeException error) { remindersScheduled = false; }
        }
        return remindersScheduled;
    }

    private static JSONObject toJson(Data data) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("schemaVersion", 1);
        root.put("exportedAt", data.exportedAt);
        JSONObject settings = new JSONObject();
        settings.put("dateMode", data.dateMode);
        settings.put("precise", data.precise);
        settings.put("soundMode", data.soundMode);
        settings.put("soundName", data.soundName);
        root.put("settings", settings);
        if (data.sound != null) root.put("customSound", new JSONObject().put("extension", data.soundExtension));
        JSONArray tasks = new JSONArray();
        for (TaskStore.Task task : data.tasks) {
            JSONObject item = new JSONObject();
            item.put("id", task.id);
            item.put("title", task.title);
            item.put("note", task.note);
            item.put("category", task.category);
            item.put("priority", task.priority);
            item.put("dueAt", task.dueAt);
            JSONArray times = new JSONArray();
            for (long time : task.reminderTimes) times.put(time);
            item.put("reminderTimes", times);
            item.put("repeatDays", task.repeatDays);
            item.put("timed", task.timed);
            item.put("paused", task.paused);
            item.put("done", task.done);
            item.put("deleted", task.deleted);
            item.put("deletedAt", task.deletedAt);
            item.put("createdAt", task.createdAt);
            tasks.put(item);
        }
        root.put("tasks", tasks);
        return root;
    }

    private static Data fromJson(JSONObject root) throws IOException, JSONException {
        if (!FORMAT.equals(root.optString("format"))) throw new IOException("这不是咻咿待办备份");
        if (root.optInt("schemaVersion", -1) != 1) throw new IOException("备份版本不受支持");
        Data data = new Data();
        data.exportedAt = root.getLong("exportedAt");
        JSONObject settings = root.getJSONObject("settings");
        data.dateMode = settings.getString("dateMode");
        data.precise = settings.getBoolean("precise");
        data.soundMode = settings.getString("soundMode");
        data.soundName = settings.getString("soundName");
        if (!"wheel".equals(data.dateMode) && !"system".equals(data.dateMode)) throw new IOException("日期设置无效");
        if (!"built_in".equals(data.soundMode) && !"system".equals(data.soundMode)
                && !"custom".equals(data.soundMode)) throw new IOException("声音设置无效");
        if ("custom".equals(data.soundMode)) {
            data.soundExtension = root.getJSONObject("customSound").getString("extension");
            if (!data.soundExtension.matches("mp3|wav|ogg|m4a|aac")) throw new IOException("音频格式不受支持");
        }
        Set<Long> ids = new HashSet<>();
        JSONArray tasks = root.getJSONArray("tasks");
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject item = tasks.getJSONObject(i);
            TaskStore.Task task = new TaskStore.Task();
            task.id = item.getLong("id");
            task.title = item.getString("title");
            task.note = item.getString("note");
            task.category = item.getString("category");
            task.priority = item.getString("priority");
            task.dueAt = item.getLong("dueAt");
            JSONArray times = item.getJSONArray("reminderTimes");
            for (int j = 0; j < times.length(); j++) task.reminderTimes.add(times.getLong(j));
            task.repeatDays = item.getInt("repeatDays");
            task.timed = item.getBoolean("timed");
            task.paused = item.getBoolean("paused");
            task.done = item.getBoolean("done");
            task.deleted = item.getBoolean("deleted");
            task.deletedAt = item.getLong("deletedAt");
            task.createdAt = item.getLong("createdAt");
            if (task.id < 1 || !ids.add(task.id) || task.title.trim().isEmpty() || task.dueAt < 0
                    || task.repeatDays < 0 || task.deletedAt < 0 || task.createdAt < 0
                    || !isCategory(task.category) || !isPriority(task.priority)) throw new IOException("备份中的待办数据无效");
            for (long time : task.reminderTimes) {
                if (time <= 0 || task.dueAt <= 0 || time >= task.dueAt) throw new IOException("备份中的提醒时间无效");
            }
            task.remindAt = task.reminderTimes.isEmpty() ? 0 : java.util.Collections.min(task.reminderTimes);
            data.tasks.add(task);
        }
        return data;
    }

    private static boolean isCategory(String value) {
        return "学习".equals(value) || "游戏".equals(value) || "工作".equals(value)
                || "日常".equals(value) || "其他".equals(value) || "生活".equals(value);
    }

    private static boolean isPriority(String value) {
        return "高".equals(value) || "中".equals(value) || "低".equals(value);
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        if (input == null) throw new IOException("无法读取备份内容");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > limit) throw new IOException("备份文件过大");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static File writeTemp(Context context, byte[] bytes, String extension) throws IOException {
        File file = File.createTempFile("xiuyi-sound-", "." + extension, context.getCacheDir());
        try (OutputStream output = new FileOutputStream(file)) { output.write(bytes); }
        return file;
    }

    private static void verifyAudio(Context context, byte[] sound, String extension) throws IOException {
        File file = writeTemp(context, sound, extension);
        try {
            MediaPlayer player = MediaPlayer.create(context, Uri.fromFile(file));
            if (player == null) throw new IOException("备份中的音频无法播放");
            player.release();
        } finally { file.delete(); }
    }
}
