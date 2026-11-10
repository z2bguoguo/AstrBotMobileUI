package io.github.astrbot.mobile.ui;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Only the verified update APK can be shared, with a temporary read grant. */
public final class UpdateProvider extends ContentProvider {
  public static File apk(Context c) {
    return new File(c.getCacheDir(), "verified-update.apk");
  }

  private void check(Uri uri) {
    if (!(getContext().getPackageName() + ".updates").equals(uri.getAuthority())
        || !"/update.apk".equals(uri.getPath())) throw new SecurityException("Unknown update");
  }

  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public String getType(Uri uri) {
    check(uri);
    return "application/vnd.android.package-archive";
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    check(uri);
    if (!"r".equals(mode)) throw new SecurityException("Read only");
    return ParcelFileDescriptor.open(apk(getContext()), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
    check(uri);
    String[] columns =
        projection == null
            ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
            : projection;
    MatrixCursor cursor = new MatrixCursor(columns);
    Object[] values = new Object[columns.length];
    for (int i = 0; i < columns.length; i++) {
      if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = "AstrBotMobileUI-update.apk";
      if (OpenableColumns.SIZE.equals(columns[i])) values[i] = apk(getContext()).length();
    }
    cursor.addRow(values);
    return cursor;
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri uri, ContentValues values, String s, String[] a) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri uri, String s, String[] a) {
    throw new UnsupportedOperationException();
  }
}
