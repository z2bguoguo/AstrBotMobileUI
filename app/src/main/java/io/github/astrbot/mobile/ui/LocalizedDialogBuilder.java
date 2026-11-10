package io.github.astrbot.mobile.ui;

import android.app.AlertDialog;
import android.content.*;

/** Translates UI copy only. The actual editor views keep user content verbatim. */
final class LocalizedDialogBuilder extends AlertDialog.Builder {
  LocalizedDialogBuilder(Context context) {
    super(context);
  }

  private CharSequence t(CharSequence s) {
    return s == null ? null : Appearance.text(s.toString());
  }

  private CharSequence[] items(CharSequence[] s) {
    CharSequence[] out = s.clone();
    for (int i = 0; i < out.length; i++) out[i] = t(out[i]);
    return out;
  }

  @Override
  public AlertDialog.Builder setTitle(CharSequence s) {
    return super.setTitle(t(s));
  }

  @Override
  public AlertDialog.Builder setMessage(CharSequence s) {
    return super.setMessage(t(s));
  }

  @Override
  public AlertDialog.Builder setPositiveButton(CharSequence s, DialogInterface.OnClickListener l) {
    return super.setPositiveButton(t(s), l);
  }

  @Override
  public AlertDialog.Builder setNegativeButton(CharSequence s, DialogInterface.OnClickListener l) {
    return super.setNegativeButton(t(s), l);
  }

  @Override
  public AlertDialog.Builder setNeutralButton(CharSequence s, DialogInterface.OnClickListener l) {
    return super.setNeutralButton(t(s), l);
  }

  @Override
  public AlertDialog.Builder setItems(CharSequence[] s, DialogInterface.OnClickListener l) {
    return super.setItems(items(s), l);
  }

  @Override
  public AlertDialog.Builder setSingleChoiceItems(
      CharSequence[] s, int checked, DialogInterface.OnClickListener l) {
    return super.setSingleChoiceItems(items(s), checked, l);
  }
}
