package com.todomaster.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

final class TaskStore extends SQLiteOpenHelper {
    static final class Task {
        long id, dueAt, remindAt, deletedAt, createdAt;
        final List<Long> reminderTimes = new ArrayList<>();
        String title, note, category, priority;
        boolean done, deleted;
    }

    TaskStore(Context context) { super(context, "tasks.db", null, 4); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, note TEXT NOT NULL, category TEXT NOT NULL, priority TEXT NOT NULL, due_at INTEGER NOT NULL, remind_at INTEGER NOT NULL, reminder_times TEXT NOT NULL DEFAULT '', done INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0, deleted_at INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL DEFAULT 0)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE tasks ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0");
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE tasks SET created_at = id WHERE created_at = 0");
        }
        if (oldVersion < 4) db.execSQL("ALTER TABLE tasks ADD COLUMN reminder_times TEXT NOT NULL DEFAULT ''");
    }

    long save(Task task) {
        if (task.id == 0 && task.createdAt == 0) task.createdAt = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("title", task.title);
        values.put("note", task.note);
        values.put("category", task.category);
        values.put("priority", task.priority);
        values.put("due_at", task.dueAt);
        values.put("remind_at", task.remindAt);
        StringBuilder times = new StringBuilder();
        for (long time : task.reminderTimes) {
            if (times.length() > 0) times.append(',');
            times.append(time);
        }
        values.put("reminder_times", times.toString());
        values.put("done", task.done ? 1 : 0);
        values.put("deleted", task.deleted ? 1 : 0);
        values.put("deleted_at", task.deletedAt);
        values.put("created_at", task.createdAt);
        if (task.id == 0) task.id = getWritableDatabase().insertOrThrow("tasks", null, values);
        else getWritableDatabase().update("tasks", values, "id=?", new String[]{String.valueOf(task.id)});
        return task.id;
    }

    Task get(long id) {
        try (Cursor cursor = getReadableDatabase().query("tasks", null, "id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    List<Task> all(boolean deleted) {
        List<Task> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("tasks", null, "deleted=?", new String[]{deleted ? "1" : "0"}, null, null, deleted ? "deleted_at DESC, id DESC" : "created_at DESC, id DESC")) {
            while (cursor.moveToNext()) result.add(read(cursor));
        }
        return result;
    }

    void deletePermanently(long id) {
        getWritableDatabase().delete("tasks", "id=? AND deleted=1", new String[]{String.valueOf(id)});
    }

    private Task read(Cursor c) {
        Task t = new Task();
        t.id = c.getLong(c.getColumnIndexOrThrow("id"));
        t.title = c.getString(c.getColumnIndexOrThrow("title"));
        t.note = c.getString(c.getColumnIndexOrThrow("note"));
        t.category = c.getString(c.getColumnIndexOrThrow("category"));
        t.priority = c.getString(c.getColumnIndexOrThrow("priority"));
        t.dueAt = c.getLong(c.getColumnIndexOrThrow("due_at"));
        t.remindAt = c.getLong(c.getColumnIndexOrThrow("remind_at"));
        String times = c.getString(c.getColumnIndexOrThrow("reminder_times"));
        if (!times.isEmpty()) {
            for (String value : times.split(",")) t.reminderTimes.add(Long.parseLong(value));
        } else if (t.remindAt > 0) t.reminderTimes.add(t.remindAt);
        t.done = c.getInt(c.getColumnIndexOrThrow("done")) != 0;
        t.deleted = c.getInt(c.getColumnIndexOrThrow("deleted")) != 0;
        t.deletedAt = c.getLong(c.getColumnIndexOrThrow("deleted_at"));
        t.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        return t;
    }
}
