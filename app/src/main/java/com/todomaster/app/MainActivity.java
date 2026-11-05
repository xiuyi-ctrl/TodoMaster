package com.todomaster.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.media.MediaPlayer;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(255, 249, 242);
    private static final int NAVY = Color.rgb(59, 53, 81);
    private static final int MUTED = Color.rgb(111, 103, 129);
    private static final int BLUE = Color.rgb(141, 115, 232);
    private static final int VIOLET = Color.rgb(112, 82, 193);
    private static final int PALE = Color.rgb(240, 233, 255);
    private static final int CORAL = Color.rgb(255, 200, 181);
    private static final int MINT = Color.rgb(191, 233, 214);
    private static final int MIDDLE = Color.rgb(233, 220, 250);
    private static final int LOW = Color.rgb(220, 244, 230);
    private static final String[] CATEGORIES = {"学习", "游戏", "工作", "日常", "其他"};
    private static final String[] PRIORITIES = {"高", "中", "低"};
    private static final String[] FILTERS = {"未完成", "全部", "已完成"};
    private static final String[] SEARCH_CATEGORIES = {"全部", "学习", "游戏", "工作", "日常", "其他"};
    private static final String[] SEARCH_STATUSES = {"全部", "未完成", "已完成"};
    private static final String[] SEARCH_PRIORITIES = {"全部", "高", "中", "低"};
    private static final String[] SEARCH_TIMES = {"全部", "已逾期", "今天", "未来7天", "无截止"};
    private static final String[] REMINDERS = {"不提醒", "5分钟", "15分钟", "1小时", "1天"};
    private static final long[] ADVANCE = {0, 5, 15, 60, 1440};
    private static final String SETTINGS_FILE = "xiuyi_settings";
    private static final String DATE_PICKER_MODE = "date_picker_mode";
    private static final int PICK_REMINDER_SOUND = 2;
    private final SimpleDateFormat dateTime = new SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA);
    private final SimpleDateFormat dateOnly = new SimpleDateFormat("MM月dd日", Locale.CHINA);
    private final SimpleDateFormat timeOnly = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private TaskStore store;
    private FrameLayout surface;
    private View homeHeading;
    private int topInset;
    private LinearLayout list;
    private ScrollView homeScroll;
    private TextView summary;
    private String filter = "未完成";
    private String todoFilter = "未完成";
    private String timedFilter = "未完成";
    private int todoScrollY;
    private int timedScrollY;
    private String query = "";
    private String searchCategory = "全部";
    private String searchStatus = "全部";
    private String searchPriority = "全部";
    private String searchTime = "全部";
    private boolean searchFiltersExpanded = true;
    private boolean trash;
    private boolean searching;
    private boolean settings;
    private boolean timedPage;
    private String settingsPage = "home";
    private boolean editing;
    private ValueAnimator taskAnimator;
    private ValueAnimator filterColorAnimator;
    private int taskTransitionToken;
    private int filterTransitionToken;
    private boolean exactAlarmAllowed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new TaskStore(this);
        ReminderSound.migrateOldCustom(this);
        exactAlarmAllowed = ReminderScheduler.canScheduleExactly(this);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        for (TaskStore.Task task : store.all(false)) {
            store.advanceTimed(task);
            ReminderScheduler.update(this, task);
        }
        showHome();
        openFromNotification(getIntent());
    }

    @Override protected void onResume() {
        super.onResume();
        boolean advanced = false;
        for (TaskStore.Task task : store.all(false)) {
            if (task.timed && !task.paused && task.repeatDays > 0 && task.dueAt <= System.currentTimeMillis()) {
                store.advanceTimed(task);
                ReminderScheduler.update(this, task);
                advanced = true;
            }
        }
        if (advanced && !editing && !trash && !searching && !settings) showHome();
        boolean allowed = ReminderScheduler.canScheduleExactly(this);
        if (allowed != exactAlarmAllowed) {
            exactAlarmAllowed = allowed;
            for (TaskStore.Task task : store.all(false)) ReminderScheduler.update(this, task);
            if (settings) showSettingsPage();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 1 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            askExactAlarmPermission();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_REMINDER_SOUND || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            String name = uri.getLastPathSegment();
            try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
            }
            Uri saved = ReminderSound.importAudio(this, uri, name);
            getSharedPreferences(SETTINGS_FILE, MODE_PRIVATE).edit()
                    .putString(ReminderSound.URI, saved.toString()).putString(ReminderSound.NAME, name)
                    .putString(ReminderSound.MODE, "custom").apply();
            showSettingsPage();
            toast("已选择提醒声音");
        } catch (SecurityException | java.io.IOException error) {
            toast("无法读取音频，请重新选择");
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openFromNotification(intent);
    }

    @Override public void onBackPressed() {
        if (editing) { editing = false; showHome(); }
        else if (trash) { trash = false; showHome(); }
        else if (searching) { searching = false; query = ""; showHome(); }
        else if (settings) { if (settingsPage.equals("home")) showHome(); else showSettings(); }
        else super.onBackPressed();
    }

    private void openFromNotification(Intent intent) {
        long id = intent.getLongExtra("task_id", -1);
        intent.removeExtra("task_id");
        if (id > 0) {
            TaskStore.Task task = store.get(id);
            if (task != null && !task.deleted) {
                timedPage = task.timed;
                edit(task);
            }
        }
    }

    private boolean useWheelDatePicker() {
        return !"system".equals(getSharedPreferences(SETTINGS_FILE, MODE_PRIVATE)
                .getString(DATE_PICKER_MODE, "wheel"));
    }

    private void showSettings() {
        settingsPage = "home";
        showSettingsPage();
    }

    private void showSettingsPage() {
        cancelTaskAnimation();
        filterTransitionToken++;
        if (filterColorAnimator != null) filterColorAnimator.cancel();
        if (list != null) list.animate().cancel();
        editing = false;
        searching = false;
        trash = false;
        settings = true;
        LinearLayout root = column();
        root.setBackgroundColor(BACKGROUND);
        setScreen(root, gutter());
        LinearLayout header = row();
        TextView back = text("‹", 34, NAVY, false);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));
        back.setOnClickListener(v -> { if (settingsPage.equals("home")) showHome(); else showSettings(); });
        TextView heading = text(settingsPage.equals("date") ? "日期选择方式"
                : settingsPage.equals("exact") ? "精准提醒"
                : settingsPage.equals("sound") ? "提醒声音" : "设置", 23, NAVY, true);
        heading.setGravity(Gravity.CENTER);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(52), 1));
        header.addView(cloud(), new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = column();
        content.setPadding(0, dp(14), 0, dp(16));
        scroll.addView(content);
        if (settingsPage.equals("home")) {
            LinearLayout intro = row();
            intro.setGravity(Gravity.CENTER_VERTICAL);
            intro.setPadding(dp(14), dp(10), dp(14), dp(10));
            intro.setBackground(shape(PALE, 18, 0));
            intro.addView(cloud(), new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout introText = column();
            introText.setPadding(dp(10), 0, 0, 0);
            introText.addView(text("调整你喜欢的节奏", 16, NAVY, true));
            introText.addView(text("让每一件小事都刚刚好 ✦", 13, MUTED, false));
            intro.addView(introText);
            LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, -2);
            introParams.bottomMargin = dp(18);
            content.addView(intro, introParams);
            addSettingsEntry(content, "日期选择方式", useWheelDatePicker() ? "滚轮选择" : "系统日期与时间弹窗", "date", R.drawable.ic_calendar);
            addSettingsEntry(content, "精准提醒", ReminderScheduler.preciseEnabled(this)
                    ? ReminderScheduler.canScheduleExactly(this) ? "已开启" : "未获系统授权" : "已关闭", "exact", R.drawable.ic_bell);
            String mode = ReminderSound.mode(this);
            addSettingsEntry(content, "提醒声音", "custom".equals(mode) ? "自定义音频"
                    : "system".equals(mode) ? "系统默认提示音" : "咻咿提示音", "sound", R.drawable.ic_settings);
            return;
        }
        if (settingsPage.equals("date")) {
        LinearLayout group = formGroup(content);
        group.addView(section("日期选择方式"));
        TextView description = text("新建和编辑待办时使用，选择会自动保存。", 13, MUTED, false);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.bottomMargin = dp(10);
        group.addView(description, descriptionParams);
        boolean wheel = useWheelDatePicker();
        addDatePickerOption(group, "滚轮选择", "同时选择日期和具体分钟", wheel, "wheel");
        addDatePickerOption(group, "系统日期与时间弹窗", "先选日期，再选时间", !wheel, "system");
        }
        if (settingsPage.equals("exact")) {
        LinearLayout alarmGroup = formGroup(content);
        alarmGroup.addView(section("精准提醒"));
        boolean exact = ReminderScheduler.canScheduleExactly(this);
        boolean enabled = ReminderScheduler.preciseEnabled(this);
        TextView status = text(enabled ? exact ? "已开启：尽量按设定时间提醒" : "系统尚未授权，暂使用普通提醒"
                : "已关闭：使用普通提醒", 14, MUTED, false);
        alarmGroup.addView(status);
        TextView toggle = primary(enabled ? "关闭精准提醒" : "开启精准提醒");
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(-1, dp(46));
        toggleParams.topMargin = dp(12);
        alarmGroup.addView(toggle, toggleParams);
        toggle.setOnClickListener(v -> {
            getSharedPreferences(SETTINGS_FILE, MODE_PRIVATE).edit().putBoolean("precise_reminders", !enabled).apply();
            for (TaskStore.Task task : store.all(false)) ReminderScheduler.update(this, task);
            showSettingsPage();
            if (!enabled && !exact) openExactAlarmSettings();
        });
        if (enabled && !exact) {
            TextView enableExact = selectable("前往系统授权");
            LinearLayout.LayoutParams exactParams = new LinearLayout.LayoutParams(-1, dp(46));
            exactParams.topMargin = dp(12);
            alarmGroup.addView(enableExact, exactParams);
            enableExact.setOnClickListener(v -> openExactAlarmSettings());
        }
        }
        if (settingsPage.equals("sound")) {
        LinearLayout soundGroup = formGroup(content);
        soundGroup.addView(section("提醒声音"));
        String soundMode = ReminderSound.mode(this);
        String soundName = getSharedPreferences(SETTINGS_FILE, MODE_PRIVATE).getString(ReminderSound.NAME, "");
        TextView current = text("当前：" + ("custom".equals(soundMode) ? soundName
                : "system".equals(soundMode) ? "系统默认提示音" : "咻咿提示音"), 14, MUTED, false);
        current.setMaxLines(2);
        soundGroup.addView(current);
        TextView builtInSound = selectable("使用咻咿提示音（推荐）");
        LinearLayout.LayoutParams builtInParams = new LinearLayout.LayoutParams(-1, dp(44));
        builtInParams.topMargin = dp(10);
        soundGroup.addView(builtInSound, builtInParams);
        builtInSound.setOnClickListener(v -> {
            getSharedPreferences(SETTINGS_FILE, MODE_PRIVATE).edit().putString(ReminderSound.MODE, "built_in").apply();
            showSettingsPage();
            toast("已使用咻咿提示音");
        });
        TextView defaultSound = selectable("使用系统默认提示音");
        LinearLayout.LayoutParams defaultParams = new LinearLayout.LayoutParams(-1, dp(44));
        defaultParams.topMargin = dp(10);
        soundGroup.addView(defaultSound, defaultParams);
        defaultSound.setOnClickListener(v -> {
            if (RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION) == null) {
                toast("系统未设置默认提示音，请使用咻咿提示音");
                return;
            }
            getSharedPreferences(SETTINGS_FILE, MODE_PRIVATE).edit().putString(ReminderSound.MODE, "system").apply();
            showSettingsPage();
            toast("已使用系统默认提示音");
        });
        TextView chooseSound = primary("上传／选择本机音频");
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(-1, dp(46));
        chooseParams.topMargin = dp(10);
        soundGroup.addView(chooseSound, chooseParams);
        chooseSound.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("audio/*");
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(pick, PICK_REMINDER_SOUND);
        });
        TextView previewSound = selectable("试听当前声音");
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(44));
        previewParams.topMargin = dp(10);
        soundGroup.addView(previewSound, previewParams);
        previewSound.setOnClickListener(v -> {
            MediaPlayer player = new MediaPlayer();
            try {
                player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                player.setDataSource(this, ReminderSound.sound(this));
                player.prepare();
                player.start();
                previewSound.postDelayed(() -> { if (player.isPlaying()) player.stop(); player.release(); }, 3000);
            } catch (Exception error) {
                player.release();
                toast("当前声音无法播放，请重新导入音频");
            }
        });
        TextView testSound = selectable("发送测试提醒");
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(-1, dp(44));
        testParams.topMargin = dp(8);
        soundGroup.addView(testSound, testParams);
        testSound.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                return;
            }
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            String channel = ReminderSound.channel(this, manager);
            manager.cancel(987654);
            try {
                manager.notify(987654, new android.app.Notification.Builder(this, channel)
                        .setSmallIcon(R.drawable.ic_cloud_notification)
                        .setContentTitle("咻咿待办测试提醒")
                        .setContentText("如果听不到声音，请检查系统通知设置和静音模式。")
                        .setAutoCancel(true).build());
            } catch (SecurityException error) { toast("请在系统设置中允许通知"); }
        });
        TextView systemSound = selectable("打开系统通知设置");
        LinearLayout.LayoutParams systemParams = new LinearLayout.LayoutParams(-1, dp(44));
        systemParams.topMargin = dp(10);
        soundGroup.addView(systemSound, systemParams);
        systemSound.setOnClickListener(v -> {
            Intent settingsIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(settingsIntent);
        });
        TextView soundHint = text("手机静音、勿扰模式或系统通知渠道静音时，提醒仍可能没有声音。", 12, MUTED, false);
        LinearLayout.LayoutParams soundHintParams = new LinearLayout.LayoutParams(-1, -2);
        soundHintParams.topMargin = dp(8);
        soundGroup.addView(soundHint, soundHintParams);
        }
    }

    private void addSettingsEntry(LinearLayout content, String title, String summary, String page, int iconResource) {
        LinearLayout card = row();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(dp(76));
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.WHITE, Color.rgb(255, 250, 254)});
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), Color.rgb(232, 222, 239));
        card.setBackground(background);
        card.setElevation(dp(2));
        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(shape(PALE, 14, 0));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(VIOLET);
        iconBox.addView(icon, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        card.addView(iconBox, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout labels = column();
        labels.setPadding(dp(12), 0, dp(6), 0);
        labels.addView(text(title, 16, NAVY, true));
        TextView detail = text(summary, 13, MUTED, false);
        detail.setPadding(0, dp(3), 0, 0);
        labels.addView(detail);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chevron = text("›", 28, VIOLET, false);
        chevron.setGravity(Gravity.CENTER);
        card.addView(chevron, new LinearLayout.LayoutParams(dp(30), dp(44)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        content.addView(card, params);
        card.setOnClickListener(v -> { settingsPage = page; showSettingsPage(); });
    }

    private void addDatePickerOption(LinearLayout group, String title, String description,
                                     boolean selected, String mode) {
        LinearLayout option = row();
        option.setPadding(dp(12), 0, dp(12), 0);
        option.setBackground(shape(selected ? PALE : Color.WHITE, 14,
                selected ? BLUE : Color.rgb(232, 222, 239)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(70));
        params.bottomMargin = dp(8);
        group.addView(option, params);
        LinearLayout labels = column();
        labels.addView(text(title, 15, NAVY, true));
        TextView subtitle = text(description, 12, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, 0);
        labels.addView(subtitle);
        option.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView radio = text(selected ? "●" : "○", 23, selected ? BLUE : MUTED, false);
        radio.setGravity(Gravity.CENTER);
        option.addView(radio, new LinearLayout.LayoutParams(dp(36), dp(48)));
        option.setOnClickListener(v -> {
            if (selected) return;
            getSharedPreferences(SETTINGS_FILE, MODE_PRIVATE).edit().putString(DATE_PICKER_MODE, mode).apply();
            showSettingsPage();
            toast("已切换为" + title);
        });
    }

    private void showHome() {
        cancelTaskAnimation();
        settings = false;
        filterTransitionToken++;
        if (filterColorAnimator != null) filterColorAnimator.cancel();
        if (list != null) list.animate().cancel();
        editing = false;
        int gutter = gutter();
        LinearLayout root = column();
        root.setBackgroundColor(BACKGROUND);
        setScreen(root, gutter);

        LinearLayout header = row();
        if (trash || searching) {
            TextView back = text("‹", 34, NAVY, false);
            back.setGravity(Gravity.CENTER);
            header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));
            back.setOnClickListener(v -> { trash = false; searching = false; query = ""; showHome(); });
            TextView title = text(trash ? "回收站" : timedPage ? "搜索定时任务" : "搜索待办", 23, NAVY, true);
            title.setGravity(Gravity.CENTER);
            header.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
            header.addView(new View(this), new LinearLayout.LayoutParams(dp(48), 1));
        } else {
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.ic_cloud_outline);
            LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(44), dp(44));
            logoParams.rightMargin = dp(8);
            header.addView(logo, logoParams);
            TextView heading = text("咻咿待办", 21, NAVY, true);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1));
            ImageView searchIcon = new ImageView(this);
            searchIcon.setImageResource(R.drawable.ic_search);
            searchIcon.setColorFilter(NAVY);
            searchIcon.setPadding(dp(12), dp(12), dp(12), dp(12));
            header.addView(searchIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));
            searchIcon.setOnClickListener(v -> {
                searching = true;
                query = "";
                searchCategory = searchStatus = searchPriority = searchTime = "全部";
                searchFiltersExpanded = true;
                showHome();
            });
            TextView menu = text("⋮", 28, NAVY, false);
            menu.setGravity(Gravity.CENTER);
            header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
            menu.setOnClickListener(v -> {
                showChoiceMenu(menu, 168, new String[]{"回收站", "设置"},
                        new int[]{R.drawable.ic_trash, R.drawable.ic_settings},
                        new Runnable[]{() -> { trash = true; showHome(); }, this::showSettings});
            });
        }
        root.addView(header);

        if (searching) {
            FrameLayout searchField = new FrameLayout(this);
            LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, dp(48));
            fieldParams.topMargin = dp(10);
            root.addView(searchField, fieldParams);
            EditText search = input(timedPage ? "搜索定时任务标题" : "搜索任务标题", query, true);
            search.setPadding(dp(42), 0, dp(42), 0);
            searchField.addView(search, new FrameLayout.LayoutParams(-1, -1));
            ImageView magnifier = new ImageView(this);
            magnifier.setImageResource(R.drawable.ic_search);
            magnifier.setColorFilter(VIOLET);
            magnifier.setPadding(dp(12), dp(12), dp(12), dp(12));
            searchField.addView(magnifier, new FrameLayout.LayoutParams(dp(42), -1, Gravity.START));
            ImageView clearSearch = new ImageView(this);
            clearSearch.setImageResource(R.drawable.ic_clear);
            clearSearch.setContentDescription("清空搜索");
            clearSearch.setPadding(dp(12), dp(12), dp(12), dp(12));
            searchField.addView(clearSearch, new FrameLayout.LayoutParams(dp(42), -1, Gravity.END));
            clearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
            clearSearch.setOnClickListener(v -> search.setText(""));
            search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    query = s.toString().trim();
                    clearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                    render();
                }
                public void afterTextChanged(Editable s) { }
            });
            addSearchFilters(root);
        }

        if (!trash && !searching) {
            TextView pageTitle = text(timedPage ? "定时任务  ⌄" : "我的待办  ⌄", 24, NAVY, true);
            FrameLayout pageHeading = new FrameLayout(this);
            View underline = new View(this);
            underline.setBackground(shape(PALE, 8, 0));
            FrameLayout.LayoutParams underlineParams = new FrameLayout.LayoutParams(dp(126), dp(7), Gravity.START | Gravity.BOTTOM);
            pageHeading.addView(underline, underlineParams);
            pageHeading.addView(pageTitle, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(-1, dp(36));
            pageParams.topMargin = dp(14);
            root.addView(pageHeading, pageParams);
            homeHeading = pageHeading;
            pageHeading.setContentDescription(timedPage ? "切换到我的待办" : "切换到定时任务");
            pageHeading.setOnClickListener(v -> {
                if (timedPage) { timedFilter = filter; timedScrollY = homeScroll.getScrollY(); }
                else { todoFilter = filter; todoScrollY = homeScroll.getScrollY(); }
                timedPage = !timedPage;
                filter = timedPage ? timedFilter : todoFilter;
                showHome();
                homeScroll.post(() -> homeScroll.scrollTo(0, timedPage ? timedScrollY : todoScrollY));
            });
        }

        summary = text("", 15, NAVY, false);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, dp(34));
        summaryParams.topMargin = dp(4);
        if (!trash && !searching) root.addView(summary, summaryParams);

        if (!trash && !searching) {
            FrameLayout filters = new FrameLayout(this);
            filters.setBackground(shape(Color.WHITE, 22, Color.rgb(225, 214, 244)));
            LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(-1, dp(46));
            filterParams.topMargin = dp(10);
            root.addView(filters, filterParams);
            View indicator = new View(this);
            indicator.setBackground(shape(BLUE, 18, 0));
            FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(0, -1, Gravity.START);
            indicatorParams.setMargins(dp(3), dp(3), 0, dp(3));
            filters.addView(indicator, indicatorParams);
            LinearLayout options = row();
            options.setPadding(dp(3), dp(3), dp(3), dp(3));
            filters.addView(options, new FrameLayout.LayoutParams(-1, -1));
            TextView[] chips = new TextView[FILTERS.length];
            for (int i = 0; i < FILTERS.length; i++) {
                String value = FILTERS[i];
                boolean selected = value.equals(filter);
                TextView chip = text(value, 14, selected ? Color.WHITE : NAVY, selected);
                chip.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1);
                options.addView(chip, cp);
                chips[i] = chip;
                chip.setOnClickListener(v -> animateFilterChange(value, chips, indicator));
            }
            filters.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int width = (right - left - dp(6)) / FILTERS.length;
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) indicator.getLayoutParams();
                if (params.width != width) { params.width = width; indicator.setLayoutParams(params); }
                indicator.setTranslationX(width * filterIndex(filter));
            });
        }

        ScrollView scroll = new ScrollView(this);
        homeScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        list = column();
        list.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (!trash && !searching) {
            TextView add = primary(timedPage ? "＋ 添加定时任务" : "＋ 添加待办");
            root.addView(add, new LinearLayout.LayoutParams(-1, dp(48)));
            add.setOnClickListener(v -> edit(null));
        }
        render();
    }

    private int filterIndex(String value) {
        for (int i = 0; i < FILTERS.length; i++) if (FILTERS[i].equals(value)) return i;
        return 0;
    }

    private void animateFilterChange(String value, TextView[] chips, View indicator) {
        if (value.equals(filter)) return;
        cancelTaskAnimation();
        filter = value;
        query = "";
        int token = ++filterTransitionToken;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            for (int i = 0; i < chips.length; i++) {
                boolean selected = FILTERS[i].equals(value);
                chips[i].setTextColor(selected ? Color.WHITE : NAVY);
                chips[i].setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            }
            int width = ((android.view.ViewGroup) indicator.getParent()).getWidth();
            indicator.setTranslationX((width - dp(6)) / FILTERS.length * filterIndex(value));
            list.setAlpha(1f);
            render();
            return;
        }
        int[] oldColors = new int[chips.length];
        for (int i = 0; i < chips.length; i++) {
            oldColors[i] = chips[i].getCurrentTextColor();
            chips[i].setTypeface(null, FILTERS[i].equals(value) ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (filterColorAnimator != null) filterColorAnimator.cancel();
        ArgbEvaluator colors = new ArgbEvaluator();
        filterColorAnimator = ValueAnimator.ofFloat(0f, 1f);
        filterColorAnimator.setDuration(220);
        filterColorAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            for (int i = 0; i < chips.length; i++) {
                int target = FILTERS[i].equals(value) ? Color.WHITE : NAVY;
                chips[i].setTextColor((int) colors.evaluate(progress, oldColors[i], target));
            }
        });
        filterColorAnimator.start();
        int width = ((android.view.ViewGroup) indicator.getParent()).getWidth();
        int segment = (width - dp(6)) / FILTERS.length;
        indicator.animate().cancel();
        indicator.animate().translationX(segment * filterIndex(value)).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
        list.animate().cancel();
        list.animate().alpha(0f).setDuration(100).withEndAction(() -> {
            if (token != filterTransitionToken) return;
            render();
            list.setAlpha(0f);
            list.animate().alpha(1f).setDuration(160).start();
        }).start();
    }

    private void addSearchFilters(LinearLayout root) {
        LinearLayout panel = column();
        panel.setPadding(dp(10), dp(6), dp(10), dp(8));
        panel.setBackground(shape(Color.WHITE, 14, Color.rgb(232, 222, 239)));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(-1, -2);
        panelParams.topMargin = dp(8);
        root.addView(panel, panelParams);

        LinearLayout heading = row();
        panel.addView(heading, new LinearLayout.LayoutParams(-1, dp(36)));
        TextView toggle = text(searchFiltersExpanded ? "筛选条件  ⌃" : "筛选条件  ⌄", 14, NAVY, true);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(toggle, new LinearLayout.LayoutParams(0, -1, 1));
        TextView reset = text("重置", 13, VIOLET, true);
        reset.setGravity(Gravity.CENTER);
        heading.addView(reset, new LinearLayout.LayoutParams(dp(48), -1));

        LinearLayout choices = column();
        panel.addView(choices);
        choices.setVisibility(searchFiltersExpanded ? View.VISIBLE : View.GONE);
        toggle.setOnClickListener(v -> {
            searchFiltersExpanded = !searchFiltersExpanded;
            choices.setVisibility(searchFiltersExpanded ? View.VISIBLE : View.GONE);
            toggle.setText(searchFiltersExpanded ? "筛选条件  ⌃" : "筛选条件  ⌄");
        });

        LinearLayout categories = addSearchFilterRow(choices, "分类", SEARCH_CATEGORIES, searchCategory,
                value -> { searchCategory = value; render(); });
        LinearLayout statuses = addSearchFilterRow(choices, "状态", SEARCH_STATUSES, searchStatus,
                value -> { searchStatus = value; render(); });
        LinearLayout priorities = addSearchFilterRow(choices, "优先级", SEARCH_PRIORITIES, searchPriority,
                value -> { searchPriority = value; render(); });
        LinearLayout times = addSearchFilterRow(choices, "截止", SEARCH_TIMES, searchTime,
                value -> { searchTime = value; render(); });
        reset.setOnClickListener(v -> {
            searchCategory = searchStatus = searchPriority = searchTime = "全部";
            fillSearchOptions(categories, SEARCH_CATEGORIES, searchCategory, value -> { searchCategory = value; render(); });
            fillSearchOptions(statuses, SEARCH_STATUSES, searchStatus, value -> { searchStatus = value; render(); });
            fillSearchOptions(priorities, SEARCH_PRIORITIES, searchPriority, value -> { searchPriority = value; render(); });
            fillSearchOptions(times, SEARCH_TIMES, searchTime, value -> { searchTime = value; render(); });
            render();
        });
    }

    private LinearLayout addSearchFilterRow(LinearLayout parent, String label, String[] options,
                                            String selected, ChoiceListener listener) {
        LinearLayout row = row();
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(42));
        parent.addView(row, rowParams);
        TextView heading = text(label, 12, MUTED, true);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(heading, new LinearLayout.LayoutParams(dp(48), -1));
        LinearLayout chips = row();
        HorizontalScrollView scroll = horizontal(chips);
        row.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        fillSearchOptions(chips, options, selected, listener);
        return chips;
    }

    private void fillSearchOptions(LinearLayout holder, String[] options, String selected, ChoiceListener listener) {
        holder.removeAllViews();
        for (String option : options) {
            boolean active = option.equals(selected);
            TextView chip = text(option, 12, active ? Color.WHITE : NAVY, active);
            chip.setGravity(Gravity.CENTER);
            chip.setMinWidth(dp(48));
            chip.setPadding(dp(10), 0, dp(10), 0);
            chip.setBackground(shape(active ? BLUE : PALE, 12, 0));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(34));
            params.rightMargin = dp(5);
            holder.addView(chip, params);
            chip.setOnClickListener(v -> {
                fillSearchOptions(holder, options, option, listener);
                listener.selected(option);
            });
        }
    }

    private boolean matchesSearchFilters(TaskStore.Task task) {
        if (!searchCategory.equals("全部") && !searchCategory.equals(task.category)) return false;
        if (searchStatus.equals("未完成") && task.done) return false;
        if (searchStatus.equals("已完成") && !task.done) return false;
        if (!searchPriority.equals("全部") && !searchPriority.equals(task.priority)) return false;
        if (searchTime.equals("全部")) return true;
        if (searchTime.equals("无截止")) return task.dueAt == 0;
        if (task.dueAt == 0) return false;
        long now = System.currentTimeMillis();
        if (searchTime.equals("已逾期")) return !task.done && task.dueAt < now;
        Calendar boundary = Calendar.getInstance();
        if (searchTime.equals("今天")) {
            boundary.set(Calendar.HOUR_OF_DAY, 0);
            boundary.set(Calendar.MINUTE, 0);
            boundary.set(Calendar.SECOND, 0);
            boundary.set(Calendar.MILLISECOND, 0);
            long start = boundary.getTimeInMillis();
            boundary.add(Calendar.DAY_OF_YEAR, 1);
            return task.dueAt >= start && task.dueAt < boundary.getTimeInMillis();
        }
        boundary.add(Calendar.DAY_OF_YEAR, 7);
        return task.dueAt >= now && task.dueAt < boundary.getTimeInMillis();
    }

    private void render() {
        list.removeAllViews();
        List<TaskStore.Task> tasks = store.all(trash);
        int complete = 0, visible = 0;
        for (TaskStore.Task task : tasks) {
            if (!trash && task.timed != timedPage) continue;
            if (task.done) complete++;
            if (!trash && !searching && ((filter.equals("未完成") && task.done) || (filter.equals("已完成") && !task.done))) continue;
            if (searching && !task.title.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
            if (searching && !matchesSearchFilters(task)) continue;
            if (trash) addTrashCard(task);
            else addTask(task);
            visible++;
        }
        int total = 0;
        for (TaskStore.Task task : tasks) if (trash || task.timed == timedPage) total++;
        summary.setText(trash ? "回收站  " + tasks.size() + " 项" : timedPage
                ? "进行中  " + (total - complete) + "   │   已完成  " + complete
                : "待完成  " + (total - complete) + "   │   已完成  " + complete);
        list.setGravity(visible == 0 ? Gravity.CENTER : Gravity.TOP);
        if (visible == 0) {
            LinearLayout empty = column();
            empty.setGravity(Gravity.CENTER);
            ImageView mascot = new ImageView(this);
            mascot.setImageResource(trash || searching ? R.drawable.xiuyi_cloud : R.drawable.xiuyi_empty);
            mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mascot.setContentDescription(trash || searching ? "咻咿云朵" : "咻咿趴在手账上");
            empty.addView(mascot, new LinearLayout.LayoutParams(dp(trash || searching ? 130 : 190), dp(trash || searching ? 110 : 165)));
            boolean hasSearchCondition = !query.isEmpty() || !searchCategory.equals("全部")
                    || !searchStatus.equals("全部") || !searchPriority.equals("全部") || !searchTime.equals("全部");
            TextView title = text(trash ? "回收站空空的" : searching && hasSearchCondition
                    ? timedPage ? "没有找到相关定时任务" : "没有找到相关待办"
                    : timedPage ? "还没有定时任务" : "还没有待办事项", 18, NAVY, true);
            title.setGravity(Gravity.CENTER);
            empty.addView(title);
            TextView subtitle = text(trash ? "整理得真干净" : searching && hasSearchCondition ? "试试其他关键词或筛选条件" : "先记下一件想做的事吧，一点点也很棒～", 14, MUTED, false);
            subtitle.setGravity(Gravity.CENTER);
            subtitle.setPadding(0, dp(8), 0, 0);
            empty.addView(subtitle);
            if (searching && hasSearchCondition) {
                TextView clear = text("清空搜索", 14, VIOLET, true);
                clear.setGravity(Gravity.CENTER);
                empty.addView(clear, new LinearLayout.LayoutParams(-1, dp(48)));
                clear.setOnClickListener(v -> {
                    query = "";
                    searchCategory = searchStatus = searchPriority = searchTime = "全部";
                    showHome();
                });
            }
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(330)));
        }
    }

    private void addTask(TaskStore.Task task) {
        LinearLayout card = column();
        card.setPadding(dp(12), dp(8), dp(12), dp(10));
        GradientDrawable cardBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                task.done ? new int[]{Color.rgb(248, 253, 249), Color.rgb(241, 251, 245)}
                        : new int[]{Color.WHITE, Color.rgb(255, 250, 252)});
        cardBackground.setCornerRadius(dp(16));
        cardBackground.setStroke(dp(1), Color.rgb(238, 230, 235));
        card.setBackground(cardBackground);
        card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        list.addView(card, params);

        LinearLayout top = row();
        CheckBox check = new CheckBox(this);
        check.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{Color.rgb(95, 170, 133), NAVY}));
        check.setChecked(task.done);
        if (task.timed && task.repeatDays == 0) check.setEnabled(false);
        check.setPadding(0, 0, dp(6), 0);
        top.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text(task.title, 17, task.done ? MUTED : NAVY, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        check.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                store.complete(task);
            } else {
                task.done = false;
                store.save(task);
            }
            ReminderScheduler.update(this, task);
            animateTaskChange(task, card, check, title, cardBackground, checked);
        });
        TextView more = text("⋮", 24, BLUE, false);
        more.setGravity(Gravity.CENTER);
        more.setContentDescription("任务操作");
        top.addView(more, new LinearLayout.LayoutParams(dp(40), dp(48)));
        card.addView(top);
        more.setOnClickListener(v -> showTaskMenu(more, task));
        if (!task.timed || task.repeatDays > 0) card.setOnClickListener(v -> edit(task));

        LinearLayout badges = row();
        badges.setPadding(dp(48), 0, 0, dp(4));
        LinearLayout category = badgeWithIcon(task.category, PALE, VIOLET, categoryIcon(task.category));
        badges.addView(category);
        int priorityBackground = task.priority.equals("高") ? CORAL : task.priority.equals("中") ? MIDDLE : LOW;
        LinearLayout priority = badgeWithIcon(task.priority + "优先级", priorityBackground, NAVY, R.drawable.ic_flag);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.leftMargin = dp(8);
        badges.addView(priority, bp);
        card.addView(badges);
        if (task.note != null && !task.note.isEmpty() && getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density >= 412) {
            TextView note = text(task.note, 13, MUTED, false);
            note.setMaxLines(2);
            note.setPadding(dp(48), 0, 0, dp(6));
            card.addView(note);
        }
        if (task.dueAt > 0) {
            String due = task.dueAt < System.currentTimeMillis() && !task.done ? "已逾期 " : shortDue(task.dueAt);
            if (!task.done && (task.remindAt > 0 || task.snoozeAt > System.currentTimeMillis())) card.addView(compactMeta(due, reminderLabel(task)));
            else card.addView(meta(R.drawable.ic_calendar, due, false));
        }
        if (task.remindAt > 0 && !task.done && task.dueAt == 0) card.addView(meta(R.drawable.ic_bell, reminderLabel(task), false));
        if (task.timed && task.repeatDays > 0) card.addView(meta(R.drawable.ic_clock,
                task.paused ? "已暂停" : task.repeatDays == 1 ? "每天执行" : task.repeatDays == 7 ? "每周执行" : "每" + task.repeatDays + "天执行", false));
    }

    private void showTaskMenu(View anchor, TaskStore.Task task) {
        LinearLayout menu = column();
        menu.setPadding(dp(5), dp(5), dp(5), dp(5));
        menu.setBackground(shape(Color.WHITE, 16, Color.rgb(225, 214, 244)));
        PopupWindow popup = new PopupWindow(menu, dp(168), -2, true);
        popup.setBackgroundDrawable(shape(Color.WHITE, 16, Color.rgb(225, 214, 244)));
        popup.setElevation(dp(8));
        popup.setOutsideTouchable(true);

        LinearLayout editAction = row();
        editAction.setGravity(Gravity.CENTER_VERTICAL);
        editAction.setPadding(dp(12), 0, dp(10), 0);
        editAction.setBackground(shape(PALE, 12, 0));
        ImageView pencil = new ImageView(this);
        pencil.setImageResource(R.drawable.ic_pencil);
        pencil.setColorFilter(VIOLET);
        editAction.addView(pencil, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView editLabel = text(task.timed ? "编辑定时任务" : "编辑待办", 15, VIOLET, true);
        editLabel.setPadding(dp(12), 0, 0, 0);
        editAction.addView(editLabel);
        if (!task.timed || task.repeatDays > 0) {
            menu.addView(editAction, new LinearLayout.LayoutParams(-1, dp(48)));
            editAction.setOnClickListener(v -> { popup.dismiss(); edit(task); });
        }

        if (task.timed && task.repeatDays > 0) {
            TextView pause = text(task.paused ? "继续定时任务" : "暂停定时任务", 15, VIOLET, true);
            pause.setGravity(Gravity.CENTER_VERTICAL);
            pause.setPadding(dp(16), 0, 0, 0);
            menu.addView(pause, new LinearLayout.LayoutParams(-1, dp(48)));
            pause.setOnClickListener(v -> {
                popup.dismiss();
                task.paused = !task.paused;
                store.save(task);
                if (!task.paused) store.advanceTimed(task);
                ReminderScheduler.update(this, task);
                render();
            });
        }

        LinearLayout deleteAction = row();
        deleteAction.setGravity(Gravity.CENTER_VERTICAL);
        deleteAction.setPadding(dp(12), 0, dp(10), 0);
        ImageView bin = new ImageView(this);
        bin.setImageResource(R.drawable.ic_trash);
        bin.setColorFilter(Color.rgb(218, 102, 94));
        deleteAction.addView(bin, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView deleteLabel = text(task.timed ? "删除定时任务" : "删除待办", 15, Color.rgb(218, 102, 94), true);
        deleteLabel.setPadding(dp(12), 0, 0, 0);
        deleteAction.addView(deleteLabel);
        menu.addView(deleteAction, new LinearLayout.LayoutParams(-1, dp(48)));
        deleteAction.setOnClickListener(v -> { popup.dismiss(); confirmDelete(task); });

        popup.showAsDropDown(anchor, dp(40) - dp(168), -dp(2));
    }

    private void showChoiceMenu(View anchor, int width, String[] labels, int[] icons, Runnable[] actions) {
        LinearLayout menu = column();
        menu.setPadding(dp(5), dp(5), dp(5), dp(5));
        menu.setBackground(shape(Color.WHITE, 16, Color.rgb(225, 214, 244)));
        PopupWindow popup = new PopupWindow(menu, dp(width), -2, true);
        popup.setBackgroundDrawable(shape(Color.WHITE, 16, Color.rgb(225, 214, 244)));
        popup.setElevation(dp(8));
        popup.setOutsideTouchable(true);
        for (int i = 0; i < labels.length; i++) {
            int index = i;
            LinearLayout action = row();
            action.setGravity(Gravity.CENTER_VERTICAL);
            action.setPadding(dp(12), 0, dp(10), 0);
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setColorFilter(VIOLET);
            action.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
            TextView label = text(labels[i], 15, NAVY, false);
            label.setPadding(dp(12), 0, 0, 0);
            action.addView(label);
            menu.addView(action, new LinearLayout.LayoutParams(-1, dp(48)));
            action.setOnClickListener(v -> { popup.dismiss(); actions[index].run(); });
        }
        popup.showAsDropDown(anchor, anchor.getWidth() - dp(width), -dp(2));
    }

    private void cancelTaskAnimation() {
        taskTransitionToken++;
        if (taskAnimator != null) {
            taskAnimator.cancel();
            taskAnimator = null;
        }
    }

    private void animateTaskChange(TaskStore.Task task, LinearLayout card, CheckBox check,
                                   TextView title, GradientDrawable background, boolean completed) {
        cancelTaskAnimation();
        int token = taskTransitionToken;
        boolean leaving = searching ? !matchesSearchFilters(task)
                : filter.equals("未完成") ? completed : filter.equals("已完成") && !completed;
        if (!ValueAnimator.areAnimatorsEnabled()) { render(); return; }
        int height = card.getHeight();
        if (height == 0) { render(); return; }
        check.animate().cancel();
        check.setScaleX(.78f);
        check.setScaleY(.78f);
        check.animate().scaleX(1f).scaleY(1f).setDuration(180)
                .setInterpolator(new OvershootInterpolator(1.3f)).start();
        int fromBackground = completed ? Color.WHITE : Color.rgb(241, 251, 245);
        int toBackground = completed ? Color.rgb(241, 251, 245) : Color.WHITE;
        int fromText = title.getCurrentTextColor();
        int toText = completed ? MUTED : NAVY;
        ArgbEvaluator colors = new ArgbEvaluator();
        taskAnimator = ValueAnimator.ofFloat(0f, 1f);
        taskAnimator.setDuration(leaving ? 260 : 230);
        taskAnimator.setInterpolator(new DecelerateInterpolator());
        taskAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            background.setColor((int) colors.evaluate(progress, fromBackground, toBackground));
            title.setTextColor((int) colors.evaluate(progress, fromText, toText));
            if (leaving) {
                card.setAlpha(1f - progress);
                card.setScaleX(1f - .03f * progress);
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
                params.height = Math.max(1, Math.round(height * (1f - progress)));
                card.setLayoutParams(params);
            }
        });
        taskAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (token != taskTransitionToken) return;
                taskAnimator = null;
                render();
            }
        });
        taskAnimator.start();
    }

    private void addTrashCard(TaskStore.Task task) {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackground(shape(Color.WHITE, 16, Color.rgb(238, 230, 235)));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(10);
        list.addView(card, cardParams);

        LinearLayout top = row();
        TextView title = text(task.title, 17, NAVY, true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout restore = row();
        restore.setPadding(dp(10), 0, dp(10), 0);
        restore.setBackground(shape(Color.WHITE, 12, Color.rgb(185, 158, 241)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_restore);
        restore.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView label = text("恢复", 14, VIOLET, false);
        label.setPadding(dp(5), 0, 0, 0);
        restore.addView(label);
        LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(-2, dp(44));
        restoreParams.leftMargin = dp(10);
        top.addView(restore, restoreParams);
        restore.setOnClickListener(v -> {
            task.deleted = false;
            task.deletedAt = 0;
            if (task.timed && task.repeatDays > 0) {
                task.paused = false;
                store.save(task);
                store.advanceTimed(task);
            }
            task.reminderTimes.removeIf(time -> time <= System.currentTimeMillis());
            task.remindAt = task.reminderTimes.isEmpty() ? 0 : Collections.min(task.reminderTimes);
            store.save(task);
            ReminderScheduler.update(this, task);
            render();
            toast(task.timed ? "已恢复定时任务" : "已恢复待办");
        });
        card.addView(top);

        LinearLayout deleted = row();
        deleted.setPadding(0, dp(4), 0, 0);
        ImageView trashIcon = new ImageView(this);
        trashIcon.setImageResource(R.drawable.ic_trash);
        trashIcon.setColorFilter(MUTED);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(16), dp(16));
        iconParams.rightMargin = dp(7);
        deleted.addView(trashIcon, iconParams);
        deleted.addView(text("删除于 " + (task.deletedAt > 0 ? relativeDeleted(task.deletedAt) : "未知时间"), 13, MUTED, false));
        card.addView(deleted);
        TextView remove = text("彻底删除", 13, Color.rgb(218, 102, 94), false);
        remove.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        card.addView(remove, new LinearLayout.LayoutParams(-1, dp(44)));
        remove.setOnClickListener(v -> confirmDelete(task, true));
    }

    private String relativeDeleted(long timestamp) {
        Calendar day = Calendar.getInstance();
        Calendar value = Calendar.getInstance();
        value.setTimeInMillis(timestamp);
        if (day.get(Calendar.YEAR) == value.get(Calendar.YEAR) && day.get(Calendar.DAY_OF_YEAR) == value.get(Calendar.DAY_OF_YEAR))
            return "今天 " + timeOnly.format(new Date(timestamp));
        day.add(Calendar.DAY_OF_YEAR, -1);
        if (day.get(Calendar.YEAR) == value.get(Calendar.YEAR) && day.get(Calendar.DAY_OF_YEAR) == value.get(Calendar.DAY_OF_YEAR))
            return "昨天 " + timeOnly.format(new Date(timestamp));
        return dateTime.format(new Date(timestamp));
    }

    private String shortDue(long timestamp) {
        Calendar now = Calendar.getInstance();
        Calendar due = Calendar.getInstance();
        due.setTimeInMillis(timestamp);
        if (now.get(Calendar.YEAR) == due.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == due.get(Calendar.DAY_OF_YEAR))
            return "今天 " + timeOnly.format(new Date(timestamp));
        now.add(Calendar.DAY_OF_YEAR, 1);
        if (now.get(Calendar.YEAR) == due.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == due.get(Calendar.DAY_OF_YEAR))
            return "明天 " + timeOnly.format(new Date(timestamp));
        return dateTime.format(new Date(timestamp));
    }

    private LinearLayout compactMeta(String due, String reminder) {
        LinearLayout line = row();
        line.setPadding(dp(48), dp(3), 0, 0);
        ImageView calendar = new ImageView(this);
        calendar.setImageResource(R.drawable.ic_calendar);
        calendar.setColorFilter(MUTED);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(15), dp(15));
        iconParams.rightMargin = dp(4);
        line.addView(calendar, iconParams);
        TextView dueText = text(due, 12, MUTED, false);
        dueText.setSingleLine(true);
        dueText.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(dueText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView divider = text(" │ ", 12, MUTED, false);
        line.addView(divider);
        ImageView bell = new ImageView(this);
        bell.setImageResource(R.drawable.ic_bell);
        bell.setColorFilter(MUTED);
        LinearLayout.LayoutParams bellParams = new LinearLayout.LayoutParams(dp(15), dp(15));
        bellParams.rightMargin = dp(4);
        line.addView(bell, bellParams);
        TextView reminderText = text(reminder, 12, MUTED, false);
        reminderText.setSingleLine(true);
        reminderText.setEllipsize(TextUtils.TruncateAt.END);
        reminderText.setMaxWidth(dp(100));
        line.addView(reminderText);
        return line;
    }

    private String reminderLabel(TaskStore.Task task) {
        int count = task.reminderTimes.size() + (task.snoozeAt > System.currentTimeMillis() ? 1 : 0);
        if (count > 1) return count + "次提醒";
        if (task.snoozeAt > System.currentTimeMillis()) return "稍后 " + timeOnly.format(new Date(task.snoozeAt));
        long minutes = (task.dueAt - task.remindAt) / 60000;
        for (int i = 1; i < ADVANCE.length; i++) if (ADVANCE[i] == minutes) return "提前" + REMINDERS[i];
        return minutes > 0 ? "提前" + advanceLabel(minutes) : "提醒我";
    }

    private LinearLayout meta(int icon, String value, boolean noIndent) {
        LinearLayout line = row();
        line.setPadding(noIndent ? 0 : dp(48), dp(3), 0, 0);
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setColorFilter(MUTED);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(16), dp(16));
        iconParams.rightMargin = dp(7);
        line.addView(image, iconParams);
        line.addView(text(value, 12, MUTED, false));
        return line;
    }

    private void edit(TaskStore.Task original) {
        cancelTaskAnimation();
        filterTransitionToken++;
        if (filterColorAnimator != null) filterColorAnimator.cancel();
        if (list != null) list.animate().cancel();
        editing = true;
        TaskStore.Task task = original == null ? new TaskStore.Task() : original;
        if (original == null) task.timed = timedPage;
        else timedPage = task.timed;
        boolean timedForm = task.timed;
        int gutter = gutter();
        LinearLayout root = column();
        root.setBackgroundColor(BACKGROUND);
        setScreen(root, gutter);

        LinearLayout header = row();
        TextView back = text("‹", 34, NAVY, false);
        back.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(52)));
        back.setOnClickListener(v -> { editing = false; showHome(); });
        TextView heading = text(original == null ? timedForm ? "新建定时任务" : "新建待办"
                : timedForm ? "编辑定时任务" : "编辑待办", 22, NAVY, true);
        heading.setGravity(Gravity.CENTER);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(52), 1));
        header.addView(cloud(), new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout form = column();
        form.setPadding(0, 0, 0, dp(8));
        scroll.addView(form);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.topMargin = dp(10);
        root.addView(scroll, scrollParams);

        LinearLayout titleGroup = formGroup(form);
        titleGroup.addView(section("任务标题 *"));
        EditText title = input("请输入任务标题", task.title, true);
        title.setPadding(dp(14), dp(10), dp(48), dp(10));
        FrameLayout titleField = new FrameLayout(this);
        titleField.addView(title, new FrameLayout.LayoutParams(-1, dp(44)));
        ImageView clearTitle = new ImageView(this);
        clearTitle.setImageResource(R.drawable.ic_clear);
        clearTitle.setContentDescription("清除任务标题");
        clearTitle.setPadding(dp(12), dp(12), dp(12), dp(12));
        titleField.addView(clearTitle, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END));
        clearTitle.setVisibility(title.length() == 0 ? View.GONE : View.VISIBLE);
        clearTitle.setOnClickListener(v -> title.setText(""));
        title.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearTitle.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
            }
            public void afterTextChanged(Editable s) { }
        });
        titleGroup.addView(titleField);

        LinearLayout noteGroup = formGroup(form);
        noteGroup.addView(section("备注（可选）"));
        EditText note = input("添加任务备注，如具体要求、参考资料等…", task.note, false);
        note.setMinHeight(dp(64));
        note.setGravity(Gravity.TOP);
        noteGroup.addView(note, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout categoryGroup = formGroup(form);
        categoryGroup.addView(section("分类"));
        String[] chosenCategory = {task.category == null ? "日常" : task.category};
        LinearLayout categories = row();
        categoryGroup.addView(horizontal(categories));
        fillChoices(categories, CATEGORIES, chosenCategory[0], value -> {
            chosenCategory[0] = value;
        });

        LinearLayout priorityGroup = formGroup(form);
        priorityGroup.addView(section("优先级"));
        String[] chosenPriority = {task.priority == null ? "中" : task.priority};
        LinearLayout priorities = row();
        priorityGroup.addView(priorities);
        fillChoices(priorities, PRIORITIES, chosenPriority[0], value -> {
            chosenPriority[0] = value;
        });

        LinearLayout dueGroup = formGroup(form);
        LinearLayout dueHeading = row();
        dueHeading.addView(section(timedForm ? original == null ? "首次执行时间" : "下次执行时间" : "截止时间"), new LinearLayout.LayoutParams(0, -2, 1));
        TextView clear = text("清除", 12, BLUE, false);
        clear.setGravity(Gravity.CENTER);
        dueHeading.addView(clear, new LinearLayout.LayoutParams(dp(48), dp(28)));
        dueGroup.addView(dueHeading);
        long[] due = {task.dueAt};
        TextView date = selectable(due[0] == 0 ? "选择日期" : dateOnly.format(new Date(due[0])));
        TextView time = selectable(due[0] == 0 ? "选择时间" : timeOnly.format(new Date(due[0])));
        LinearLayout dateRow = row();
        dateRow.addView(iconLabel("日期", R.drawable.ic_calendar), new LinearLayout.LayoutParams(dp(76), -2));
        dateRow.addView(date, new LinearLayout.LayoutParams(0, dp(40), 1));
        dueGroup.addView(dateRow);
        LinearLayout timeRow = row();
        timeRow.addView(iconLabel("时间", R.drawable.ic_clock), new LinearLayout.LayoutParams(dp(76), -2));
        timeRow.addView(time, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-1, -2);
        timeParams.topMargin = dp(4);
        dueGroup.addView(timeRow, timeParams);

        int[] repeatDays = {task.repeatDays > 0 && task.repeatDays != 1 && task.repeatDays != 7 ? -1
                : task.repeatDays > 0 ? task.repeatDays : 1};
        EditText customRepeat = input("间隔天数", task.repeatDays > 0 && task.repeatDays != 1 && task.repeatDays != 7
                ? Integer.toString(task.repeatDays) : "", true);
        if (timedForm) {
            LinearLayout repeatGroup = formGroup(form);
            repeatGroup.addView(section("执行周期"));
            LinearLayout repeatChoices = row();
            repeatGroup.addView(horizontal(repeatChoices));
            customRepeat.setInputType(InputType.TYPE_CLASS_NUMBER);
            customRepeat.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3)});
            repeatGroup.addView(customRepeat, new LinearLayout.LayoutParams(-1, dp(44)));
            customRepeat.setVisibility(repeatDays[0] == -1 ? View.VISIBLE : View.GONE);
            String repeatSelected = repeatDays[0] == 1 ? "每天" : repeatDays[0] == 7 ? "每周" : "自定义";
            fillChoices(repeatChoices, new String[]{"每天", "每周", "自定义"}, repeatSelected, value -> {
                repeatDays[0] = value.equals("每天") ? 1 : value.equals("每周") ? 7 : -1;
                customRepeat.setVisibility(value.equals("自定义") ? View.VISIBLE : View.GONE);
            });
        }

        LinearLayout reminderGroup = formGroup(form);
        reminderGroup.addView(section(timedForm ? "执行前提醒" : "到期前提醒"));
        List<Long> advances = new ArrayList<>();
        for (long timeValue : task.reminderTimes) {
            long minutes = (task.dueAt - timeValue) / 60000;
            if (minutes > 0 && !advances.contains(minutes)) advances.add(minutes);
        }
        TextView reminder = selectable("＋ 添加提醒时间   ▾");
        reminder.setSingleLine(true);
        reminder.setEllipsize(TextUtils.TruncateAt.END);
        reminderGroup.addView(reminder, new LinearLayout.LayoutParams(-1, dp(40)));
        LinearLayout selectedReminders = column();
        reminderGroup.addView(selectedReminders);
        long[] snooze = {task.snoozeAt > System.currentTimeMillis() ? task.snoozeAt : 0};
        TextView snoozeChoice = text("", 13, VIOLET, false);
        snoozeChoice.setGravity(Gravity.CENTER_VERTICAL);
        snoozeChoice.setPadding(dp(12), 0, dp(12), 0);
        snoozeChoice.setBackground(shape(PALE, 12, 0));
        LinearLayout.LayoutParams snoozeParams = new LinearLayout.LayoutParams(-1, dp(36));
        snoozeParams.topMargin = dp(6);
        reminderGroup.addView(snoozeChoice, snoozeParams);
        snoozeChoice.setVisibility(snooze[0] > 0 ? View.VISIBLE : View.GONE);
        if (snooze[0] > 0) snoozeChoice.setText("稍后提醒 · " + dateTime.format(new Date(snooze[0])) + "    ×");
        TextView hint = text(timedForm ? "选择执行时间后设置提醒" : "选择截止时间后设置提醒", 13, MUTED, false);
        hint.setPadding(0, dp(4), 0, 0);
        reminderGroup.addView(hint);
        Runnable refreshHint = () -> {
            selectedReminders.removeAllViews();
            for (long minutes : new ArrayList<>(advances)) {
                TextView selected = text("提前" + advanceLabel(minutes) + "    ×", 13, VIOLET, false);
                selected.setGravity(Gravity.CENTER_VERTICAL);
                selected.setPadding(dp(12), 0, dp(12), 0);
                selected.setBackground(shape(PALE, 12, 0));
                LinearLayout.LayoutParams selectedParams = new LinearLayout.LayoutParams(-1, dp(36));
                selectedParams.topMargin = dp(6);
                selectedReminders.addView(selected, selectedParams);
                selected.setOnClickListener(view -> {
                    advances.remove(minutes);
                    selectedReminders.removeView(selected);
                    int count = advances.size() + (snooze[0] > 0 ? 1 : 0);
                    hint.setText(count == 0 ? "不发送提醒通知" : "已设置 " + count + " 次提醒，点击上方时间可移除");
                });
            }
            int count = advances.size() + (snooze[0] > 0 ? 1 : 0);
            hint.setText(count == 0 ? "不发送提醒通知" : due[0] == 0
                    ? timedForm ? "请先选择执行时间" : "请先选择截止时间"
                    : "已设置 " + count + " 次提醒，点击上方时间可移除");
        };
        snoozeChoice.setOnClickListener(v -> { snooze[0] = 0; snoozeChoice.setVisibility(View.GONE); refreshHint.run(); });
        reminder.setOnClickListener(v -> {
            String[] labels = {REMINDERS[1], REMINDERS[2], REMINDERS[3], REMINDERS[4], "自定义…"};
            int[] icons = {R.drawable.ic_bell, R.drawable.ic_bell, R.drawable.ic_bell,
                    R.drawable.ic_bell, R.drawable.ic_pencil};
            Runnable[] actions = new Runnable[labels.length];
            for (int i = 0; i < 4; i++) {
                long minutes = ADVANCE[i + 1];
                actions[i] = () -> {
                    if (!advances.contains(minutes)) advances.add(minutes);
                    refreshHint.run();
                };
            }
            actions[4] = () -> chooseCustomReminder(0, minutes -> {
                if (!advances.contains(minutes)) advances.add(minutes);
                refreshHint.run();
            });
            showChoiceMenu(reminder, 168, labels, icons, actions);
        });
        date.setOnClickListener(v -> {
            if (useWheelDatePicker()) pickDueDateTime(due, date, time, refreshHint);
            else pickSystemDate(due, date, time, refreshHint);
        });
        time.setOnClickListener(v -> {
            if (useWheelDatePicker()) pickDueDateTime(due, date, time, refreshHint);
            else pickSystemTime(due, time, refreshHint);
        });
        clear.setOnClickListener(v -> { due[0] = 0; date.setText("选择日期"); time.setText("选择时间"); refreshHint.run(); });
        refreshHint.run();

        TextView save = primary(timedForm ? "保存定时任务" : "保存待办");
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(56));
        saveParams.topMargin = dp(12);
        root.addView(save, saveParams);
        save.setOnClickListener(v -> {
            String value = title.getText().toString().trim();
            if (value.isEmpty()) { title.setError("请输入任务标题"); return; }
            if (due[0] > 0 && due[0] <= System.currentTimeMillis()) { toast(timedForm ? "首次执行时间需晚于当前时间" : "截止时间需晚于当前时间"); return; }
            int interval = timedForm ? repeatDays[0] : 0;
            if (interval == -1) {
                try { interval = Integer.parseInt(customRepeat.getText().toString()); }
                catch (NumberFormatException error) { customRepeat.setError("请输入间隔天数"); return; }
                if (interval < 1) { customRepeat.setError("间隔至少 1 天"); return; }
            }
            if (timedForm && due[0] == 0) { toast("请设置首次执行时间"); return; }
            if (!advances.isEmpty() && due[0] == 0) { toast(timedForm ? "请先选择执行时间" : "请先选择截止时间"); return; }
            List<Long> reminderTimes = new ArrayList<>();
            for (long minutes : advances) {
                if (timedForm && minutes >= (long) interval * 1440) {
                    toast("提醒提前量需短于执行周期");
                    return;
                }
                long timeValue = due[0] - minutes * 60000;
                if (timeValue <= System.currentTimeMillis()) { toast("提醒时间需晚于当前时间"); return; }
                reminderTimes.add(timeValue);
            }
            task.title = value;
            task.note = note.getText().toString().trim();
            task.category = chosenCategory[0];
            task.priority = chosenPriority[0];
            task.dueAt = due[0];
            task.repeatDays = interval;
            task.timed = timedForm;
            task.reminderTimes.clear();
            task.reminderTimes.addAll(reminderTimes);
            task.remindAt = reminderTimes.isEmpty() ? 0 : Collections.min(reminderTimes);
            task.snoozeAt = snooze[0] > 0 && snooze[0] < due[0] ? snooze[0] : 0;
            store.save(task);
            ReminderScheduler.update(this, task);
            editing = false;
            showHome();
            toast(timedForm ? "已保存定时任务" : "已保存待办");
            if (!reminderTimes.isEmpty()) askNotificationPermission();
        });
    }

    private String advanceLabel(long minutes) {
        long days = minutes / 1440;
        long hours = minutes % 1440 / 60;
        long rest = minutes % 60;
        String value = days > 0 ? days + "天" : "";
        if (hours > 0) value += hours + "小时";
        if (rest > 0) value += rest + "分钟";
        return value;
    }

    private void pickSystemDate(long[] due, TextView date, TextView time, Runnable refreshHint) {
        Calendar value = Calendar.getInstance();
        if (due[0] > 0) value.setTimeInMillis(due[0]);
        new DatePickerDialog(this, (picker, year, month, day) -> {
            value.set(year, month, day);
            if (due[0] == 0) {
                value.set(Calendar.HOUR_OF_DAY, 18);
                value.set(Calendar.MINUTE, 0);
            }
            value.set(Calendar.SECOND, 0);
            value.set(Calendar.MILLISECOND, 0);
            due[0] = value.getTimeInMillis();
            date.setText(dateOnly.format(new Date(due[0])));
            time.setText(timeOnly.format(new Date(due[0])));
            refreshHint.run();
        }, value.get(Calendar.YEAR), value.get(Calendar.MONTH), value.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickSystemTime(long[] due, TextView time, Runnable refreshHint) {
        if (due[0] == 0) { toast("请先选择截止日期"); return; }
        Calendar value = Calendar.getInstance();
        value.setTimeInMillis(due[0]);
        new TimePickerDialog(this, (picker, hour, minute) -> {
            value.set(Calendar.HOUR_OF_DAY, hour);
            value.set(Calendar.MINUTE, minute);
            due[0] = value.getTimeInMillis();
            time.setText(timeOnly.format(new Date(due[0])));
            refreshHint.run();
        }, value.get(Calendar.HOUR_OF_DAY), value.get(Calendar.MINUTE), true).show();
    }

    private interface AdvanceListener { void selected(long minutes); }

    private void chooseCustomReminder(long initial, AdvanceListener listener) {
        LinearLayout content = column();
        content.setPadding(dp(20), dp(18), dp(20), dp(18));
        content.setBackground(shape(Color.WHITE, 20, Color.rgb(238, 225, 245)));
        TextView heading = text("自定义提醒时间", 20, NAVY, true);
        heading.setGravity(Gravity.CENTER);
        content.addView(heading);
        TextView explanation = text("设置在截止时间前多久提醒", 13, MUTED, false);
        explanation.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams explanationParams = new LinearLayout.LayoutParams(-1, -2);
        explanationParams.topMargin = dp(6);
        explanationParams.bottomMargin = dp(16);
        content.addView(explanation, explanationParams);
        LinearLayout fields = row();
        content.addView(fields);
        EditText days = reminderNumber(fields, "天", initial / 1440, 4);
        EditText hours = reminderNumber(fields, "小时", initial % 1440 / 60, 2);
        EditText minutes = reminderNumber(fields, "分钟", initial % 60, 2);
        LinearLayout actions = row();
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionsParams.topMargin = dp(20);
        content.addView(actions, actionsParams);
        TextView cancel = text("取消", 15, VIOLET, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(shape(Color.WHITE, 14, Color.rgb(188, 160, 238)));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, -1, 1));
        TextView confirm = primary("确定");
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, -1, 1);
        confirmParams.leftMargin = dp(10);
        actions.addView(confirm, confirmParams);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(content).create();
        dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(32), -2);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            try {
                String dayText = days.getText().toString();
                String hourText = hours.getText().toString();
                String minuteText = minutes.getText().toString();
                long dayValue = dayText.isEmpty() ? 0 : Long.parseLong(dayText);
                long hourValue = hourText.isEmpty() ? 0 : Long.parseLong(hourText);
                long minuteValue = minuteText.isEmpty() ? 0 : Long.parseLong(minuteText);
                if (hourValue > 23) { hours.setError("小时需在 0–23 之间"); return; }
                if (minuteValue > 59) { minutes.setError("分钟需在 0–59 之间"); return; }
                long total = dayValue * 1440 + hourValue * 60 + minuteValue;
                if (total == 0) { minutes.setError("请设置大于 0 的提前量"); return; }
                listener.selected(total);
                dialog.dismiss();
            } catch (NumberFormatException error) {
                toast("请填写有效的天、小时和分钟");
            }
        });
    }

    private EditText reminderNumber(LinearLayout fields, String unit, long value, int maxLength) {
        LinearLayout part = column();
        LinearLayout.LayoutParams partParams = new LinearLayout.LayoutParams(0, -2, 1);
        partParams.rightMargin = dp(6);
        fields.addView(part, partParams);
        TextView label = text(unit, 13, NAVY, true);
        label.setGravity(Gravity.CENTER);
        part.addView(label);
        EditText input = input(unit, Long.toString(value), true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(maxLength)});
        input.setSelectAllOnFocus(true);
        input.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(48));
        inputParams.topMargin = dp(6);
        part.addView(input, inputParams);
        return input;
    }

    private void pickDueDateTime(long[] due, TextView date, TextView time, Runnable refreshHint) {
        Calendar first = Calendar.getInstance();
        first.set(Calendar.HOUR_OF_DAY, 0);
        first.set(Calendar.MINUTE, 0);
        first.set(Calendar.SECOND, 0);
        first.set(Calendar.MILLISECOND, 0);
        first.add(Calendar.DAY_OF_YEAR, -365);
        String[] dates = new String[1096];
        SimpleDateFormat pickerDate = new SimpleDateFormat("MM月dd日 EEE", Locale.CHINA);
        Calendar cursor = (Calendar) first.clone();
        for (int i = 0; i < dates.length; i++) {
            dates[i] = pickerDate.format(cursor.getTime());
            cursor.add(Calendar.DAY_OF_YEAR, 1);
        }
        String[] hours = new String[24];
        String[] minutes = new String[60];
        for (int i = 0; i < hours.length; i++) hours[i] = String.format(Locale.CHINA, "%02d", i);
        for (int i = 0; i < minutes.length; i++) minutes[i] = String.format(Locale.CHINA, "%02d", i);
        Calendar chosen = Calendar.getInstance();
        if (due[0] > 0) chosen.setTimeInMillis(due[0]);
        else { chosen.set(Calendar.HOUR_OF_DAY, 18); chosen.set(Calendar.MINUTE, 0); }
        Calendar chosenDay = (Calendar) chosen.clone();
        chosenDay.set(Calendar.HOUR_OF_DAY, 0);
        chosenDay.set(Calendar.MINUTE, 0);
        chosenDay.set(Calendar.SECOND, 0);
        chosenDay.set(Calendar.MILLISECOND, 0);
        int selectedDay = Math.max(0, Math.min(dates.length - 1,
                (int) ((chosenDay.getTimeInMillis() - first.getTimeInMillis()) / 86400000L)));

        LinearLayout content = column();
        content.setPadding(dp(18), dp(12), dp(18), dp(20));
        content.setBackground(shape(Color.WHITE, 22, Color.rgb(238, 225, 245)));
        FrameLayout heading = new FrameLayout(this);
        content.addView(heading, new LinearLayout.LayoutParams(-1, dp(50)));
        TextView title = text("选择日期和时间", 20, NAVY, true);
        title.setGravity(Gravity.CENTER);
        heading.addView(title, new FrameLayout.LayoutParams(-1, -1));
        TextView close = text("×", 24, VIOLET, false);
        close.setGravity(Gravity.CENTER);
        heading.addView(close, new FrameLayout.LayoutParams(dp(44), -1, Gravity.END));
        LinearLayout labels = row();
        TextView dayLabel = text("日期", 13, MUTED, true);
        dayLabel.setGravity(Gravity.CENTER);
        labels.addView(dayLabel, new LinearLayout.LayoutParams(0, dp(36), 1.25f));
        TextView hourLabel = text("小时", 13, MUTED, true);
        hourLabel.setGravity(Gravity.CENTER);
        labels.addView(hourLabel, new LinearLayout.LayoutParams(0, dp(36), .6f));
        TextView minuteLabel = text("分钟", 13, MUTED, true);
        minuteLabel.setGravity(Gravity.CENTER);
        labels.addView(minuteLabel, new LinearLayout.LayoutParams(0, dp(36), .6f));
        content.addView(labels);
        LinearLayout wheels = row();
        content.addView(wheels);
        NumberPicker dayWheel = new NumberPicker(this);
        dayWheel.setMinValue(0);
        dayWheel.setMaxValue(dates.length - 1);
        dayWheel.setDisplayedValues(dates);
        dayWheel.setValue(selectedDay);
        dayWheel.setWrapSelectorWheel(false);
        dayWheel.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        NumberPicker hourWheel = new NumberPicker(this);
        hourWheel.setMinValue(0);
        hourWheel.setMaxValue(hours.length - 1);
        hourWheel.setDisplayedValues(hours);
        hourWheel.setValue(chosen.get(Calendar.HOUR_OF_DAY));
        hourWheel.setWrapSelectorWheel(true);
        hourWheel.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        NumberPicker minuteWheel = new NumberPicker(this);
        minuteWheel.setMinValue(0);
        minuteWheel.setMaxValue(minutes.length - 1);
        minuteWheel.setDisplayedValues(minutes);
        minuteWheel.setValue(chosen.get(Calendar.MINUTE));
        minuteWheel.setWrapSelectorWheel(true);
        minuteWheel.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        if (Build.VERSION.SDK_INT >= 29) {
            dayWheel.setTextColor(NAVY);
            hourWheel.setTextColor(NAVY);
            minuteWheel.setTextColor(NAVY);
        }
        LinearLayout.LayoutParams wheelParams = new LinearLayout.LayoutParams(0, dp(180), 1.25f);
        wheels.addView(dayWheel, wheelParams);
        wheels.addView(hourWheel, new LinearLayout.LayoutParams(0, dp(180), .6f));
        wheels.addView(minuteWheel, new LinearLayout.LayoutParams(0, dp(180), .6f));
        TextView confirm = primary("确定");
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(-1, dp(50));
        confirmParams.topMargin = dp(16);
        content.addView(confirm, confirmParams);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(content).create();
        dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(32), -2);
        close.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            Calendar value = (Calendar) first.clone();
            value.add(Calendar.DAY_OF_YEAR, dayWheel.getValue());
            value.set(Calendar.HOUR_OF_DAY, hourWheel.getValue());
            value.set(Calendar.MINUTE, minuteWheel.getValue());
            due[0] = value.getTimeInMillis();
            date.setText(dateOnly.format(new Date(due[0])));
            time.setText(timeOnly.format(new Date(due[0])));
            refreshHint.run();
            dialog.dismiss();
        });
    }

    private interface ChoiceListener { void selected(String value); }

    private void fillChoices(LinearLayout holder, String[] values, String selected, ChoiceListener listener) {
        holder.removeAllViews();
        for (String value : values) {
            boolean category = values == CATEGORIES;
            boolean chosen = value.equals(selected);
            int accent = category ? BLUE : value.equals("高") ? Color.rgb(232, 105, 102)
                    : value.equals("中") ? Color.rgb(239, 168, 76) : Color.rgb(99, 177, 130);
            int fill = chosen ? category ? PALE : value.equals("高") ? Color.rgb(255, 231, 224)
                    : value.equals("中") ? Color.rgb(255, 243, 220) : Color.rgb(230, 248, 236)
                    : Color.rgb(253, 250, 254);
            LinearLayout chip = row();
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(7), 0, dp(7), 0);
            chip.setBackground(shape(fill, 14, chosen ? accent : Color.rgb(232, 222, 239)));
            ImageView icon = new ImageView(this);
            icon.setImageResource(category ? categoryIcon(value) : R.drawable.ic_flag);
            icon.setColorFilter(accent);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(14), dp(14));
            iconParams.rightMargin = dp(3);
            chip.addView(icon, iconParams);
            chip.addView(text(value, 12, NAVY, chosen));
            LinearLayout.LayoutParams cp = values == PRIORITIES
                    ? new LinearLayout.LayoutParams(0, dp(36), 1)
                    : new LinearLayout.LayoutParams(-2, dp(36));
            cp.rightMargin = dp(values == CATEGORIES ? 4 : 8);
            holder.addView(chip, cp);
            chip.setOnClickListener(v -> {
                fillChoices(holder, values, value, listener);
                if (listener != null) listener.selected(value);
            });
        }
    }

    private HorizontalScrollView horizontal(LinearLayout content) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(content);
        return scroll;
    }

    private int categoryIcon(String value) {
        if ("学习".equals(value)) return R.drawable.ic_study;
        if ("游戏".equals(value)) return R.drawable.ic_game;
        if ("工作".equals(value)) return R.drawable.ic_work;
        if ("日常".equals(value)) return R.drawable.ic_calendar;
        return R.drawable.ic_more;
    }

    private LinearLayout iconLabel(String value, int drawable) {
        LinearLayout label = row();
        ImageView icon = new ImageView(this);
        icon.setImageResource(drawable);
        icon.setColorFilter(BLUE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(16), dp(16));
        iconParams.rightMargin = dp(5);
        label.addView(icon, iconParams);
        label.addView(text(value, 13, NAVY, true));
        return label;
    }

    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private ImageView cloud() {
        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.xiuyi_cloud);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription("咻咿云朵");
        return image;
    }
    private void setScreen(LinearLayout root, int gutter) {
        homeHeading = null;
        root.setPadding(dp(gutter), dp(12), dp(gutter), dp(12));
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getInsets(WindowInsets.Type.statusBars()).top;
            int bottom = Math.max(insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                    insets.getInsets(WindowInsets.Type.ime()).bottom);
            topInset = top;
            root.setPadding(dp(gutter), dp(12) + top, dp(gutter), dp(12) + bottom);
            return insets;
        });
        surface = new FrameLayout(this);
        surface.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(surface);
    }
    private int dp(int value) { return (int) (getResources().getDisplayMetrics().density * value + .5f); }
    private int screenWidthDp() { return (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density); }
    private int gutter() { return screenWidthDp() >= 412 ? 20 : 16; }
    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(radius));
        if (stroke != 0) result.setStroke(dp(1), stroke);
        return result;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        if (bold) result.setTypeface(null, Typeface.BOLD);
        return result;
    }
    private LinearLayout badgeWithIcon(String value, int background, int color, int icon) {
        LinearLayout result = row();
        result.setPadding(dp(9), dp(4), dp(10), dp(4));
        result.setBackground(shape(background, 12, 0));
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setColorFilter(color);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(14), dp(14));
        iconParams.rightMargin = dp(4);
        result.addView(image, iconParams);
        result.addView(text(value, 12, color, false));
        return result;
    }
    private TextView primary(String value) {
        TextView result = text(value, 17, Color.WHITE, true);
        result.setGravity(Gravity.CENTER);
        result.setBackground(shape(BLUE, 16, 0));
        return result;
    }
    private LinearLayout section(String value) {
        LinearLayout result = row();
        result.setPadding(0, 0, 0, dp(5));
        ImageView icon = new ImageView(this);
        int image = value.startsWith("任务标题") ? R.drawable.ic_pencil
                : value.startsWith("备注") ? R.drawable.ic_note
                : value.equals("分类") ? R.drawable.ic_folder
                : value.equals("优先级") ? R.drawable.ic_flag
                : value.equals("截止时间") || value.equals("日期选择方式") ? R.drawable.ic_calendar : R.drawable.ic_bell;
        icon.setImageResource(image);
        icon.setColorFilter(value.equals("优先级") ? Color.rgb(232, 105, 102) : BLUE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(16), dp(16));
        iconParams.rightMargin = dp(6);
        result.addView(icon, iconParams);
        result.addView(text(value.endsWith(" *") ? value.substring(0, value.length() - 2) : value, 13, NAVY, true));
        if (value.endsWith(" *")) result.addView(text(" *", 13, Color.rgb(235, 101, 120), true));
        return result;
    }
    private LinearLayout formGroup(LinearLayout form) {
        LinearLayout group = column();
        group.setPadding(dp(10), dp(8), dp(10), dp(9));
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.WHITE, Color.rgb(255, 250, 254)});
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(242, 231, 245));
        group.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(7);
        form.addView(group, params);
        return group;
    }
    private EditText input(String hint, String value, boolean oneLine) {
        EditText result = new EditText(this);
        result.setHint(hint);
        result.setText(value);
        result.setTextSize(15);
        result.setTextColor(NAVY);
        result.setHintTextColor(MUTED);
        result.setSingleLine(oneLine);
        result.setPadding(dp(14), dp(10), dp(14), dp(10));
        result.setBackground(shape(Color.rgb(252, 249, 255), 12, Color.rgb(215, 202, 235)));
        result.setOnFocusChangeListener((view, focused) -> result.setBackground(shape(Color.rgb(252, 249, 255), 12,
                focused ? BLUE : Color.rgb(215, 202, 235))));
        return result;
    }
    private TextView selectable(String value) {
        TextView result = text(value, 13, NAVY, false);
        result.setGravity(Gravity.CENTER_VERTICAL);
        result.setPadding(dp(12), 0, dp(12), 0);
        result.setBackground(shape(BACKGROUND, 12, Color.rgb(215, 202, 235)));
        return result;
    }
    private void confirmDelete(TaskStore.Task task) { confirmDelete(task, false); }

    private void confirmDelete(TaskStore.Task task, boolean permanently) {
        LinearLayout content = column();
        content.setPadding(dp(20), dp(14), dp(20), dp(18));
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setBackground(shape(Color.WHITE, 22, Color.rgb(242, 226, 230)));
        FrameLayout iconRow = new FrameLayout(this);
        content.addView(iconRow, new LinearLayout.LayoutParams(-1, dp(62)));
        FrameLayout iconBubble = new FrameLayout(this);
        iconBubble.setBackground(shape(Color.rgb(255, 232, 224), 32, 0));
        iconRow.addView(iconBubble, new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_trash);
        icon.setColorFilter(Color.rgb(229, 105, 96));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER);
        iconBubble.addView(icon, iconParams);
        TextView close = text("×", 24, MUTED, false);
        close.setGravity(Gravity.CENTER);
        iconRow.addView(close, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END | Gravity.TOP));
        TextView title = text(permanently ? "彻底删除任务？" : task.timed ? "删除定时任务？" : "删除任务？", 20, NAVY, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(3), 0, dp(4));
        content.addView(title);
        TextView message = text(permanently ? "删除后无法恢复。" : "删除后将移入回收站，可在回收站中恢复。", 14, MUTED, false);
        message.setGravity(Gravity.CENTER);
        content.addView(message);
        LinearLayout actions = row();
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(46));
        actionsParams.topMargin = dp(22);
        content.addView(actions, actionsParams);
        TextView cancel = text("取消", 15, VIOLET, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(shape(Color.WHITE, 14, Color.rgb(188, 160, 238)));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, -1, 1));
        TextView delete = text(permanently ? "彻底删除" : "删除", 15, Color.WHITE, true);
        delete.setGravity(Gravity.CENTER);
        delete.setBackground(shape(Color.rgb(246, 133, 114), 14, 0));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, -1, 1);
        deleteParams.leftMargin = dp(10);
        actions.addView(delete, deleteParams);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(content).create();
        dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(32), -2);
        close.setOnClickListener(v -> dialog.dismiss());
        cancel.setOnClickListener(v -> dialog.dismiss());
        delete.setOnClickListener(v -> {
            dialog.dismiss();
            if (permanently) {
                ReminderScheduler.update(this, task);
                store.deletePermanently(task.id);
            } else {
                task.deleted = true;
                task.deletedAt = System.currentTimeMillis();
                store.save(task);
                ReminderScheduler.update(this, task);
            }
            render();
            toast(permanently ? "已彻底删除待办" : "已移入回收站");
        });
    }
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            askExactAlarmPermission();
            return;
        }
        LinearLayout content = column();
        content.setPadding(dp(22), dp(10), dp(22), dp(20));
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setBackground(shape(Color.WHITE, 22, Color.rgb(238, 225, 245)));
        FrameLayout artwork = new FrameLayout(this);
        content.addView(artwork, new LinearLayout.LayoutParams(-1, dp(128)));
        ImageView mascot = cloud();
        artwork.addView(mascot, new FrameLayout.LayoutParams(dp(112), dp(112), Gravity.CENTER));
        ImageView bell = new ImageView(this);
        bell.setImageResource(R.drawable.ic_bell);
        bell.setColorFilter(Color.rgb(238, 177, 65));
        FrameLayout.LayoutParams bellParams = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER);
        bellParams.leftMargin = dp(72);
        bellParams.topMargin = dp(50);
        artwork.addView(bell, bellParams);
        TextView close = text("×", 24, MUTED, false);
        close.setGravity(Gravity.CENTER);
        artwork.addView(close, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END | Gravity.TOP));
        TextView title = text("开启提醒通知", 21, NAVY, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(3), 0, dp(10));
        content.addView(title);
        TextView message = text("开启后，咻咿待办会在待办到期前发送提醒，帮你不错过重要的事情～", 14, MUTED, false);
        message.setGravity(Gravity.CENTER);
        content.addView(message);
        TextView enable = primary("开启通知");
        LinearLayout.LayoutParams enableParams = new LinearLayout.LayoutParams(-1, dp(50));
        enableParams.topMargin = dp(22);
        content.addView(enable, enableParams);
        TextView later = text("暂不开启", 16, VIOLET, true);
        later.setGravity(Gravity.CENTER);
        later.setBackground(shape(Color.WHITE, 16, Color.rgb(188, 160, 238)));
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(-1, dp(50));
        laterParams.topMargin = dp(8);
        content.addView(later, laterParams);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(content).create();
        dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(32), -2);
        close.setOnClickListener(v -> dialog.dismiss());
        later.setOnClickListener(v -> dialog.dismiss());
        enable.setOnClickListener(v -> {
            dialog.dismiss();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        });
    }
    private void askExactAlarmPermission() {
        if (!ReminderScheduler.preciseEnabled(this) || ReminderScheduler.canScheduleExactly(this) ||
                getSharedPreferences(SETTINGS_FILE, MODE_PRIVATE).getBoolean("exact_prompted", false)) return;
        getSharedPreferences(SETTINGS_FILE, MODE_PRIVATE).edit().putBoolean("exact_prompted", true).apply();
        LinearLayout content = column();
        content.setPadding(dp(22), dp(20), dp(22), dp(20));
        content.setBackground(shape(Color.WHITE, 22, Color.rgb(238, 225, 245)));
        TextView title = text("开启精准提醒", 21, NAVY, true);
        title.setGravity(Gravity.CENTER);
        content.addView(title);
        TextView message = text("允许设置闹钟和提醒后，待办通知会尽量按设定时间送达。未开启时仍会提醒，但系统可能延后。", 14, MUTED, false);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, -2);
        messageParams.topMargin = dp(12);
        content.addView(message, messageParams);
        TextView enable = primary("前往开启");
        LinearLayout.LayoutParams enableParams = new LinearLayout.LayoutParams(-1, dp(50));
        enableParams.topMargin = dp(20);
        content.addView(enable, enableParams);
        TextView later = text("稍后再说", 15, VIOLET, true);
        later.setGravity(Gravity.CENTER);
        content.addView(later, new LinearLayout.LayoutParams(-1, dp(48)));
        AlertDialog dialog = new AlertDialog.Builder(this).setView(content).create();
        dialog.show();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(32), -2);
        later.setOnClickListener(v -> dialog.dismiss());
        enable.setOnClickListener(v -> { dialog.dismiss(); openExactAlarmSettings(); });
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31) return;
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + getPackageName()));
        try { startActivity(intent); }
        catch (android.content.ActivityNotFoundException error) { toast("请在系统设置中开启闹钟和提醒权限"); }
    }
    private void toast(String message) {
        FrameLayout frame = homeHeading instanceof FrameLayout ? (FrameLayout) homeHeading : surface;
        if (frame == null) return;
        View previous = frame.findViewWithTag("message");
        if (previous != null) frame.removeView(previous);
        boolean success = message.startsWith("已");
        TextView notice = text(message, 14, NAVY, true);
        notice.setTag("message");
        notice.setGravity(Gravity.CENTER);
        notice.setSingleLine(true);
        notice.setEllipsize(TextUtils.TruncateAt.END);
        notice.setPadding(dp(14), 0, dp(14), 0);
        notice.setBackground(shape(success ? MINT : PALE, 16, 0));
        notice.setElevation(dp(8));
        boolean inHeading = frame == homeHeading;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, dp(inHeading ? 36 : 44),
                inHeading ? Gravity.END | Gravity.CENTER_VERTICAL : Gravity.TOP | Gravity.END);
        if (!inHeading) {
            params.rightMargin = dp(gutter());
            params.topMargin = topInset + dp(74);
        }
        notice.setMaxWidth(dp(180));
        frame.addView(notice, params);
        notice.setTranslationX(dp(250));
        notice.setAlpha(0f);
        notice.animate().translationX(0).alpha(1f).setDuration(220).start();
        notice.setOnClickListener(v -> frame.removeView(notice));
        notice.postDelayed(() -> { if (notice.getParent() == frame) frame.removeView(notice); }, 2500);
    }
}
