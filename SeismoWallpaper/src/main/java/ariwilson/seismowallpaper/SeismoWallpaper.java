package com.ariwilson.seismowallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

/** Live wallpaper engine; sensor work runs only while the wallpaper is visible. */
public class SeismoWallpaper extends WallpaperService {
  public static final String PREFERENCES = "com.ariwilson.seismowallpaper";

  @Override public Engine onCreateEngine() { return new SeismoEngine(); }

  private final class SeismoEngine extends Engine implements
      SharedPreferences.OnSharedPreferenceChangeListener {
    SeismoEngine() {
      preferences_ = SeismoWallpaper.this.getSharedPreferences(PREFERENCES, 0);
      preferences_.registerOnSharedPreferenceChangeListener(this);
      ctx_ = getApplicationContext();
      readPreferences();
    }

    private void readPreferences() {
      filter_ = preferences_.getBoolean("filter", true);
      axis_ = parsePreference("axis", 3, 0, 3);
      sensitivity_g_ = parseFloatPreference("sensitivity_g", 0.5f, 0.05f, 10f);
      spectrum_ = preferences_.getBoolean("spectrum", false);
      auto_range_ = preferences_.getBoolean("auto_range", true);
      period_ = parsePreference("sample_period", 33, 5, 100);
      line_color_ = preferences_.getInt("line_color", Color.rgb(45, 212, 191));
      background_color_ = preferences_.getInt("background_color", Color.rgb(8, 13, 24));
    }

    private float parseFloatPreference(String key, float fallback, float min, float max) {
      try {
        return Math.max(min, Math.min(max,
            Float.parseFloat(preferences_.getString(key, Float.toString(fallback)))));
      } catch (NumberFormatException e) {
        return fallback;
      }
    }

    private int parsePreference(String key, int fallback, int min, int max) {
      try {
        return Math.max(min, Math.min(max,
            Integer.parseInt(preferences_.getString(key, Integer.toString(fallback)))));
      } catch (NumberFormatException e) {
        return fallback;
      }
    }

    @Override public void onVisibilityChanged(boolean visible) {
      super.onVisibilityChanged(visible);
      if (visible) startSampling(); else stopSampling();
    }

    private void startSampling() {
      if (view_thread_ != null) return;
      reader_ = new AccelerometerReader(ctx_);
      reader_.setPeriod(period_);
      view_thread_ = new SeismoViewThread(ctx_, getSurfaceHolder(), filter_, axis_,
                                          line_color_, background_color_, period_);
      view_thread_.setSensitivity(sensitivity_g_);
      view_thread_.setSpectrum(spectrum_);
      view_thread_.setAutoRange(auto_range_);
      view_thread_.setSurfaceSize(Math.max(1, canvas_width_), Math.max(1, canvas_height_));
      reader_thread_ = new AccelerometerReaderThread(reader_, view_thread_, false, period_);
      view_thread_.start();
      reader_thread_.start();
    }

    private void stopSampling() {
      if (view_thread_ == null) return;
      view_thread_.setRunning(false);
      reader_thread_.setRunning(false);
      reader_.close();
      try {
        view_thread_.join(500);
        reader_thread_.join(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      view_thread_ = null;
      reader_thread_ = null;
      reader_ = null;
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
      readPreferences();
      if (view_thread_ != null) {
        view_thread_.setFilter(filter_);
        view_thread_.setAxis(axis_);
        view_thread_.setSensitivity(sensitivity_g_);
        view_thread_.setSpectrum(spectrum_);
        view_thread_.setAutoRange(auto_range_);
        view_thread_.setLineColor(line_color_);
        view_thread_.setBackgroundColor(background_color_);
        view_thread_.setPeriod(period_);
      }
      if (reader_thread_ != null) reader_thread_.setPeriod(period_);
      if (reader_ != null) reader_.setPeriod(period_);
    }

    @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width,
                                          int height) {
      super.onSurfaceChanged(holder, format, width, height);
      canvas_width_ = width;
      canvas_height_ = height;
      if (view_thread_ != null) view_thread_.setSurfaceSize(width, height);
    }

    @Override public void onSurfaceCreated(SurfaceHolder holder) {
      super.onSurfaceCreated(holder);
      if (isVisible()) startSampling();
    }

    @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
      stopSampling();
      super.onSurfaceDestroyed(holder);
    }

    @Override public void onDestroy() {
      stopSampling();
      preferences_.unregisterOnSharedPreferenceChangeListener(this);
      super.onDestroy();
    }

    private final SharedPreferences preferences_;
    private AccelerometerReader reader_;
    private AccelerometerReaderThread reader_thread_;
    private SeismoViewThread view_thread_;
    private int canvas_height_ = 1;
    private int canvas_width_ = 1;
    private boolean filter_;
    private int axis_;
    private float sensitivity_g_;
    private boolean spectrum_;
    private boolean auto_range_;
    private int line_color_;
    private int background_color_;
    private final Context ctx_;
    private int period_;
  }
}
