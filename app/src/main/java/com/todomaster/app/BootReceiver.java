package com.todomaster.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        for (TaskStore.Task task : new TaskStore(context).all(false)) ReminderScheduler.update(context, task);
    }
}
