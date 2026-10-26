package com.ariwilson.seismo;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.SensorManager;
import android.view.SurfaceHolder;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.widget.Toast;

/** Live acceleration trace and a compact 128-point spectrum analyzer. */
public class SeismoViewThread extends Thread {
  public SeismoViewThread(Context ctx, SurfaceHolder holder, boolean filter, int axis, int period) {
    scale_paint_.setAntiAlias(true);
    line_paint_.setAntiAlias(true);
    line_paint_.setStrokeCap(Paint.Cap.ROUND);
    holder_ = holder;
    context_ = ctx;
    db_ = SeismoDbAdapter.getAdapter();
    setFilter(filter);
    setAxis(axis);
    setPeriod(period);
  }

  @Override public void run() {
    long lastRender = 0;
    while (running_) {
      accelerations_.clear();
      try {
        ArrayList<Float> sample = history_queue_.poll(period_, TimeUnit.MILLISECONDS);
        if (sample != null) accelerations_.add(sample);
      } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
      history_queue_.drainTo(accelerations_);
      synchronized (history_) {
        for (ArrayList<Float> sample : accelerations_) {
          history_.add(sample);
          while (!history_.isEmpty() && sample.get(0) - history_.get(0).get(0) > SECONDS_TO_SAVE * 1000) { history_.remove(0); start_ = Math.max(0, start_ - 1); }
          while (start_ < history_.size() && sample.get(0) - history_.get(start_).get(0) > SECONDS_TO_DISPLAY * 1000) start_++;
        }
        long now = System.currentTimeMillis();
        if (now - lastRender < 16) continue;
        lastRender = now;
        synchronized (holder_) {
          Canvas canvas = holder_.lockCanvas();
          if (canvas == null) continue;
          try {
            canvas.drawARGB(255, 8, 13, 24);
            if (spectrum_) drawSpectrum(canvas); else drawWaveform(canvas);
          } finally { holder_.unlockCanvasAndPost(canvas); }
        }
      }
    }
  }

  private void drawWaveform(Canvas canvas) {
    int gridColor = 0xff3b4a60;
    scale_paint_.setColor(gridColor);
    scale_paint_.setStrokeWidth(Math.max(1f, canvas_width_ / 900f));
    float textSize = Math.max(10f, canvas_width_ / 38f);
    scale_paint_.setTextSize(textSize);
    for (int i = 0; i <= 10; i++) { float y = canvas_height_ * i / 10f; canvas.drawLine(0, y, canvas_width_, y, scale_paint_); }
    float range = Math.max(0.05f, sensitivity_g_);
    float scaleMin = -range * SensorManager.GRAVITY_EARTH;
    float scaleMax = range * SensorManager.GRAVITY_EARTH;
    float endTime = history_.isEmpty() ? -1 : history_.get(history_.size() - 1).get(0) / 1000f;
    float startTime = endTime - SECONDS_TO_DISPLAY;
    if (autoRange_ && !history_.isEmpty()) {
      scaleMin = Float.POSITIVE_INFINITY;
      scaleMax = Float.NEGATIVE_INFINITY;
      for (int i = Math.max(0, start_); i < history_.size(); i++) {
        float value = selectedValue(history_.get(i));
        scaleMin = Math.min(scaleMin, value);
        scaleMax = Math.max(scaleMax, value);
      }
      float padding = Math.max(SensorManager.GRAVITY_EARTH * 0.05f, (scaleMax - scaleMin) * 0.12f);
      scaleMin -= padding;
      scaleMax += padding;
    }
    float span = Math.max(0.01f, scaleMax - scaleMin);
    scale_paint_.setColor(0xffcbd5e1);
    scale_paint_.setTextAlign(Paint.Align.CENTER);
    for (int i = 0; i <= 10; i += 2) {
      float x = canvas_width_ * i / 10f;
      float g = (scaleMin + span * i / 10f) / SensorManager.GRAVITY_EARTH;
      canvas.drawLine(x, 0, x, canvas_height_, scale_paint_);
      canvas.drawText(String.format(java.util.Locale.US, "%.2fg", g), x, canvas_height_ - textSize * 0.35f, scale_paint_);
    }
    if (endTime >= 0) {
      scale_paint_.setTextAlign(Paint.Align.LEFT);
      for (int s = (int)Math.floor(endTime); s >= Math.max((int)Math.floor(startTime), 0); s--) { float y = canvas_height_ * (s - startTime) / SECONDS_TO_DISPLAY; canvas.drawLine(0, y, canvas_width_ / 20f, y, scale_paint_); canvas.drawText(s + "s", canvas_width_ / 20f + textSize * .2f, y + textSize * .5f, scale_paint_); }
    }
    int segments = Math.max(0, history_.size() - start_ - 1);
    if (pts_ == null || pts_.length < segments * 4) pts_ = new float[segments * 4];
    for (int i = start_ + 1; i < history_.size(); i++) {
      ArrayList<Float> a = history_.get(i - 1), b = history_.get(i); int j = (i - start_ - 1) * 4;
      float av = selectedValue(a), bv = selectedValue(b);
      pts_[j] = canvas_width_ * (av - scaleMin) / span; pts_[j + 1] = canvas_height_ * (a.get(0) / 1000f - startTime) / SECONDS_TO_DISPLAY;
      pts_[j + 2] = canvas_width_ * (bv - scaleMin) / span; pts_[j + 3] = canvas_height_ * (b.get(0) / 1000f - startTime) / SECONDS_TO_DISPLAY;
    }
    line_paint_.setStrokeWidth(Math.max(2f, canvas_width_ / 360f));
    canvas.drawLines(pts_, 0, segments * 4, line_paint_);
  }

  private static final int FFT_SIZE = 128;

  private void drawSpectrum(Canvas canvas) {
    float left = Math.max(42f, canvas_width_ * 0.12f);
    float right = canvas_width_ - Math.max(12f, canvas_width_ * 0.04f);
    float top = Math.max(18f, canvas_height_ * 0.06f);
    float bottom = canvas_height_ - Math.max(30f, canvas_height_ * 0.14f);
    float width = Math.max(1f, right - left);
    float height = Math.max(1f, bottom - top);
    int n = FFT_SIZE;
    scale_paint_.setTextSize(Math.max(10f, canvas_width_ / 42f));
    scale_paint_.setStrokeWidth(1f);
    scale_paint_.setTextAlign(Paint.Align.CENTER);
    int count = history_.size();
    if (count < n) {
      scale_paint_.setColor(0xff94a3b8);
      canvas.drawText("Collecting samples for FFT…", canvas_width_ / 2f, canvas_height_ / 2f, scale_paint_);
      return;
    }
    double[] real = new double[n];
    double[] imag = new double[n];
    double mean = 0;
    int first = count - n;
    for (int i = 0; i < n; i++) mean += selectedValue(history_.get(first + i));
    mean /= n;
    double windowSum = 0;
    for (int i = 0; i < n; i++) {
      double window = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (n - 1));
      real[i] = (selectedValue(history_.get(first + i)) - mean) * window;
      windowSum += window;
    }
    fft(real, imag);
    int bins = n / 2;
    float maxHz = 500f / Math.max(5, period_);
    float maxG = Math.max(0.05f, sensitivity_g_);
    float maxAmplitude = maxG * SensorManager.GRAVITY_EARTH;
    scale_paint_.setColor(0xff253247);
    for (int i = 0; i <= 4; i++) {
      float y = top + height * i / 4f;
      canvas.drawLine(left, y, right, y, scale_paint_);
      float hz = maxHz * i / 4f;
      canvas.drawText(String.format(java.util.Locale.US, "%.1f", hz), left + width * i / 4f, bottom + scale_paint_.getTextSize() + 5, scale_paint_);
    }
    for (int i = 0; i <= 4; i++) {
      float y = bottom - height * i / 4f;
      float g = maxG * i / 4f;
      scale_paint_.setTextAlign(Paint.Align.RIGHT);
      canvas.drawText(String.format(java.util.Locale.US, "%.2fg", g), left - 5, y + scale_paint_.getTextSize() * 0.35f, scale_paint_);
      scale_paint_.setTextAlign(Paint.Align.CENTER);
    }
    scale_paint_.setColor(0xff94a3b8);
    canvas.drawText("Frequency (Hz)", left + width / 2f, canvas_height_ - 4, scale_paint_);
    if (pts_ == null || pts_.length < (bins - 1) * 4) pts_ = new float[(bins - 1) * 4];
    float[] points = pts_;
    for (int k = 1; k < bins; k++) {
      double amp = Math.sqrt(real[k] * real[k] + imag[k] * imag[k]) * 2.0 / Math.max(1.0, windowSum);
      float x = left + width * k / bins;
      float y = bottom - height * Math.min(1f, (float) (amp / maxAmplitude));
      int j = (k - 1) * 4;
      points[j] = x; points[j + 1] = bottom; points[j + 2] = x; points[j + 3] = y;
    }
    line_paint_.setStrokeWidth(Math.max(2f, canvas_width_ / 420f));
    canvas.drawLines(points, 0, Math.max(0, (bins - 1) * 4), line_paint_);
  }

  private float selectedValue(ArrayList<Float> sample) {
    if (axis_ == 3) {
      float x = sample.get(1), y = sample.get(2), z = sample.get(3);
      return (float) Math.sqrt(x * x + y * y + z * z);
    }
    return sample.get(axis_ + 1);
  }

  private static void fft(double[] real, double[] imag) {
    int n = real.length;
    for (int i = 1, j = 0; i < n; i++) {
      int bit = n >> 1;
      for (; (j & bit) != 0; bit >>= 1) j ^= bit;
      j ^= bit;
      if (i < j) { double tr = real[i]; real[i] = real[j]; real[j] = tr; double ti = imag[i]; imag[i] = imag[j]; imag[j] = ti; }
    }
    for (int len = 2; len <= n; len <<= 1) {
      double angle = -2.0 * Math.PI / len;
      double wr0 = Math.cos(angle), wi0 = Math.sin(angle);
      for (int start = 0; start < n; start += len) {
        double wr = 1, wi = 0;
        for (int j = 0; j < len / 2; j++) {
          int even = start + j, odd = even + len / 2;
          double tr = wr * real[odd] - wi * imag[odd];
          double ti = wr * imag[odd] + wi * real[odd];
          real[odd] = real[even] - tr; imag[odd] = imag[even] - ti;
          real[even] += tr; imag[even] += ti;
          double nextWr = wr * wr0 - wi * wi0; wi = wr * wi0 + wi * wr0; wr = nextWr;
        }
      }
    }
  }


  public void update(float x, float y, float z) {
    ArrayList<Float> sample = new ArrayList<Float>(4);
    sample.add((float)(new Date().getTime() - start_time_));
    if (filter_) {
      filter_acceleration_[0] = x * FILTERING_FACTOR + filter_acceleration_[0] * (1 - FILTERING_FACTOR); sample.add(x - filter_acceleration_[0]);
      filter_acceleration_[1] = y * FILTERING_FACTOR + filter_acceleration_[1] * (1 - FILTERING_FACTOR); sample.add(y - filter_acceleration_[1]);
      filter_acceleration_[2] = z * FILTERING_FACTOR + filter_acceleration_[2] * (1 - FILTERING_FACTOR); sample.add(z - filter_acceleration_[2]);
    } else { sample.add(x); sample.add(y); sample.add(z); }
    history_queue_.offer(sample);
  }

  public void setSurfaceSize(int width, int height) { synchronized (holder_) { canvas_width_ = Math.max(1, width); canvas_height_ = Math.max(1, height); start_ = 0; pts_ = null; } }
  public void setRunning(boolean value) { running_ = value; if (!value) interrupt(); }
  public void setPaused(boolean value) { if (value) start_paused_time_ = System.currentTimeMillis(); else synchronized (history_) { start_time_ += System.currentTimeMillis() - start_paused_time_; } }
  public void setFilter(boolean value) { filter_ = value; }
  public void setAxis(int value) { axis_ = Math.max(0, Math.min(3, value)); }
  public void setSpectrum(boolean value) { spectrum_ = value; }
  public void setAutoRange(boolean value) { autoRange_ = value; }
  public void setSensitivity(float value) { sensitivity_g_ = Math.max(0.05f, Math.min(10f, value)); }
  public void setPeriod(int value) { period_ = Math.max(5, Math.min(100, value)); }
  public void setLineColor(int value) { line_color_ = value; }
  public void setBackgroundColor(int value) { background_color_ = value; }

  public void save() {
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    String name = format.format(new Date());
    db_.open(context_);
    synchronized (history_) {
      if (db_.createGraph(name, history_) >= 0) {
        Toast.makeText(context_, "Saved graph as " + name + ".", Toast.LENGTH_LONG).show();
      } else {
        Toast.makeText(context_, "Failed to save graph. Please try again.", Toast.LENGTH_LONG).show();
      }
    }
    db_.close();
  }

  private static final float FILTERING_FACTOR = 0.1f;
  private static final int SECONDS_TO_SAVE = 60;
  private static final int SECONDS_TO_DISPLAY = 10;
  private final LinkedList<ArrayList<Float>> accelerations_ = new LinkedList<ArrayList<Float>>();
  private final ArrayList<ArrayList<Float>> history_ = new ArrayList<ArrayList<Float>>();
  private final ArrayBlockingQueue<ArrayList<Float>> history_queue_ = new ArrayBlockingQueue<ArrayList<Float>>(64);
  private final Paint scale_paint_ = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint line_paint_ = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float[] pts_;
  private final float[] filter_acceleration_ = new float[3];
  private long start_time_ = System.currentTimeMillis();
  private long start_paused_time_ = start_time_;
  private volatile int canvas_height_ = 1, canvas_width_ = 1;
  private volatile boolean running_ = true, filter_, spectrum_, autoRange_;
  private volatile int axis_;
  private volatile float sensitivity_g_ = 3f;
  private volatile int period_ = 20;
  private int start_ = 0, line_color_ = 0xff2dd4bf, background_color_ = 0xff080d18;
  private final SurfaceHolder holder_;
  private final Context context_;
  private SeismoDbAdapter db_;
}
