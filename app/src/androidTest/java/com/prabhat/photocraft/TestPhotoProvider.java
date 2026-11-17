package com.prabhat.photocraft;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class TestPhotoProvider extends ContentProvider {
    private byte[] photoBytes;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        String[] columns = projection != null
                ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                row[i] = "instrumentation-sample.png";
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                row[i] = getPhotoBytes().length;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "image/png";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            final ParcelFileDescriptor reader = pipe[0];
            final ParcelFileDescriptor writer = pipe[1];
            new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                             new ParcelFileDescriptor.AutoCloseOutputStream(writer)) {
                    output.write(getPhotoBytes());
                } catch (IOException ignored) {
                    // The consumer may close the pipe before reading the full PNG.
                }
            }, "test-photo-provider").start();
            return reader;
        } catch (IOException error) {
            FileNotFoundException failure = new FileNotFoundException("Unable to open sample photo");
            failure.initCause(error);
            throw failure;
        }
    }

    private synchronized byte[] getPhotoBytes() {
        if (photoBytes == null) {
            Bitmap bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.rgb(40, 150, 210));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            bitmap.recycle();
            photoBytes = output.toByteArray();
        }
        return photoBytes;
    }
}
