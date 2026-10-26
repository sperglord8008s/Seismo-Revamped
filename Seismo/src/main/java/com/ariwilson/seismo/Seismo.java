package com.ariwilson.seismo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Standalone accelerometer viewer with time and frequency domain controls. */
public class Seismo extends Activity {
  private static final int INK = Color.rgb(226, 232, 240);
  private static final int PANEL = Color.rgb(15, 23, 42);
  private static final int ACCENT = Color.rgb(45, 212, 191);
  private static final float[] RANGES_G = {0.1f, 0.25f, 0.5f, 1f, 2f, 3f, 5f, 10f};
  private static final int[] PERIODS_MS = {5, 10, 15, 20, 33, 50, 100};
  private final TextView[] axisButtons = new TextView[4];
  private TextView spectrumButton;
  private TextView autoFitButton;
  private TextView gravityButton;
  private SeismoView seismoView;
  private boolean paused;
  private boolean filterEnabled = true;
  private boolean spectrum;
  private boolean autoRange;
  private int axis = 3;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
    axis = prefs.getInt("axis", 3);
    spectrum = prefs.getBoolean("spectrum", false);
    filterEnabled = prefs.getBoolean("gravity_removal", true);
    autoRange = prefs.getBoolean("auto_range", true);
    int rangeIndex = Math.max(0, Math.min(RANGES_G.length - 1, prefs.getInt("range_index", 2)));
    int periodIndex = Math.max(0, Math.min(PERIODS_MS.length - 1, prefs.getInt("period_index", 2)));
    LinearLayout page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setBackgroundColor(Color.rgb(8, 13, 24));
    page.setOnApplyWindowInsetsListener((view, insets) -> {
      view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
          insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
      return insets.consumeSystemWindowInsets();
    });

    LinearLayout header = new LinearLayout(this);
    header.setOrientation(LinearLayout.VERTICAL);
    header.setPadding(dp(20), dp(15), dp(20), dp(12));
    header.setBackgroundColor(PANEL);
    TextView title = new TextView(this);
    title.setText("SEISMO");
    title.setTextColor(INK);
    title.setTextSize(18);
    title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    TextView subtitle = new TextView(this);
    subtitle.setText("LIVE ACCELERATION  ·  m/s²");
    subtitle.setTextColor(0xff94a3b8);
    subtitle.setTextSize(11);
    header.addView(title);
    header.addView(subtitle);
    page.addView(header);

    seismoView = new SeismoView(this, PERIODS_MS[periodIndex]);
    seismoView.setSensitivity(RANGES_G[rangeIndex]);
    seismoView.setAxis(axis);
    seismoView.setSpectrum(spectrum);
    seismoView.setAutoRange(autoRange);
    if (!filterEnabled) seismoView.unfilter();
    page.addView(seismoView, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

    LinearLayout rangeRow = row();
    TextView rangeLabel = label("Zoom ±" + formatRange(RANGES_G[rangeIndex]) + " g", 106);
    LinearLayout.LayoutParams rangeLabelParams = new LinearLayout.LayoutParams(dp(106), dp(42));
    rangeRow.addView(rangeLabel, rangeLabelParams);
    SeekBar range = slider(RANGES_G.length - 1, rangeIndex);
    rangeRow.addView(range, new LinearLayout.LayoutParams(0, dp(42), 1));
    range.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
        float value = RANGES_G[progress];
        rangeLabel.setText("Zoom ±" + formatRange(value) + " g");
        seismoView.setSensitivity(value);
        prefs.edit().putInt("range_index", progress).apply();
      }
      @Override public void onStartTrackingTouch(SeekBar bar) {}
      @Override public void onStopTrackingTouch(SeekBar bar) {}
    });
    page.addView(rangeRow);

    LinearLayout rateRow = row();
    TextView rateLabel = label(rateText(PERIODS_MS[periodIndex]), 106);
    LinearLayout.LayoutParams rateLabelParams = new LinearLayout.LayoutParams(dp(106), dp(42));
    rateRow.addView(rateLabel, rateLabelParams);
    SeekBar rate = slider(PERIODS_MS.length - 1, periodIndex);
    rateRow.addView(rate, new LinearLayout.LayoutParams(0, dp(42), 1));
    rate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
        int period = PERIODS_MS[progress];
        rateLabel.setText(rateText(period));
        seismoView.setPeriod(period);
        prefs.edit().putInt("period_index", progress).apply();
      }
      @Override public void onStartTrackingTouch(SeekBar bar) {}
      @Override public void onStopTrackingTouch(SeekBar bar) {}
    });
    page.addView(rateRow);

    LinearLayout axes = row();
    addAxisButton(axes, "X", 0);
    addAxisButton(axes, "Y", 1);
    addAxisButton(axes, "Z", 2);
    addAxisButton(axes, "Vector", 3);
    page.addView(axes);

    LinearLayout actions = row();
    spectrumButton = addButton(actions, spectrum ? "FFT spectrum" : "Time trace", v -> {
      spectrum = !spectrum;
      seismoView.setSpectrum(spectrum);
    seismoView.setAutoRange(autoRange);
    if (!filterEnabled) seismoView.unfilter();
      prefs.edit().putBoolean("spectrum", spectrum).apply();
      ((TextView) v).setText(spectrum ? "FFT spectrum" : "Time trace");
      updateSpectrumButton();
    });
    gravityButton = addButton(actions, gravityLabel(), v -> {
      filterEnabled = !filterEnabled;
      if (filterEnabled) seismoView.filter(); else seismoView.unfilter();
      prefs.edit().putBoolean("gravity_removal", filterEnabled).apply();
      ((TextView) v).setText(gravityLabel());
    });
    autoFitButton = addButton(actions, autoRange ? "Auto fit on" : "Auto fit off", v -> {
      autoRange = !autoRange;
      seismoView.setAutoRange(autoRange);
      prefs.edit().putBoolean("auto_range", autoRange).apply();
      ((TextView) v).setText(autoRange ? "Auto fit on" : "Auto fit off");
      ((TextView) v).setTextColor(autoRange ? PANEL : INK);
      ((TextView) v).setBackground(makePill(autoRange ? ACCENT : 0xff1e293b));
    });
    addButton(actions, "Pause", v -> {
      paused = !paused;
      if (paused) seismoView.pause(); else seismoView.resume();
      ((TextView) v).setText(paused ? "Resume" : "Pause");
    });
    page.addView(actions);

    LinearLayout storage = row();
    addButton(storage, "Save", v -> seismoView.save());
    addButton(storage, "Export", v -> startActivity(new Intent(this, Export.class)));
    page.addView(storage);
    setContentView(page);
    updateAxisButtons();
    updateSpectrumButton();
  }

  @Override protected void onPause() {
    if (seismoView != null) seismoView.pause();
    super.onPause();
  }

  @Override protected void onResume() {
    super.onResume();
    if (seismoView != null && !paused) seismoView.resume();
  }

  private void addAxisButton(LinearLayout row, String title, int value) {
    axisButtons[value] = addButton(row, title, v -> {
      axis = value;
      seismoView.setAxis(axis);
      getPreferences(MODE_PRIVATE).edit().putInt("axis", axis).apply();
      updateAxisButtons();
    });
  }

  private void updateAxisButtons() {
    for (int i = 0; i < axisButtons.length; i++) {
      axisButtons[i].setTextColor(i == axis ? PANEL : INK);
      axisButtons[i].setBackground(makePill(i == axis ? ACCENT : 0xff1e293b));
    }
  }

  private void updateSpectrumButton() {
    spectrumButton.setTextColor(spectrum ? PANEL : INK);
    spectrumButton.setBackground(makePill(spectrum ? ACCENT : 0xff1e293b));
  }

  private String formatRange(float value) {
    if (value < 1f) return value == 0.1f ? "0.10" : String.format(java.util.Locale.US, "%.2f", value);
    return value == (int)value ? Integer.toString((int)value) : Float.toString(value);
  }

  private String rateText(int period) {
    return "Rate " + Math.round(1000f / period) + " Hz";
  }

  private TextView label(String text, int width) {
    TextView view = new TextView(this);
    view.setText(text);
    view.setTextColor(INK);
    view.setTextSize(12);
    view.setGravity(Gravity.CENTER_VERTICAL);
    return view;
  }

  private SeekBar slider(int max, int progress) {
    SeekBar seek = new SeekBar(this);
    seek.setMax(max);
    seek.setProgress(progress);
    seek.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
    seek.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
    return seek;
  }

  private LinearLayout row() {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(12), dp(3), dp(12), dp(3));
    row.setBackgroundColor(PANEL);
    return row;
  }

  private TextView addButton(LinearLayout row, String text, View.OnClickListener listener) {
    TextView button = new TextView(this);
    button.setText(text);
    button.setTextColor(INK);
    button.setTextSize(12);
    button.setGravity(Gravity.CENTER);
    button.setPadding(dp(6), dp(7), dp(6), dp(7));
    button.setBackground(makePill(0xff1e293b));
    button.setOnClickListener(listener);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
    params.setMargins(dp(3), dp(2), dp(3), dp(2));
    row.addView(button, params);
    return button;
  }

  private GradientDrawable makePill(int color) {
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(color);
    shape.setCornerRadius(dp(10));
    return shape;
  }

  private int dp(float value) {
    return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
  }

  @Override public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.options, menu);
    return true;
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.Filter) {
      filterEnabled = !filterEnabled;
      if (filterEnabled) seismoView.filter(); else seismoView.unfilter();
      getPreferences(MODE_PRIVATE).edit().putBoolean("gravity_removal", filterEnabled).apply();
      if (gravityButton != null) gravityButton.setText(gravityLabel());
      item.setTitle(filterEnabled ? "Turn gravity removal off" : "Turn gravity removal on");
      return true;
    } else if (id == R.id.Pause) {
      paused = !paused;
      if (paused) seismoView.pause(); else seismoView.resume();
      item.setTitle(paused ? "Resume measurement" : "Pause measurement");
      return true;
    } else if (id == R.id.x) { selectAxis(0); return true; }
    else if (id == R.id.y) { selectAxis(1); return true; }
    else if (id == R.id.z) { selectAxis(2); return true; }
    else if (id == R.id.all) { selectAxis(3); return true; }
    else if (id == R.id.spectrum) {
      spectrum = !spectrum;
      seismoView.setSpectrum(spectrum);
    seismoView.setAutoRange(autoRange);
    if (!filterEnabled) seismoView.unfilter();
      getPreferences(MODE_PRIVATE).edit().putBoolean("spectrum", spectrum).apply();
      updateSpectrumButton();
      return true;
    } else if (id == R.id.Save) { seismoView.save(); return true; }
    else if (id == R.id.Export) { startActivity(new Intent(this, Export.class)); return true; }
    else if (id == R.id.Help) { startActivity(new Intent(this, Help.class)); return true; }
    return super.onOptionsItemSelected(item);
  }

  private String gravityLabel() { return filterEnabled ? "Gravity removed" : "Raw + gravity"; }

  private void selectAxis(int value) {
    axis = value;
    seismoView.setAxis(axis);
    getPreferences(MODE_PRIVATE).edit().putInt("axis", axis).apply();
    updateAxisButtons();
  }
}
