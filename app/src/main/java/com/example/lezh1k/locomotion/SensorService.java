package com.example.lezh1k.locomotion;


import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
//import android.support.annotation.RequiresApi;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import androidx.annotation.RequiresApi;

import static com.example.lezh1k.locomotion.Constants.*;
import static java.lang.Math.pow;


public class SensorService extends Service {

    // ** Simple objects **
    // remember if listeners are already running
    private boolean mRegistered = false;

    // correlate sensor timestamp and real time
    private long          mTimestampDeltaMilliSec;
    // counting the steps
    private float[]       mStepsCumul = {0,-1,-1}; // 0: detail, 1: regulary, 2: daily
    private float         mStepsSensBefore = 0;
    private float         mStepsTemp = 0;
    // atmospheric pressure, mpressure is initialized negative!
    private float         mPressure = -1, mPressureTemporary = 0, mPressureZ = cPRESSURE_SEA;
    private int           mPressCount = 0;
    // for measuring the settling time of pressure sensor
    private long          mPressStartTimestamp = 0; //nanoseconds

    // counting height
    private float[]       mHeightCumul = {0,-1,-1}; // 0: detail, 1: regulary, 2: daily
    private float         mHeightBefore = 0;
    // Reference height
    private long          mHeightRefTimestamp = 0;  //nanoseconds
    private float         mHeightRef = cINIT_HEIGHT_REFCAL;
    private float         mInitHeight;
    private float         mStepCount;

    private int         mLevel;
    private int         nowLevel;
    private float       stepsact = 0;

    // timestamp of sensor events (nanoseconds!)
    private long mEvtTimestampMilliSec = 0; // we only need one timestamp for all *Cumul-values


    // Remember calibration height for first pressure measurement
    private float         mCalibrationHeight = cINIT_HEIGHT_REFCAL;

    // ** External classes **
    // * My own classes *
    // SaveData
    private SaveData mSave;

    // * external *
    // to return a binder-Object
    private IBinder               mBinder = new LocalBinder();
    // SensorManager, to handle the sensors
    private SensorManager mSensorManager;
    // Sensors: Step-Sensor, pressure sensor and significant motion sensor
    private Sensor        mStepSensor, mBarometer, mMotion;


    // Start and stop alarm
    private AlarmReceiver mAlarm;

    //Preferences
    private SharedPreferences mSettings;

    //Notification Manager to update notification
    private NotificationManager mNotificationManager;

    //Pending Intent to call Activity from notification
    private PendingIntent mPIntentActivity;

    //For wakelocks
    private PowerManager mPowerManger;
    //Wakelock for settling of pressure sensor - acquire at first event, release at later event
    private PowerManager.WakeLock mWakelockSettle;

    // Was only needed for idleMessage, we now use standard messages
    // MessageQueue         mQueue;


    // ** Subclasses and arrays **

    // remember pressure for correlation
    private class         mPressureHistory{
        float             pressure;
        long              timestamp; //nanoseconds
        mPressureHistory(float p, long t){
            pressure = p;
            timestamp = t;
        }
    }


private class mStepCountHistory{
        float stepcount;
        mStepCountHistory(float stepcount){
             mStepCount = stepcount;
        }
}

    private class mHeightHistory{
        float mHeight;
        mHeightHistory(float h){
            mHeight = h;
        }
    }
    private ArrayList<mPressureHistory> mPressureHistoryList = new ArrayList<>();
    private ArrayList<mHeightHistory> mHeightHistoryList = new ArrayList<>();
    private ArrayList<Float> mStepCountHistoryList = new ArrayList<Float>();


    // Storing all data
    private class mStepSensorValues {
        long steptimestamp; // step sensor event timestamp in nanoseconds
        float stepstotal; // steps from sensor
        mStepSensorValues(long sts, float stt){
            steptimestamp = sts;
            stepstotal = stt;
        }
        String printdebug(float pressure, long pressuretimestamp, float height){
            String outline;
            SimpleDateFormat sdformati = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US); // fixed formatting, not local formatting
            // Correlation timestamp
            outline = sdformati.format(System.currentTimeMillis()) + ";";
            // Step event timestamp translated to real time
            outline = outline + sdformati.format(steptimestamp/ cNANO_IN_MILLISECONDS + mTimestampDeltaMilliSec) + ";";
            outline = outline + String.format(Locale.US,"%.3f; %.0f; %.0f; %.3f; %.3f; %.2f; %.3f; %.2f; %.2f \n",
                    (float)steptimestamp/ cNANO_IN_SECONDS,mStepsCumul[0],stepstotal,(float)pressuretimestamp/ cNANO_IN_SECONDS,pressure,height,
                    (float)mHeightRefTimestamp/ cNANO_IN_SECONDS,mHeightRef,mHeightCumul[0]);
            return outline;
        }
    }

    // We need a list to save the events and process (correlate) them asynchronously
    private ArrayList<mStepSensorValues> mStepHistoryList = new ArrayList<>();

    // for calculating delta we remember the last values
    //   we don't use the array, as it is difficult to handle init- and before-values
    private mStepSensorValues mStepValuesCorrBefore = null;

    public boolean walking;
    public boolean ascending;
    public boolean sameLevel;
    public int level;

    /* See https://developer.android.com/reference/android/app/Service.html#LocalServiceSample
       Public class to access Service
     */
    class LocalBinder extends Binder {
        SensorService getServerInstance() {
            return SensorService.this;
        }
    }

    private int mInterval = 5000; // 5 seconds by default, can be changed later
    private Handler mHandler;


    // ** Initialization **

    @Override
    public void onCreate() {
        mHandler = new Handler();
        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        // Get our own thread and looper
        HandlerThread thread = new HandlerThread("SensorService", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        thread.getLooper();
        // Initialize our sensors
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        //unfortunately neither StepCounter nor PressureSensor do have a wakeup-type
        // Step: No batching (fifo = 0 entries)
        mStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER,false);
        // Pressure: Fifo: 300 entries, maxdelay: 10 seconds
        mBarometer = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE,false);
        // Significant motion - only this one does have a wakeup type
        mMotion = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION,true);
        // create an Alarm receiver
        mAlarm = new AlarmReceiver();
        // to save our data to disk
        mSave = new SaveData(this);

        // for notification update
        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // for wakelocks
        mPowerManger = (PowerManager)getSystemService(Context.POWER_SERVICE);

        // get our values back from previous session
        restorePersistent();

        Log.d("motion2", String.valueOf(walking));
    }




    @Override
    public IBinder onBind(Intent intent){
        return mBinder;
    }

    // ** Start/Stop measuring **
    boolean startListeners() {
        mSave.saveDebugStatus("Start measurement requested");
        if (!mRegistered) {
            // this must finish, so request a wakelock
            @SuppressLint("InvalidWakeLockTag") PowerManager.WakeLock wakelock = mPowerManger.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "START");
            wakelock.acquire(cWAKELOCK_ALARM); //that should be more than enough - we will release it

            // Build an intent for starting our MainActivity from notification
            Intent nIntent = new Intent(this, MainActivity.class);
            // this is necessary when activity is started from a service
            //  we don't need to reuse an existing acitvity, as our main activity is stateless
            nIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Create pendingIntent which can be given to notification builder
            mPIntentActivity = PendingIntent.getActivity(this, 0, nIntent, 0);
            // back stack creation seems not to be necessary (would it even be possible from service?)
            Notification noti = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_walkinsteps)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.step_is_counting))
                    .setContentIntent(mPIntentActivity)
                    .build();
            startForeground(cNOTIID, noti);

            // Check, if we have statistic data from previous run to save
            periodicStatistics(System.currentTimeMillis(),cNOTRUNNING);

            // if there was no measurement in this periodic intervall, cumul-values will be set to -1
            if (mHeightCumul[1] < 0) mHeightCumul[1] = 0;
            if (mHeightCumul[2] < 0) mHeightCumul[2] = 0;
            if (mStepsCumul[1] < 0) mStepsCumul[1] = 0;
            if (mStepsCumul[2] < 0) mStepsCumul[2] = 0;
            // change should be saved
            savePersistent();


            if (getDetailSave("a"))
                mSave.saveStatistics(System.currentTimeMillis(), mStepsCumul[0], mHeightCumul[0], getHeight(), cSTAT_TYPE_START);

            mAlarm.setAlarm(this);

            // Register only pressure sensor listener
            //  step sensor will be registered when pressure sensor settling time is over
            boolean succ = mSensorManager.registerListener(mSensorBarListener, mBarometer, SensorManager.SENSOR_DELAY_GAME);
            mRegistered = true;
            mSensorManager.requestTriggerSensor(mMotionListener, mMotion);
            // update display
            getValues();

            //now we can release the wakelock
            if (wakelock.isHeld()) wakelock.release();
            return succ;
        }
        // if we are already registered to sensors, we don't have to do anything
        else return true;
    }

    int stopListeners() {
        savePersistent();

        mSave.saveDebugStatus("Stopping measurement");
        mSensorManager.unregisterListener(mSensorBarListener);
        mSensorManager.unregisterListener(mSensorStepListener);
        mSensorManager.cancelTriggerSensor(mMotionListener,mMotion);
        // make sure we have all work done
        correlateSensorEvents();

        if (getDetailSave("o"))
            mSave.saveStatistics(System.currentTimeMillis(), mStepsCumul[0], mHeightCumul[0], getHeight(), cSTAT_TYPE_STOP);


        //reset array
        mStepHistoryList.clear();
        mPressureHistoryList.clear();

        mStepValuesCorrBefore = null;
        mStepsSensBefore = 0;

        //reset pressure values
        mPressure = -1; //sensor settling and calibration has to be done again
        mPressStartTimestamp = 0; // pressure sensor will start anew
        mTimestampDeltaMilliSec = 0; // shouldn't be necessary, just make sure

        //after restart height reference will not be valid anymore
        mHeightRef = cINIT_HEIGHT_REFCAL;

        mAlarm.cancelAlarm(this);
        mRegistered = false;
        // update display
        getValues();
        // Now we don't care if we are killed
        stopForeground(true);

        return 0;
    }


    private boolean correlateSensorEvents() {
        int limit = mStepHistoryList.size(), calcindex;
        mSave.saveDebugStatus("correlation started");


        //here

        for (calcindex = 0; calcindex < limit; calcindex++) {

            int lowindex = 0, highindex = mPressureHistoryList.size() - 1, midindex = 0;
            long lasttimestamp;

            mStepSensorValues values = mStepHistoryList.get(0);

            if (values.steptimestamp > (mPressureHistoryList.get(highindex).timestamp)) {
                mSave.saveDebugStatus("No actual pressure value, putting additional task in queue 2 seconds later");
                Handler handler = new Handler();
                handler.postDelayed(this::correlateSensorEvents, cDELAY_CORRELATION);
                break;
            }

            while (lowindex <= highindex) {
                midindex = (lowindex + highindex) / 2;
                lasttimestamp = mPressureHistoryList.get(midindex).timestamp;
                if (values.steptimestamp < lasttimestamp) {
                    highindex = midindex - 1;
                } else if (values.steptimestamp > lasttimestamp) {
                    lowindex = midindex + 1;
                }
            }

            periodicStatistics(values.steptimestamp / cNANO_IN_MILLISECONDS
                    + mTimestampDeltaMilliSec, cISRUNNING);



            float initpressure = mPressureHistoryList.get(0).pressure;
            float pressure = mPressureHistoryList.get(midindex).pressure;
            long pressuretimestamp = mPressureHistoryList.get(midindex).timestamp;


            float initheight = calcHeight(initpressure);
            float height = calcHeight(pressure); //if previous
            mInitHeight = initheight;


            if (mStepValuesCorrBefore == null) {  //no beforevalues yet: init and pause
                // just save reference value
                mHeightRef = height;
                // we use the step sensor timestamp as reference
                mHeightRefTimestamp = values.steptimestamp;
            } else { //normal values
                // first save the steps
                for (int i = 0; i < mStepsCumul.length; i++) {
                    mStepsCumul[i] =
                            mStepsCumul[i] + values.stepstotal - mStepValuesCorrBefore.stepstotal;
                }
                // and timestamp for event (we will only need msec in Unix time)
                mEvtTimestampMilliSec = values.steptimestamp / cNANO_IN_MILLISECONDS + mTimestampDeltaMilliSec;


                if ( ( (values.steptimestamp - mStepValuesCorrBefore.steptimestamp)
                        / (values.stepstotal - mStepValuesCorrBefore.stepstotal)
                        > cMAX_STEP_DURATION * cNANO_IN_SECONDS
                ) ||
                        ( (height - mHeightBefore)
                                / (values.steptimestamp - mStepValuesCorrBefore.steptimestamp)
                                > cMAX_ELEV_GAIN
                        ) ||
                        ( mHeightRef <= cINIT_HEIGHT_REFCAL)
                ) {
                    mHeightRef = height;
                    mHeightRefTimestamp = values.steptimestamp;
                } else {
                    // Here is the only place where we count the ascending
                    // only count if ascending is greater than 1m
                    // lower values could be everything (e.g. atmospheric pressure change)

                    if ((height - mHeightRef) >= 1) {
                        Log.d("real ascending", String.valueOf(height - mHeightRef));

                        for (int i = 0; i < mHeightCumul.length; i++) {
                            mHeightCumul[i] = mHeightCumul[i] + height - mHeightRef;
                        }
                        mHeightRef = height;
                        mHeightRefTimestamp = values.steptimestamp;

                    } else {
                        if (((values.steptimestamp - mHeightRefTimestamp) > cMAX_DURATION_1M * cNANO_IN_SECONDS)
                                || ((height - mHeightRef) <= -1)) {
                            mHeightRef = height;
                            mHeightRefTimestamp = values.steptimestamp;
                        }
                    }

                }

            }


            mStepValuesCorrBefore = values;
            mHeightBefore = height;

            // ** Update activity, notification, debug-information and persistent values **
            savePersistent();

            if (mSettings.getBoolean(cPREF_DEBUG, false)) mSave.saveDebugValues(
                    values.printdebug(pressure, pressuretimestamp, height));

            if (getDetailSave("e"))
                mSave.saveStatistics(mEvtTimestampMilliSec,
                        mStepsCumul[0], mHeightCumul[0], height, cSTAT_TYPE_SENS);

            // Update Notification, put actual values in it
            Notification noti = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_walkinsteps)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.steps) + ": "
                            + String.format(Locale.getDefault(), "%.0f", mStepsCumul[0])
                            + " " + getString(R.string.height_accumulated) + ": "
                            + String.format(Locale.getDefault(), "%.1f", mHeightCumul[0]))
                    .setContentIntent(mPIntentActivity)
                    .build();
            mNotificationManager.notify(cNOTIID, noti);


            // Callback to MainActivity
            Intent callback = new Intent();
            callback.setAction("grmpl.mk.stepandheighcounter.custom.intent.Callback");
            if (mSettings.getBoolean(cPREF_DEBUG, false)) {
                String outtext = getString(R.string.out_stat_listlength) + Integer.toString(mStepHistoryList.size()) + "\n";
                outtext = outtext + getString(R.string.out_stat_pressure) + Float.toString(pressure) + "\n";
                outtext = outtext + getString(R.string.out_stat_referenceheight) + Float.toString(mHeightRef) + "\n";
                callback.putExtra("Status", outtext);
            } else callback.putExtra("Status", " ");
            callback.putExtra("Steps", mStepsCumul[0]);
            callback.putExtra("Height", height);
            callback.putExtra("Heightacc", mHeightCumul[0]);
            callback.putExtra("Registered", true);
            callback.putExtra("Stepstoday", mStepsCumul[2]);
            callback.putExtra("Heighttoday", mHeightCumul[2]);
            sendBroadcast(callback);

        }

        mSave.saveDebugStatus("correlation loop finished, " + Integer.toString(calcindex) +
                "/" + Integer.toString(limit) +
                ") items processed");

        mSensorManager.cancelTriggerSensor(mMotionListener, mMotion);
        mSensorManager.requestTriggerSensor(mMotionListener, mMotion);
        mSave.saveDebugStatus("Register to significant motion sensor from correlation task");
        return true;

    }


    // ** Listeners for sensors **

    private TriggerEventListener mMotionListener = new TriggerEventListener() {

        @SuppressLint("InvalidWakeLockTag")
        @Override
        public void onTrigger(TriggerEvent triggerEvent) {
            PowerManager.WakeLock wakelock;
            // do nothing, just acquire wakelock to let sensor events come through
            wakelock = mPowerManger.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SIGNIFICANT_MOTION");
            wakelock.acquire(cWAKELOCK_TRIGGER); //acquire for 30sec - this should not be a single move only
            mSave.saveDebugStatus("Wake up from trigger");
            mSensorManager.flush(mSensorBarListener);
            mSensorManager.flush(mSensorStepListener);
            //and register again
            mSensorManager.requestTriggerSensor(mMotionListener,mMotion);
            mSave.saveDebugStatus("Register to significant motion sensor from trigger");
            // unfortunately, trigger seems to be lost after some hours of operation,
            // so we register after every correlation, too (see above)
        }
    };

    private SensorEventListener mSensorBarListener = new SensorEventListener() {
        @RequiresApi(api = Build.VERSION_CODES.N)
        @SuppressLint("InvalidWakeLockTag")
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            if (mPressure < 0) {
                // first event
                if (mPressStartTimestamp == 0){
                    // get a wakelock, we have to finish this
                    mWakelockSettle = mPowerManger.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SENSOR_SETTLE");
                    mWakelockSettle.acquire(cWAKELOCK_SETTLE_PRESSURE);
                    mSave.saveDebugStatus("First pressure value, waiting 1 sec for sensor to settle down");
                    mPressStartTimestamp = sensorEvent.timestamp;
                    // calculate first timestampdelta
                    mTimestampDeltaMilliSec = System.currentTimeMillis()-(sensorEvent.timestamp / 1000000);
                }
                // settling time
                else if (sensorEvent.timestamp-mPressStartTimestamp < cPRESSURE_SETTLE_DURATION) {
                    // do nothing, just adjust timestampdelta
                    long tsdelta = System.currentTimeMillis()-(sensorEvent.timestamp / 1000000);
                    // timestampdelta must be positive (current time must be more than sensor timestamp)
                    //  so if delta is smaller it'a a better value
                    if ( tsdelta < mTimestampDeltaMilliSec )
                        mTimestampDeltaMilliSec = tsdelta;
                } // time is over - start measurement
                else {
                    mSave.saveDebugStatus("Wating time over, starting Step sensor");
                    // register StepSensor
                    boolean succ = mSensorManager.registerListener(mSensorStepListener, mStepSensor,
                            SensorManager.SENSOR_DELAY_FASTEST);
                    if (!succ){
                        Intent callback = new Intent();
                        callback.setAction("grmpl.mk.stepandheighcounter.custom.intent.Callback");
                        callback.putExtra("Status",getString(R.string.stepsensor_not_activated));
                        sendBroadcast(callback);
                        mSave.saveDebugStatus("Error in registering step sensor.");
                        stopListeners();
                    } else
                        mPressure = 0;
                    // wakelock can be released
                    if (mWakelockSettle.isHeld()) mWakelockSettle.release();
                }
            } else { //pressure> 0
                // We average over 5 values, this would be ~1sec
                mPressureTemporary = mPressureTemporary + sensorEvent.values[0];

                if (mPressCount == (cPRESSURE_AVG_COUNT - 1)) {
                    // mPressure is just the last valid average over 5 pressure values
                    //  if there is a problem with getting pressure values, we won't notice
                    mPressure = mPressureTemporary / cPRESSURE_AVG_COUNT;
                    // always check if calibration is needed
                    if (mCalibrationHeight > cINIT_HEIGHT_REFCAL)
                        calibrateHeight(mCalibrationHeight);


                    // remember the last 400 values of pressure
                    mPressureHistory pressure = new mPressureHistory(mPressure, sensorEvent.timestamp);
                    mPressureHistoryList.add(pressure);

                    if (mPressureHistoryList.size() > 4) {
                        if ((mPressureHistoryList.get(mPressureHistoryList.size() - 4).pressure - mPressureHistoryList.get(mPressureHistoryList.size() - 1).pressure > (float) 0.04)) {
                            ascending = true;
                            sameLevel = false;
                        }

                        //ascending
                        else if (mPressureHistoryList.get(mPressureHistoryList.size() - 1).pressure - mPressureHistoryList.get(mPressureHistoryList.size() - 4).pressure > (float) 0.04) {
                            ascending = false;
                            sameLevel = false;

                        } else {
                            sameLevel = true;
                        }
                    }

                    if (mPressureHistoryList.size() > cMAX_PRESSURE_SAVE)
                        mPressureHistoryList.remove(0);
                    if (mSettings.getBoolean(cPREF_DEBUG, false)) {
                        mSave.saveDebugStatus(
                                String.format(Locale.US, "Pressure value %.2f saved, listsize: %d",
                                        mPressure, mPressureHistoryList.size())
                        );
                    }

                    mPressCount = 0;
                    mPressureTemporary = 0;
                } else mPressCount++;
            }

        }



        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
            //Don't care
        }


    };

    private SensorEventListener mSensorStepListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            //Don't care
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            long steptimestamp;
            stepsact = event.values[0]; // new value
            Log.d("stepsact", String.valueOf(stepsact));
            steptimestamp = event.timestamp;
            /* first sensor reading after pause or initialization
                initialize mStepsBefore and save initial array element with reference values
             */

            mStepsTemp = mStepsSensBefore;

            if (mStepsSensBefore == 0) { //first time only.
                mStepsSensBefore = stepsact; //first data
                mStepSensorValues data = new mStepSensorValues(steptimestamp, stepsact);
                mStepHistoryList.add(data); //first value in list

                Handler handler = new Handler();
                handler.postDelayed(SensorService.this::correlateSensorEvents,cDELAY_CORRELATION_FIRST);
                mSave.saveDebugStatus("Step Counter init.");
            }

            else if ((stepsact - mStepsSensBefore) >cMIN_STEPS_DELTA) { //an increase, walking
                // just save the data
                mStepSensorValues data = new mStepSensorValues(steptimestamp, stepsact);
                mStepHistoryList.add(data); //second value in list

                // and remember it for next check
                mStepsSensBefore = stepsact;
                Handler handler = new Handler();
                handler.postDelayed(SensorService.this::correlateSensorEvents,cDELAY_CORRELATION_FIRST);

                mSave.saveDebugStatus("Step Counter event saved");
            }
        }

    };

    // ** Methods for Activity and helpers **

    void calibrateLevel (int cLevel){
        mLevel = cLevel;

    }

    void calibrateHeight(float cHeight){

        // if pressure is already measured, calculate sea level pressure from actual pressure
        if (mPressure > 0) {
            if (getDetailSave("c"))
                mSave.saveStatistics(System.currentTimeMillis(), mStepsCumul[0], mHeightCumul[0], getHeight(), getString(R.string.stat_type_calibration_before));

            // get pressure of height reference (saving it would be more difficult than reextracting
            //   as calibration would be done seldom)
            double heightrefpressure = mPressureZ * pow(1-(mHeightRef * 0.0065 / 288.15),5.255);
            // calculate new mPressureZ
            mPressureZ = (float) (mPressure / pow((1 - (cHeight * 0.0065 / 288.15)), 5.255));
            // calculate new height reference
            mHeightRef = (float) ((1 - pow((heightrefpressure / mPressureZ), (1 / 5.255))) * 288.15 / 0.0065);
            SharedPreferences.Editor editpref = mSettings.edit();
            editpref.putFloat("mPressureZ",mPressureZ);
            editpref.apply();
            // calibration done, temporary value must be cleared
            mCalibrationHeight = cINIT_HEIGHT_REFCAL;
            if (getDetailSave("c"))
                mSave.saveStatistics(System.currentTimeMillis(), mStepsCumul[0], mHeightCumul[0], getHeight(), getString(R.string.stat_type_calibration_after));

        }
        // if there is no pressure yet, save value for later calibration
        else mCalibrationHeight = cHeight;

    }

    void resetData() {
        if (getDetailSave("r"))
            mSave.saveStatistics(System.currentTimeMillis(), mStepsCumul[0], mHeightCumul[0], getHeight(), getString(R.string.stat_type_reset_before) );
        mStepsCumul[0] = 0;
        mPressureZ = cPRESSURE_SEA;
        mHeightRef = cINIT_HEIGHT_REFCAL; // 0 as init-value would not work at sea
        mHeightCumul[0] = 0;
        savePersistent();
        if (getDetailSave("r"))
            mSave.saveStatistics(System.currentTimeMillis(), mStepsCumul[0], mHeightCumul[0], getHeight(), getString(R.string.stat_type_reset_after) );
    }

    void getValues() {
        // if we are measuring, update statistic values
        if (mRegistered) periodicStatistics(System.currentTimeMillis(),cISRUNNING);
        else periodicStatistics(System.currentTimeMillis(),cNOTRUNNING);

        Intent callback = new Intent();
        callback.setAction("com.example.lezh1k.locomotion.custom.intent.Callback");

        if (mSettings.getBoolean(cPREF_DEBUG, false)) {
            String outtext = getString(R.string.out_stat_listlength) + Integer.toString(mStepHistoryList.size()) + "\n";
            outtext = outtext + getString(R.string.out_stat_pressure) + Float.toString(mPressure) + "\n";
            outtext = outtext + getString(R.string.out_stat_referenceheight) + Float.toString(mHeightRef) + "\n";
            callback.putExtra("Status", outtext);
        } else callback.putExtra("Status", " ");
        callback.putExtra("Steps",mStepsCumul[0]);
        callback.putExtra("Height",getHeight());
        callback.putExtra("Heightacc",mHeightCumul[0]);
        callback.putExtra("Registered",mRegistered);
        callback.putExtra("Stepstoday", mStepsCumul[2]);
        callback.putExtra("Heighttoday", mHeightCumul[2]);

        sendBroadcast(callback);
    }

    private boolean getDetailSave(String identifier){
        Set<String> detail_multi = mSettings.getStringSet(cPREF_STAT_DETAIL_MULTI, cPREF_STAT_DETAIL_MULTI_DEFAULT);

        for (String s:  detail_multi ) {
            if ( s.equals(identifier) ) return true;
        }

        return false;
    }

    float getHeight(){

        if (mPressure <= 0) return 9998F; // no measurement yet


        return calcHeight(mPressure);
    }

    String getMovement() {
        if (mPressure <= 0) return "You are on the same level"; // no measurement yet

        mStepCountHistoryList.add(stepsact);
        if(mStepCountHistoryList.size() > 4) {
            if (mStepCountHistoryList.get(mStepCountHistoryList.size() - 1) > mStepCountHistoryList.get(mStepCountHistoryList.size() - 2)) {
                walking = true;
            }
            else {
                walking = false;
            }
        }



        if (sameLevel){ //you are on the same level
            return "You are on the same level";
            }

        else {
            if (ascending && walking){
                return "You are walking UP";
            }
            else if (ascending && walking == false){
                return "You are taking the lift UP";
            }
            else if (ascending == false && walking){
                return "You are walking DOWN";
            }
            else if (ascending ==false && walking ==false ){
                return "You are taking the lift DOWN";

            }
        }


        return null;
    }


    String getLevel(){
        if (calcHeight(mPressure) > mInitHeight){
            return String.valueOf(mLevel + Integer.valueOf((int) (calcHeight(mPressure) - mInitHeight)) /3 );
        }
        else {
            return String.valueOf(mLevel - Integer.valueOf((int) ((int)  mInitHeight - (calcHeight(mPressure) /3))));
        }


    }


    private float calcHeight(float pressure){
        return (float) ((1 - pow((pressure / mPressureZ), (1 / 5.255))) * 288.15 / 0.0065);
    }

    private void savePersistent() {
        SharedPreferences.Editor editpref = mSettings.edit();

        for ( int i = 0; i < mStepsCumul.length; i++){
            editpref.putFloat("mStepsCumul" + i, mStepsCumul[i]);
            editpref.putFloat("mHeightCumul" + i, mHeightCumul[i]);
        }
        editpref.putLong("mEvtTimestampMilliSec", mEvtTimestampMilliSec);
        editpref.putFloat("mPressureZ", mPressureZ);
        editpref.apply();

    }

    private void restorePersistent() {
        // only steps, elevation gain and calibration is important to remember
        //  all other values will be calculated again after init
        for ( int i = 0; i < mStepsCumul.length; i++){
            mStepsCumul[i]   = mSettings.getFloat("mStepsCumul" + i, 0);
            mHeightCumul[i]  = mSettings.getFloat("mHeightCumul" + i, 0);
        }
        mEvtTimestampMilliSec = mSettings.getLong("mEvtTimestampMilliSec", 0);
        mPressureZ = mSettings.getFloat("mPressureZ", cPRESSURE_SEA);
    }

    private void periodicStatistics(long acttimestamp_msec, int running){  //acttimestamp in milliseconds

        TimeZone tz = TimeZone.getDefault();
        boolean setevttimestamp = false;

        if( mEvtTimestampMilliSec > cMILLISECONDS_IN_YEAR &&  // plausibility: mEvtTimestampMilliSec should be at least in 1971
                (mEvtTimestampMilliSec / (15 * 60 * 1000) < acttimestamp_msec / (15 * 60 * 1000))) {
            // Do we have to save regular statistics?
            if (mSettings.getBoolean(cPREF_STAT_HOUR, false)){

                long interval_msec = Integer.valueOf(mSettings.getString(cPREF_STAT_HOUR_MIN, "30")) * 60 * 1000;
                long evtint = mEvtTimestampMilliSec / interval_msec; // integer-division: correct sequence is necessary!
                long currint = acttimestamp_msec / interval_msec;
                if ( evtint < currint ){

                    mSave.saveStatistics( (evtint + 1) * interval_msec,
                            mStepsCumul[1],mHeightCumul[1],cSTAT_TYPE_REGULAR);
                    mStepsCumul[1] = running;  // method can be called even if measurement is not running
                    mHeightCumul[1] = running; // so we have to save actual state

                    setevttimestamp = true;

                    if (currint - evtint > 100) evtint = ( 24*60*60*1000 * ( acttimestamp_msec/(24 * 60 * 60 * 1000) )
                            - tz.getOffset(acttimestamp_msec) ) / interval_msec - 1 ;
                    for (long l = evtint + 2; l <= currint; l++)
                        mSave.saveStatistics( l * interval_msec, running, running,cSTAT_TYPE_REGULAR);
                }
                //else nothing to do
            }
            // do we have to save daily statistics?
            if (mSettings.getBoolean(cPREF_STAT_DAILY, false)){
                // get the interval duration for regular statistics
                long interval_msec = 24 * 60 * 60 * 1000;//24h hours
                // We calculate our days based on current timezone:
                long evtint = ( mEvtTimestampMilliSec + tz.getOffset(mEvtTimestampMilliSec) ) / interval_msec; //int is enough here
                long currint = ( acttimestamp_msec + tz.getOffset(acttimestamp_msec) )/ interval_msec;
                // if we have values from previous interval, last interval is finished, we can save them
                if ( evtint < currint ){
                    // as our days are UTC-days ( integer * 24h ), we have to correct the timestamps with timezone-information
                    //   timestamp for saving would be 0:00
                    mSave.saveStatistics( evtint * interval_msec - tz.getOffset(mEvtTimestampMilliSec),
                            mStepsCumul[2],mHeightCumul[2],cSTAT_TYPE_DAILY);
                    mStepsCumul[2] = running;  //see above
                    mHeightCumul[2] = running; //see above
                    setevttimestamp = true;
                    // fill up all intervals without values, but not more than one week
                    if (currint - evtint > 8) evtint = currint - 8;
                    for (long l = evtint + 1; l < currint; l++)
                        mSave.saveStatistics( l * interval_msec - tz.getOffset(mEvtTimestampMilliSec), running, running, cSTAT_TYPE_DAILY);
                }
                //else nothing to do
            }
            if (setevttimestamp){
                mEvtTimestampMilliSec = acttimestamp_msec;
                savePersistent();
            }

        }
    }

    @Override
    public void onDestroy() {
        savePersistent(); // should be done in stopListeners, but maybe we don't reach this method
        stopListeners();

        mAlarm.cancelAlarm(this);

        super.onDestroy();
    }


}
