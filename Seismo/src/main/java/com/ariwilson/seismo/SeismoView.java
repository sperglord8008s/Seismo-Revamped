package com.ariwilson.seismo;

import android.content.Context;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class SeismoView extends SurfaceView implements SurfaceHolder.Callback {
  public SeismoView(Context ctx, int period) {
    super(ctx);

    SurfaceHolder holder = getHolder();
    holder.addCallback(this);
    setKeepScreenOn(true);
    ctx_ = ctx;
    period_ = period;
  }

  public void surfaceChanged(SurfaceHolder holder, int format, int width,
                             int height) {
    if (view_thread_ != null) view_thread_.setSurfaceSize(width, height);
  }

  public void surfaceCreated(SurfaceHolder holder) {
    reader_ = new AccelerometerReader(ctx_);
    reader_.setPeriod(period_);
    view_thread_ = new SeismoViewThread(ctx_, holder, filter_, axis_, period_);
    view_thread_.setSensitivity(sensitivity_g_);
    view_thread_.setSpectrum(spectrum_);
    view_thread_.setAutoRange(auto_range_);
    reader_thread_ = new AccelerometerReaderThread(reader_, view_thread_,
                                                   paused_, period_);
    view_thread_.start();
    reader_thread_.start();
  }

  public void surfaceDestroyed(SurfaceHolder holder) {
    if (view_thread_ != null) view_thread_.setRunning(false);
    if (reader_thread_ != null) reader_thread_.setRunning(false);
    if (reader_ != null) reader_.close();
    try {
      if (view_thread_ != null) view_thread_.join();
      if (reader_thread_ != null) reader_thread_.join();
    } catch (InterruptedException e) {
      // Do nothing.
    }
  }

  public void pause() {
    paused_ = true;
    if (reader_ != null) reader_.close();
    if (view_thread_ != null) view_thread_.setPaused(true);
    if (reader_thread_ != null) reader_thread_.setPaused(true);
  }

  public void resume() {
    paused_ = false;
    if (reader_ != null) reader_.start();
    if (view_thread_ != null) view_thread_.setPaused(false);
    if (reader_thread_ != null) reader_thread_.setPaused(false);
  }

  public void filter() {
    filter_ = true;
    if (view_thread_ != null) view_thread_.setFilter(true);
  }

  public void unfilter() {
    filter_ = false;
    if (view_thread_ != null) view_thread_.setFilter(false);
  }
  
  public void x() {
    axis_ = 0;
    if (view_thread_ != null) view_thread_.setAxis(0);
  }

  public void y() {
    axis_ = 1;
    if (view_thread_ != null) view_thread_.setAxis(1);
  }

  public void z() { setAxis(2); }

  public void allAxes() { setAxis(3); }

  public void setAxis(int axis) {
    axis_ = Math.max(0, Math.min(3, axis));
    if (view_thread_ != null) view_thread_.setAxis(axis_);
  }

  public void setSpectrum(boolean spectrum) {
    spectrum_ = spectrum;
    if (view_thread_ != null) view_thread_.setSpectrum(spectrum);
  }

  public void setAutoRange(boolean enabled) {
    auto_range_ = enabled;
    if (view_thread_ != null) view_thread_.setAutoRange(enabled);
  }

  public void setPeriod(int period) {
    period_ = Math.max(5, Math.min(100, period));
    if (view_thread_ != null) view_thread_.setPeriod(period_);
    if (reader_thread_ != null) reader_thread_.setPeriod(period_);
    if (reader_ != null) reader_.setPeriod(period_);
  }

  public void save() {
    if (view_thread_ != null) view_thread_.save();
  }

  public void setSensitivity(float sensitivity_g) {
    sensitivity_g_ = Math.max(0.05f, Math.min(10f, sensitivity_g));
    if (view_thread_ != null) view_thread_.setSensitivity(sensitivity_g_);
  }

  private AccelerometerReaderThread reader_thread_;
  private AccelerometerReader reader_;
  private SeismoViewThread view_thread_;
  private boolean paused_ = false;
  private boolean filter_ = true;
  private boolean spectrum_;
  private boolean auto_range_;
  private int axis_ = 2;
  private Context ctx_;
  private int period_;
  private float sensitivity_g_ = 0.5f;
}
