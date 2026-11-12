package com.todomaster.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WidgetProvider extends AppWidgetProvider {
    private static final String REFRESH = "com.todomaster.app.WIDGET_REFRESH";
    private static final String COMPLETE = "com.todomaster.app.WIDGET_COMPLETE";
    public static final class Todo extends WidgetProvider { }
    public static final class Timed extends WidgetProvider { }
    public static final class TodoWide extends WidgetProvider { }
    public static final class TimedWide extends WidgetProvider { }

    static void requestRefresh(Context context) {
        context.sendBroadcast(new Intent(context, Todo.class).setAction(REFRESH));
        context.sendBroadcast(new Intent(context, Timed.class).setAction(REFRESH));
        context.sendBroadcast(new Intent(context, TodoWide.class).setAction(REFRESH));
        context.sendBroadcast(new Intent(context, TimedWide.class).setAction(REFRESH));
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (COMPLETE.equals(intent.getAction())) {
            long id = intent.getLongExtra("task_id", -1);
            TaskStore store = new TaskStore(context);
            TaskStore.Task task = store.get(id);
            if (task != null && !task.done && !task.deleted && !task.paused
                    && task.dueAt == intent.getLongExtra("due_at", -1)) {
                store.complete(task);
                ReminderScheduler.update(context, task);
            }
        }
        super.onReceive(context, intent);
        if (COMPLETE.equals(intent.getAction()) || REFRESH.equals(intent.getAction())) refresh(context);
    }

    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        render(context, manager, manager.getAppWidgetIds(new ComponentName(context, Todo.class)), false);
        render(context, manager, manager.getAppWidgetIds(new ComponentName(context, Timed.class)), true);
        render(context, manager, manager.getAppWidgetIds(new ComponentName(context, TodoWide.class)), false);
        render(context, manager, manager.getAppWidgetIds(new ComponentName(context, TimedWide.class)), true);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        render(context, manager, ids, this instanceof Timed || this instanceof TimedWide);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        render(context, manager, new int[]{id}, this instanceof Timed || this instanceof TimedWide);
    }

    private static void render(Context context, AppWidgetManager manager, int[] ids, boolean timed) {
        if (ids.length == 0) return;
        List<TaskStore.Task> tasks = new ArrayList<>();
        for (TaskStore.Task task : new TaskStore(context).all(false))
            if (task.timed == timed && !task.done && !task.paused) tasks.add(task);
        int[] rows = {R.id.row1, R.id.row2, R.id.row3, R.id.row4};
        int[] checks = {R.id.check1, R.id.check2, R.id.check3, R.id.check4};
        int[] titles = {R.id.task1, R.id.task2, R.id.task3, R.id.task4};
        for (int id : ids) {
            Bundle options = manager.getAppWidgetOptions(id);
            int width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 140);
            int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140);
            boolean compact = width < 250 && idIsSmall(context, manager, id);
            boolean wide = !compact && height < 220;
            RemoteViews views = new RemoteViews(context.getPackageName(), compact ? R.layout.widget_small
                    : wide ? R.layout.widget_wide : R.layout.widget_list);
            views.setTextViewText(R.id.widget_title, timed ? "定时任务" : "我的待办");
            views.setTextViewText(R.id.widget_count, compact ? String.valueOf(tasks.size()) : "待完成 " + tasks.size());
            if (wide) views.setTextViewText(R.id.widget_add, timed ? "＋ 新建任务" : "＋ 新建待办");
            views.setOnClickPendingIntent(R.id.widget_title, open(context, timed, -1, false));
            views.setOnClickPendingIntent(R.id.widget_add, open(context, timed, -1, true));
            if (compact) {
                TaskStore.Task task = tasks.isEmpty() ? null : tasks.get(0);
                views.setTextViewText(R.id.widget_task, task == null ? "待办清空啦 ✦" : task.title);
                views.setTextViewText(R.id.widget_due, task == null ? "今天轻松一点" : shortDue(task));
                if (task != null) views.setOnClickPendingIntent(R.id.widget_task, open(context, timed, task.id, false));
            } else {
                int capacity = wide ? 2 : height >= 320 ? 4 : 2;
                for (int i = 0; i < (wide ? 2 : 4); i++) {
                    TaskStore.Task task = i < Math.min(capacity, tasks.size()) ? tasks.get(i) : null;
                    views.setViewVisibility(rows[i], task == null ? View.GONE : View.VISIBLE);
                    if (task == null) continue;
                    views.setTextViewText(titles[i], wide ? task.title : task.title + " · " + due(task));
                    if (wide) views.setTextViewText(i == 0 ? R.id.due1 : R.id.due2, due(task));
                    views.setOnClickPendingIntent(titles[i], open(context, timed, task.id, false));
                    Intent complete = new Intent(context, timed ? Timed.class : Todo.class).setAction(COMPLETE)
                            .setData(Uri.parse("xiuyi://widget/complete/" + task.id + "/" + task.dueAt))
                            .putExtra("task_id", task.id).putExtra("due_at", task.dueAt);
                    views.setOnClickPendingIntent(checks[i], PendingIntent.getBroadcast(context, 0, complete,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                }
                views.setOnClickPendingIntent(R.id.widget_more, open(context, timed, -1, false));
                if (tasks.isEmpty()) {
                    views.setViewVisibility(R.id.row1, View.VISIBLE);
                    views.setTextViewText(R.id.task1, timed ? "还没有定时任务" : "待办清空啦 ✦");
                    views.setTextViewText(R.id.check1, "✦");
                    if (wide) views.setTextViewText(R.id.due1, "点击＋添加第一项");
                    views.setOnClickPendingIntent(R.id.task1, open(context, timed, -1, false));
                }
            }
            manager.updateAppWidget(id, views);
        }
    }

    private static boolean idIsSmall(Context context, AppWidgetManager manager, int id) {
        for (int item : manager.getAppWidgetIds(new ComponentName(context, TodoWide.class))) if (item == id) return false;
        for (int item : manager.getAppWidgetIds(new ComponentName(context, TimedWide.class))) if (item == id) return false;
        return true;
    }

    private static PendingIntent open(Context context, boolean timed, long taskId, boolean create) {
        Intent intent = new Intent(context, MainActivity.class)
                .setData(Uri.parse("xiuyi://widget/open/" + (timed ? "timed" : "todo") + "/" + taskId + "/" + create))
                .putExtra("widget_timed", timed).putExtra("widget_new", create)
                .putExtra("task_id", taskId)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static String due(TaskStore.Task task) {
        if (task.dueAt == 0) return "无截止时间";
        if (task.dueAt < System.currentTimeMillis()) return "已逾期";
        return new SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(new Date(task.dueAt));
    }

    private static String shortDue(TaskStore.Task task) {
        if (task.dueAt == 0) return "无截止时间";
        if (task.dueAt < System.currentTimeMillis()) return "已逾期";
        return new SimpleDateFormat("MM/dd HH:mm", Locale.CHINA).format(new Date(task.dueAt));
    }
}
