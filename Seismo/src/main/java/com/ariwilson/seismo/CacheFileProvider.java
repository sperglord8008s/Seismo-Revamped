package com.ariwilson.seismo;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Shares generated CSV files from the app cache without exposing file:// URIs. */
public final class CacheFileProvider extends ContentProvider {
  public static Uri uriFor(Context context, File file) {
    return new Uri.Builder()
        .scheme("content")
        .authority(context.getPackageName() + ".files")
        .appendPath(file.getName())
        .build();
  }

  @Override public boolean onCreate() { return true; }

  @Override public String getType(Uri uri) { return "text/csv"; }

  @Override public Cursor query(Uri uri, String[] projection, String selection,
                                String[] selectionArgs, String sortOrder) {
    File file = resolve(uri);
    String[] columns = projection == null
        ? new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE }
        : projection;
    MatrixCursor cursor = new MatrixCursor(columns);
    Object[] row = new Object[columns.length];
    for (int i = 0; i < columns.length; i++) {
      if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
      else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
    }
    cursor.addRow(row);
    return cursor;
  }

  @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
      throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
    return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  private File resolve(Uri uri) {
    Context context = getContext();
    File root = new File(context.getCacheDir(), "shared");
    File file = new File(root, uri.getLastPathSegment());
    try {
      if (!file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) {
        throw new IllegalArgumentException("Invalid shared file");
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid shared file", e);
    }
    return file;
  }

  @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
  @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
  @Override public int update(Uri uri, ContentValues values, String selection,
                              String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
