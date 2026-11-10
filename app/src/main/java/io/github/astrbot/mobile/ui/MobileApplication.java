package io.github.astrbot.mobile.ui;

public final class MobileApplication extends android.app.Application {
  @Override
  public void onCreate() {
    super.onCreate();
    CoreStore.initialize(this);
    Appearance.initialize(this);
  }
}
