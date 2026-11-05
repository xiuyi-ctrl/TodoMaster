package com.todomaster.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

public final class ReminderSoundProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || uri.getPathSegments().size() != 2 ||
                !"sound".equals(uri.getPathSegments().get(0))) throw new FileNotFoundException();
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("[a-f0-9]{32}\\.(mp3|wav|ogg|m4a|aac)")) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(new File(new File(getContext().getFilesDir(), "reminder_sounds"), name),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        String path = uri.getLastPathSegment();
        if (path == null) return null;
        if (path.endsWith(".mp3")) return "audio/mpeg";
        if (path.endsWith(".wav")) return "audio/wav";
        if (path.endsWith(".ogg")) return "audio/ogg";
        if (path.endsWith(".m4a")) return "audio/mp4";
        if (path.endsWith(".aac")) return "audio/aac";
        return null;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
