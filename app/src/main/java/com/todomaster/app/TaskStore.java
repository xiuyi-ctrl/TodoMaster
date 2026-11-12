package com.todomaster.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;

final class TaskStore extends SQLiteOpenHelper {
    private final Context context;
    static final class Task {
        long id, dueAt, remindAt, snoozeAt, deletedAt, createdAt;
        int repeatDays;
        final List<Long> reminderTimes = new ArrayList<>();
        final List<Subtask> subtasks = new ArrayList<>();
        String title, note, category, priority;
        boolean done, deleted, timed, paused;
    }
    static final class Subtask {
        long id, createdAt;
        int sortIndex;
        String title;
        boolean done;
    }

    TaskStore(Context context) { super(context, "tasks.db", null, 8); this.context = context.getApplicationContext(); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, note TEXT NOT NULL, category TEXT NOT NULL, priority TEXT NOT NULL, due_at INTEGER NOT NULL, remind_at INTEGER NOT NULL, reminder_times TEXT NOT NULL DEFAULT '', snooze_at INTEGER NOT NULL DEFAULT 0, repeat_days INTEGER NOT NULL DEFAULT 0, timed INTEGER NOT NULL DEFAULT 0, paused INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0, deleted_at INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL DEFAULT 0)");
        createSubtasks(db);
    }

    private void createSubtasks(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE subtasks (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id INTEGER NOT NULL, title TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, sort_index INTEGER NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX subtasks_task_order ON subtasks(task_id, sort_index, id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE tasks ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0");
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE tasks SET created_at = id WHERE created_at = 0");
        }
        if (oldVersion < 4) db.execSQL("ALTER TABLE tasks ADD COLUMN reminder_times TEXT NOT NULL DEFAULT ''");
        if (oldVersion < 5) db.execSQL("ALTER TABLE tasks ADD COLUMN repeat_days INTEGER NOT NULL DEFAULT 0");
        if (oldVersion < 6) db.execSQL("ALTER TABLE tasks ADD COLUMN snooze_at INTEGER NOT NULL DEFAULT 0");
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN timed INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN paused INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE tasks SET timed=1 WHERE repeat_days>0 AND done=0");
        }
        if (oldVersion < 8) createSubtasks(db);
    }

    long save(Task task) {
        if (task.id == 0 && task.createdAt == 0) task.createdAt = System.currentTimeMillis();
        ContentValues values = values(task);
        if (task.id == 0) task.id = getWritableDatabase().insertOrThrow("tasks", null, values);
        else getWritableDatabase().update("tasks", values, "id=?", new String[]{String.valueOf(task.id)});
        WidgetProvider.requestRefresh(context);
        return task.id;
    }

    void saveWithSubtasks(Task task) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (task.id == 0 && task.createdAt == 0) task.createdAt = System.currentTimeMillis();
            if (task.id == 0) task.id = db.insertOrThrow("tasks", null, values(task));
            else db.update("tasks", values(task), "id=?", new String[]{String.valueOf(task.id)});
            db.delete("subtasks", "task_id=?", new String[]{String.valueOf(task.id)});
            insertSubtasks(db, task);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        WidgetProvider.requestRefresh(context);
    }

    private void insertSubtasks(SQLiteDatabase db, Task task) {
        for (int i = 0; i < task.subtasks.size(); i++) {
            Subtask subtask = task.subtasks.get(i);
            ContentValues values = new ContentValues();
            if (subtask.id > 0) values.put("id", subtask.id);
            values.put("task_id", task.id);
            values.put("title", subtask.title);
            values.put("done", subtask.done ? 1 : 0);
            values.put("sort_index", i);
            values.put("created_at", subtask.createdAt > 0 ? subtask.createdAt : System.currentTimeMillis());
            subtask.id = db.insertOrThrow("subtasks", null, values);
            subtask.sortIndex = i;
        }
    }

    void replaceAll(List<Task> tasks) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("subtasks", null, null);
            db.delete("tasks", null, null);
            for (Task task : tasks) {
                ContentValues values = values(task);
                values.put("id", task.id);
                db.insertOrThrow("tasks", null, values);
                insertSubtasks(db, task);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        WidgetProvider.requestRefresh(context);
    }

    private ContentValues values(Task task) {
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
        values.put("snooze_at", task.snoozeAt);
        values.put("repeat_days", task.repeatDays);
        values.put("timed", task.timed ? 1 : 0);
        values.put("paused", task.paused ? 1 : 0);
        values.put("done", task.done ? 1 : 0);
        values.put("deleted", task.deleted ? 1 : 0);
        values.put("deleted_at", task.deletedAt);
        values.put("created_at", task.createdAt);
        return values;
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

    List<Task> snapshot() {
        SQLiteDatabase db = getReadableDatabase();
        db.beginTransaction();
        try {
            List<Task> tasks = all(false);
            tasks.addAll(all(true));
            db.setTransactionSuccessful();
            return tasks;
        } finally { db.endTransaction(); }
    }

    void deletePermanently(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String[] args = {String.valueOf(id)};
            if (db.delete("tasks", "id=? AND deleted=1", args) > 0) db.delete("subtasks", "task_id=?", args);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        WidgetProvider.requestRefresh(context);
    }

    boolean complete(Task task) {
        ContentValues completed = new ContentValues();
        completed.put("done", 1);
        if (!task.timed) completed.put("repeat_days", 0);
        if (getWritableDatabase().update("tasks", completed, "id=? AND done=0 AND deleted=0",
                new String[]{String.valueOf(task.id)}) == 0) return false;
        task.done = true;
        if (!task.timed) task.repeatDays = 0;
        WidgetProvider.requestRefresh(context);
        return true;
    }

    void advanceTimed(Task task) {
        if (!task.timed || task.repeatDays < 1 || task.paused || task.deleted || task.dueAt > System.currentTimeMillis()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Task current = get(task.id);
            if (current == null || current.dueAt != task.dueAt || current.paused || current.deleted) {
                if (current != null) {
                    task.dueAt = current.dueAt;
                    task.done = current.done;
                    task.paused = current.paused;
                    task.deleted = current.deleted;
                    task.reminderTimes.clear();
                    task.reminderTimes.addAll(current.reminderTimes);
                    task.remindAt = current.remindAt;
                }
                db.setTransactionSuccessful();
                return;
            }
            task.done = current.done;
            task.title = current.title;
            task.note = current.note;
            task.category = current.category;
            task.priority = current.priority;
            task.reminderTimes.clear();
            task.reminderTimes.addAll(current.reminderTimes);
            if (task.done) {
                Task history = new Task();
                history.title = task.title;
                history.note = task.note;
                history.category = task.category;
                history.priority = task.priority;
                history.dueAt = task.dueAt;
                history.timed = true;
                history.done = true;
                save(history);
            }
            long previousDue = task.dueAt;
            Calendar date = Calendar.getInstance();
            date.setTimeInMillis(previousDue);
            do { date.add(Calendar.DAY_OF_YEAR, task.repeatDays); }
            while (date.getTimeInMillis() <= System.currentTimeMillis());
            task.dueAt = date.getTimeInMillis();
            List<Long> oldTimes = new ArrayList<>(task.reminderTimes);
            task.reminderTimes.clear();
            for (long time : oldTimes) {
                long advance = previousDue - time;
                if (advance > 0) task.reminderTimes.add(task.dueAt - advance);
            }
            task.remindAt = task.reminderTimes.isEmpty() ? 0 : java.util.Collections.min(task.reminderTimes);
            task.snoozeAt = 0;
            task.done = false;
            save(task);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
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
        t.snoozeAt = c.getLong(c.getColumnIndexOrThrow("snooze_at"));
        String times = c.getString(c.getColumnIndexOrThrow("reminder_times"));
        if (!times.isEmpty()) {
            for (String value : times.split(",")) t.reminderTimes.add(Long.parseLong(value));
        } else if (t.remindAt > 0) t.reminderTimes.add(t.remindAt);
        if (t.dueAt > 0) {
            t.reminderTimes.removeIf(time -> time >= t.dueAt);
            t.remindAt = t.reminderTimes.isEmpty() ? 0 : java.util.Collections.min(t.reminderTimes);
            if (t.snoozeAt >= t.dueAt) t.snoozeAt = 0;
        }
        t.done = c.getInt(c.getColumnIndexOrThrow("done")) != 0;
        t.deleted = c.getInt(c.getColumnIndexOrThrow("deleted")) != 0;
        t.deletedAt = c.getLong(c.getColumnIndexOrThrow("deleted_at"));
        t.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        t.repeatDays = c.getInt(c.getColumnIndexOrThrow("repeat_days"));
        t.timed = c.getInt(c.getColumnIndexOrThrow("timed")) != 0;
        t.paused = c.getInt(c.getColumnIndexOrThrow("paused")) != 0;
        try (Cursor children = getReadableDatabase().query("subtasks", null, "task_id=?",
                new String[]{String.valueOf(t.id)}, null, null, "sort_index, id")) {
            while (children.moveToNext()) {
                Subtask child = new Subtask();
                child.id = children.getLong(children.getColumnIndexOrThrow("id"));
                child.title = children.getString(children.getColumnIndexOrThrow("title"));
                child.done = children.getInt(children.getColumnIndexOrThrow("done")) != 0;
                child.sortIndex = children.getInt(children.getColumnIndexOrThrow("sort_index"));
                child.createdAt = children.getLong(children.getColumnIndexOrThrow("created_at"));
                t.subtasks.add(child);
            }
        }
        return t;
    }
}
