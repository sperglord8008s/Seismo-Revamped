package com.ariwilson.seismowallpaper;

import android.content.Context;
import android.hardware.Sensor; 
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class AccelerometerReader { 
  public volatile float x = 0;
  public volatile float y = 0;
  public volatile float z = 0;

  public AccelerometerReader(Context ctx) throws UnsupportedOperationException {
    sensor_manager_ = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
    accelerometer_ = sensor_manager_.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    start();
  }

  private SensorEventListener listener_ = new SensorEventListener(){
    public void onAccuracyChanged(Sensor arg0, int arg1) {}

    public void onSensorChanged(SensorEvent evt) {
      float vals[] = evt.values;
      
      if(evt.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
        x = vals[0];
        y = vals[1];
        z = vals[2];
      }
    }
  };

  public void close() {
    sensor_manager_.unregisterListener(listener_);
    registered_ = false;
  }

  public void setPeriod(int period) {
    sampling_period_us_ = Math.max(5000, Math.min(100000, period * 1000));
    if (registered_) {
      sensor_manager_.unregisterListener(listener_);
      registered_ = false;
      start();
    }
  }

  public void start() {
    if (!registered_ && accelerometer_ != null) {
      registered_ = sensor_manager_.registerListener(listener_, accelerometer_,
          sampling_period_us_);
    }
  }

  private SensorManager sensor_manager_;
  private Sensor accelerometer_;
  private boolean registered_;
  private int sampling_period_us_ = 20000;
}
