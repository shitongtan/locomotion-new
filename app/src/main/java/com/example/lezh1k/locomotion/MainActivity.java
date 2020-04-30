package com.example.lezh1k.locomotion;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
//import android.support.annotation.NonNull;
//import android.support.annotation.Nullable;
//import android.support.design.widget.FloatingActionButton;
//import android.support.design.widget.Snackbar;
//import android.support.v4.app.ActivityCompat;
//import android.support.v4.content.ContextCompat;
//import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


//mapmatching
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.printer.AndroidPrinter;
import com.elvishew.xlog.printer.Printer;
import com.elvishew.xlog.printer.file.FilePrinter;
import com.elvishew.xlog.printer.file.backup.FileSizeBackupStrategy;
import com.elvishew.xlog.printer.file.naming.FileNameGenerator;

import mad.location.manager.lib.Commons.Utils;
import mad.location.manager.lib.Interfaces.ILogger;
import mad.location.manager.lib.Interfaces.LocationServiceInterface;
import mad.location.manager.lib.Loggers.GeohashRTFilter;
import mad.location.manager.lib.SensorAux.SensorCalibrator;
import mad.location.manager.lib.Services.KalmanLocationService;
import mad.location.manager.lib.Services.ServicesHelper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

import com.example.lezh1k.locomotion.Interfaces.MapInterface;
import com.example.lezh1k.locomotion.Presenters.MapPresenter;
import com.mapbox.android.core.location.LocationEngine;
//import com.mapbox.android.core.location.LocationEngineListener;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.matching.v5.MapboxMapMatching;
import com.mapbox.api.matching.v5.models.MapMatchingMatching;
import com.mapbox.api.matching.v5.models.MapMatchingResponse;
import com.mapbox.core.exceptions.ServicesException;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
//import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;

import com.example.lezh1k.locomotion.SensorService.LocalBinder;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.mapboxsdk.utils.ColorUtils;

import org.w3c.dom.Text;

import static com.example.lezh1k.locomotion.Constants.*;
import static com.mapbox.api.isochrone.IsochroneCriteria.PROFILE_DRIVING;
import static com.mapbox.core.constants.Constants.PRECISION_6;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, PermissionsListener, LocationServiceInterface, MapInterface, ILogger {


    private SharedPreferences mSharedPref;
    private String xLogFolderPath;
    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private Location lastLocation;
    private String movement;


    class ChangableFileNameGenerator implements FileNameGenerator {
        private String fileName;

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public ChangableFileNameGenerator() {
        }

        @Override
        public boolean isFileNameChangeable() {
            return true;
        }

        @Override
        public String generateFileName(int logLevel, long timestamp) {
            return fileName;
        }
    }

    ChangableFileNameGenerator xLogFileNameGenerator = new ChangableFileNameGenerator();

    public void initXlogPrintersFileName() {
        sdf.setTimeZone(TimeZone.getDefault());
        String dateStr = sdf.format(System.currentTimeMillis());
        String fileName = dateStr;
        final int secondsIn24Hour = 86400; //I don't think that it's possible to press button more frequently
        for (int i = 0; i < secondsIn24Hour; ++i) {
            fileName = String.format("%s_%d", dateStr, i);
            File f = new File(xLogFolderPath, fileName);
            if (!f.exists())
                break;
        }
        xLogFileNameGenerator.setFileName(fileName);
    }

    @Override
    public void log2file(String format, Object... args) {
        XLog.i(format, args);
    }

    class RefreshTask extends AsyncTask {
        boolean needTerminate = false;
        long deltaT;
        Context owner;

        RefreshTask(long deltaTMs, Context owner) {
            this.owner = owner;
            this.deltaT = deltaTMs;
        }

        @Override
        protected Object doInBackground(Object[] objects) {
            while (!needTerminate) {
                try {
                    Thread.sleep(deltaT);
                    publishProgress();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Object... values) {
//            TextView tvStatus = (TextView) findViewById(R.id.tvStatus);
//            TextView tvDistance = (TextView) findViewById(R.id.tvDistance);
            Log.d("islogging", String.valueOf(m_isLogging));
            if (m_isLogging) {
                if (m_geoHashRTFilter == null)
                    return;

                TextView calibrationStatus = (TextView) findViewById(R.id.textView2);
                ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);
                int calibrationPercentage = m_sensorCalibrator.getAbsCalibration();

                progressBar.setProgress(m_sensorCalibrator.getAbsCalibration());
                if (calibrationPercentage < 50) {

                    calibrationStatus.setText("Calibrating ..");
                }
                else if (calibrationPercentage >= 50 && calibrationPercentage < 100)
                { calibrationStatus.setText("Almost there ..!");}

                else
                { calibrationStatus.setText("You're good to go!");}


                if (m_sensorCalibrator.getDcAbsLinearAcceleration().isCalculated() &&
                        m_sensorCalibrator.getDcLinearAcceleration().isCalculated()) {
                    set_isCalibrating(false, false);
//                    tvDistance.setText(m_sensorCalibrator.getDcLinearAcceleration().deviationInfoString());
                }

            } else {
                if (!m_sensorCalibrator.isInProgress())
                    return;


            }
        }
    }

    /*********************************************************/

    private MapPresenter m_presenter;
    private MapboxMap m_map;
    private MapView m_mapView;
    private PermissionsManager permissionsManager;

    private GeohashRTFilter m_geoHashRTFilter;
    private SensorCalibrator m_sensorCalibrator = null;
    private boolean m_isLogging = false;
    private boolean m_isCalibrating = false;
    private RefreshTask m_refreshTask = new RefreshTask(1000l, this);
//    LoadGeoJson _loadGeoJson;



    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {

        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            enableLocationComponent(m_map.getStyle());
            Log.d("permissions", String.valueOf(granted));
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
            finish();
        }

    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void set_isLogging(boolean isLogging) {
        Button btnStartStopTracking = (Button) findViewById(R.id.btnStartStopTracking);
//        TextView tvStatus = (TextView) findViewById(R.id.tvStatus);
//        Button btnCalibrateSensors = (Button) findViewById(R.id.btnCalibrateSensors);
        String btnStartStopTrackingText;
        String btnTvStatusText;


        if (isLogging) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            m_presenter.stop();
            m_presenter.start();
            m_geoHashRTFilter.stop();
            m_geoHashRTFilter.reset(this);
            int a = 10;
            Log.d("tracking", String.valueOf(a));
            ServicesHelper.getLocationService(this, value -> {
                if (value.IsRunning()) {
                    return;
                }
                value.stop();
                initXlogPrintersFileName();
                try {
                    KalmanLocationService.Settings settings =
                            new KalmanLocationService.Settings(
                                    Utils.ACCELEROMETER_DEFAULT_DEVIATION,
                                    Integer.parseInt(mSharedPref.getString("pref_gps_min_distance", "")),
                                    Integer.parseInt(mSharedPref.getString("pref_gps_min_time", "")),
                                    Integer.parseInt(mSharedPref.getString("pref_position_min_time", "")),
                                    Integer.parseInt(mSharedPref.getString("pref_geohash_precision", "")),
                                    Integer.parseInt(mSharedPref.getString("pref_geohash_min_point", "")),
                                    Double.parseDouble(mSharedPref.getString("pref_sensor_frequency", "")),
                                    this,
                                    false,
                                    Utils.DEFAULT_VEL_FACTOR,
                                    Utils.DEFAULT_POS_FACTOR
                            );
                    value.reset(settings); //warning!! here you can adjust your filter behavior
                    value.start();
                } catch (NumberFormatException ex) { // handle your exception
                    Log.d("not a number", String.valueOf(mSharedPref));
                }
                ;
            });

            btnStartStopTrackingText = "Stop tracking";
            btnTvStatusText = "Tracking is in progress";

        } else {
            btnStartStopTrackingText = "Start tracking";
            m_presenter.stop();
            ServicesHelper.getLocationService(this, value -> {
                value.stop();
            });
        }

        if (btnStartStopTracking != null)
            btnStartStopTracking.setText(btnStartStopTrackingText);
        btnStartStopTracking.setEnabled(!isLogging);
        m_isLogging = isLogging;
    }

    private void set_isCalibrating(boolean isCalibrating, boolean byUser) {
        Button btnStartStopTracking = (Button) findViewById(R.id.btnStartStopTracking);
        String tvStatusText;

        if (isCalibrating) {
            tvStatusText = "Calibrating..";
            m_sensorCalibrator.reset();
            m_sensorCalibrator.start();
        } else {
            tvStatusText = byUser ? "Calibration finished by user" : "Calibration finished";
            m_sensorCalibrator.stop();
        }

        btnStartStopTracking.setEnabled(!isCalibrating);
        m_isCalibrating = isCalibrating;
    }

    private void initActivity() {

        String[] interestedPermissions;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            interestedPermissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        } else {
            interestedPermissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }

        ArrayList<String> lstPermissions = new ArrayList<>(interestedPermissions.length);
        for (String perm : interestedPermissions) {
            if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                lstPermissions.add(perm);
            }
        }

        if (!lstPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, lstPermissions.toArray(new String[0]),
                    100);
        }

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (sensorManager == null || locationManager == null) {
            System.exit(1);
        }

        m_sensorCalibrator = new SensorCalibrator(sensorManager);
        ServicesHelper.getLocationService(this, value -> {
            set_isLogging(value.IsRunning());
            int b = 12;
            Log.d("tracking", String.valueOf(b));
        });
        set_isCalibrating(false, true);
    }

    //uncaught exceptions
    private Thread.UncaughtExceptionHandler defaultUEH;
    // handler listener
    private Thread.UncaughtExceptionHandler _unCaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            try {
                XLog.i("UNHANDLED EXCEPTION: %s, stack : %s", ex.toString(), ex.getStackTrace());
            } catch (Exception e) {
                Log.i("SensorDataCollector", String.format("Megaunhandled exception : %s, %s, %s",
                        e.toString(), ex.toString(), ex.getStackTrace()));
            }
            defaultUEH.uncaughtException(thread, ex);
        }
    };

    private void enableLocationComponent(@NonNull Style loadedMapStyle) {
// Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            Log.d("permissionsgranted", String.valueOf(m_map));
            // Get an instance of the component
            LocationComponent locationComponent = m_map.getLocationComponent();

            // Activate with options
            locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this, loadedMapStyle).build());
            // Enable to make component visible
            locationComponent.setLocationComponentEnabled(true);

            // Set the component's camera mode
            locationComponent.setCameraMode(CameraMode.TRACKING);

            // Set the component's render mode
            locationComponent.setRenderMode(RenderMode.COMPASS);
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }


    private Context context;

    @Override
    public void locationChanged(Location location) {
        if (m_map != null && m_presenter != null) {
            m_presenter.locationChanged(location, m_map.getCameraPosition());
        }


    }


    public static final int FILTER_KALMAN_ONLY = 0;
    public static final int FILTER_KALMAN_WITH_GEO = 1;
    public static final int GPS_ONLY = 2;
    private int routeColors[] = {R.color.mapbox_blue, R.color.colorAccent, R.color.cornflowerblue};

    private int routeWidths[] = {1, 3, 2};
    private Polyline lines[] = new Polyline[3];
    private double originlat;
    private double originlong;
    private double destinationlat;
    private double destinationlong;
    private List<LatLng> route2;

    @Override
    public void showRoute(List<LatLng> route, int interestedRoute) {
        Log.d("routefind", String.valueOf(route));
        route2 = route;
        if(route.size()>0) {
            originlat = route.get(0).getLatitude();
            originlong = route.get(0).getLongitude();
            destinationlat = route.get(route.size()-1).getLatitude();
            destinationlong = route.get(route.size()-1).getLongitude();
        }


        int a = 16;
        Log.d("shownmap", String.valueOf(a));
        CheckBox cbGps, cbFilteredKalman, cbFilteredKalmanGeo;
        cbGps = (CheckBox) findViewById(R.id.cbGPS);
        cbFilteredKalman = (CheckBox) findViewById(R.id.cbFilteredKalman);
        cbFilteredKalmanGeo = (CheckBox) findViewById(R.id.cbFilteredKalmanGeo);
        boolean enabled[] = {cbFilteredKalman.isChecked(), cbFilteredKalmanGeo.isChecked(), cbGps.isChecked()};
        if (m_map != null) {
            runOnUiThread(() ->
                    m_mapView.post(() -> {
                        if (lines[interestedRoute] != null)
                            m_map.removeAnnotation(lines[interestedRoute]);

                        if (!enabled[interestedRoute])
                            route.clear(); //too many hacks here

                        lines[interestedRoute] = m_map.addPolyline(new PolylineOptions()
                                .addAll(route)
                                .color(ContextCompat.getColor(this, routeColors[interestedRoute]))
                                .width(routeWidths[interestedRoute]));
                    }));
        }
    }



    @Override
    public void moveCamera(CameraPosition position) {
        runOnUiThread(() ->
                m_mapView.postDelayed(() -> {
                    if (m_map != null) {
                        m_map.animateCamera(CameraUpdateFactory.newCameraPosition(position));
                    }
                }, 100));
    }

    @Override
    public void setAllGesturesEnabled(boolean enabled) {
        if (enabled) {
            m_mapView.postDelayed(() -> {
                m_map.getUiSettings().setScrollGesturesEnabled(true);
                m_map.getUiSettings().setZoomGesturesEnabled(true);
                m_map.getUiSettings().setDoubleTapGesturesEnabled(true);
            }, 500);
        } else {
            m_map.getUiSettings().setScrollGesturesEnabled(false);
            m_map.getUiSettings().setZoomGesturesEnabled(false);
            m_map.getUiSettings().setDoubleTapGesturesEnabled(false);
        }
    }


    @Override
    public void onMapReady(@NonNull MapboxMap mapboxMap) {

    }

    public void setupMap(@Nullable Bundle savedInstanceState) {
        m_mapView = (MapView) findViewById(R.id.mapView);
        m_mapView.onCreate(savedInstanceState);

        m_presenter = new MapPresenter(this, this, m_geoHashRTFilter);
        m_mapView.getMapAsync(mapboxMap -> {
            MainActivity this_ = this;
            m_map = mapboxMap;
            ProgressDialog progress = new ProgressDialog(this);
            progress.setTitle("Loading");
            progress.setMessage("Wait while map loading...");
            progress.setCancelable(false); // disable dismiss by tapping outside of the dialog
            progress.show();

            m_map.setStyle(Style.MAPBOX_STREETS);

            m_map.setStyle(Style.SATELLITE_STREETS, new Style.OnStyleLoaded() {
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    m_map.getUiSettings().setLogoEnabled(false);
                    m_map.getUiSettings().setAttributionEnabled(false);
                    m_map.getUiSettings().setTiltGesturesEnabled(false);
                    int leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
                    int topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 56, getResources().getDisplayMetrics());
                    int rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
                    int bottomMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
                    m_map.getUiSettings().setCompassMargins(leftMargin, topMargin, rightMargin, bottomMargin);
                    ServicesHelper.addLocationServiceInterface(this_);
                    enableLocationComponent(style);
                    m_presenter.getRoute();
//                    _loadGeoJson = new LoadGeoJson(MainActivity.this);
//                    _loadGeoJson.execute();

                        progress.dismiss();
//                        ServicesHelper.addLocationServiceInterface((LocationServiceInterface) this_);
                    Log.d("doinmap", String.valueOf(lastLocation));
                }

            });

        });
    }


    //Mapmatching


    private class LoadGeoJson extends AsyncTask<Void, Void, FeatureCollection> {

        private WeakReference<MainActivity> weakReference;
        private FeatureCollection featureCollection;


        LoadGeoJson(MainActivity activity) {
            this.weakReference = new WeakReference<>(activity);
        }



        @Override
        protected FeatureCollection doInBackground(Void... voids) {
            while (true) {
                MainActivity activity = weakReference.get();
                try {
                    if (true){

                        double latitudeCoord;
                        double longitudeCoord;
                        String mapMatchedCoord ="";
                        String totalMapMatchedCoord="";
                        //Create a string for the all the route coordinates.


                            for (int i = 2; i < route2.size()-1; i++) {
                                latitudeCoord = route2.get(i).getLatitude();
                                longitudeCoord = route2.get(i).getLongitude();
                                mapMatchedCoord = "[" + longitudeCoord + "," + latitudeCoord + "],";
                                totalMapMatchedCoord += mapMatchedCoord;
                                //get lat and long from each index i

                            }

                            return featureCollection = FeatureCollection.fromJson("{\n" +
                                "    \"type\": \"FeatureCollection\",\n" +
                                "    \"features\": [\n" +
                                "    {\n" +
                                "    \"type\": \"Feature\",\n" +
                                "    \"properties\": {},\n" +
                                "    \"geometry\": {\n" +
                                "    \"type\": \"LineString\",\n" +
                                "    \"coordinates\": [\n" +
                                totalMapMatchedCoord +
                                "[" +  destinationlong + "," + destinationlat + "]" +
                                "\n" +
                                "\n" +
                                "    ]\n" +
                                "    }\n" +
                                "    }\n" +
                                "    ]\n" +
                                "    }\n"
                        );

                    }
                } catch (Exception exception) {
                    Timber.e("Exception Loading GeoJSON: %s", exception.toString());
                }
            if (false){
                return null;}
            }
        }

        @Override
        protected void onPostExecute(@Nullable FeatureCollection featureCollection) {
            super.onPostExecute(featureCollection);
            Log.d("bouncetype3", String.valueOf(featureCollection.getClass()));
            MainActivity activity = weakReference.get();
            if (activity != null && featureCollection != null) {
                activity.drawLines(featureCollection);
                Log.d("drawing", String.valueOf(activity));
            }

        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void drawMapMatched(@NonNull List<MapMatchingMatching> matchings) { //not raw
        Style style = m_map.getStyle();
        if (style != null && !matchings.isEmpty()) {
            style.addSource(new GeoJsonSource("matched-source-id", Feature.fromGeometry(LineString.fromPolyline(
                    Objects.requireNonNull(matchings.get(0).geometry()), PRECISION_6)))
            );
            style.addLayer(new LineLayer("matched-layer-id", "matched-source-id")
                    .withProperties(
                            lineColor(ColorUtils.colorToRgbaString(Color.parseColor("#d4d544"))),
                            lineWidth(6f))
            );
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void requestMapMatched(Feature feature) {
        List<Point> points = ((LineString) Objects.requireNonNull(feature.geometry())).coordinates();
        Log.d("feature", String.valueOf(feature.geometry()));
        Log.d("featurenongeo", String.valueOf(feature));
        Log.d("features", "will be  great");
        try {
            // Setup the request using a client.
            MapboxMapMatching client = MapboxMapMatching.builder()
                    .accessToken(Objects.requireNonNull(Mapbox.getAccessToken()))
                    .profile(PROFILE_DRIVING)
                    .coordinates(points)
                    .build();

            // Execute the API call and handle the response.
            client.enqueueCall(new Callback<MapMatchingResponse>() {
                @Override
                public void onResponse(@NonNull Call<MapMatchingResponse> call,
                                       @NonNull Response<MapMatchingResponse> response) {

                    if (response.code() == 200) {
                        drawMapMatched(Objects.requireNonNull(response.body()).matchings());
                        Log.d("features", "will be  greater");


                    } else {
                        Timber.e("MapboxMapMatching failed with %s", response.code());
                    }
                }

                @Override
                public void onFailure(Call<MapMatchingResponse> call, Throwable throwable) {
                    Timber.e(throwable, "MapboxMapMatching error");
                }
            });
        } catch (ServicesException servicesException) {
            Timber.e(servicesException, "MapboxMapMatching error");
        }
    }

    private void drawBeforeMapMatching(Feature feature) { //raw
        m_map.getStyle(style -> {
            style.addSource(new GeoJsonSource("pre-matched-source-id", feature));
            style.addLayer(new LineLayer("pre-matched-layer-id", "pre-matched-source-id").withProperties(
                    lineColor(ColorUtils.colorToRgbaString(Color.parseColor("#c14a00"))),
                    lineWidth(6f),
                    lineOpacity(1f)
            ));
        });
        Log.d("drawbefore", "mapmatching");
    }

    private void drawLines(@NonNull FeatureCollection featureCollection) {
        List<Feature> features = featureCollection.features();
        if (features != null && features.size() > 0) {
            Feature feature = features.get(0);
            Log.d("features", String.valueOf(features.get(0)));
            requestMapMatched(feature);
        }
    }


    private TextView mStatusText, mHeightText, mStepText, mHeightaccText, mStepDailyText, mHeightDailyText, movementOfUser, levelOfUser;
    private EditText mCalibrateIn;
    private Button mCalibrateHeight, btnStartStopTracking;

    private ProgressBar mStepDailyProgress, mHeightDailyProgress;
    boolean mBounded = false, mRunning = false;
    private SensorService mSensService;
    private MyReceiver mReceiver = null;
    private SharedPreferences mSettings;
    private SaveData mSave;
    private int mInterval = 500; // 5 seconds by default, can be changed later
    private Handler mHandler;
    private LocationManager mlocManager;
    private ImageView mImageView;
    private TextView startTrackingSign, startTrackingSign2 ;
    private FloatingActionButton mapmatchingicon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(_unCaughtExceptionHandler);
        Mapbox.getInstance(this, BuildConfig.access_token);
        setContentView(R.layout.activity_main);
                m_geoHashRTFilter = new GeohashRTFilter(Utils.GEOHASH_DEFAULT_PREC, Utils.GEOHASH_DEFAULT_MIN_POINT_COUNT);
        setupMap(savedInstanceState);

        mSettings = PreferenceManager.getDefaultSharedPreferences(this);
        btnStartStopTracking = (Button) findViewById(R.id.btnStartStopTracking);
        mImageView = (ImageView) findViewById(R.id.imageView2);
        mapmatchingicon =(FloatingActionButton) findViewById(R.id.mapmatchingicon);

        // Get all necessary elements
        mHeightText = (TextView) findViewById(R.id.textViewHeightO);
        mCalibrateIn = (EditText) findViewById(R.id.editTextHeightcal);
        levelOfUser = (TextView) findViewById(R.id.levelOfUser);
        startTrackingSign = (TextView) findViewById(R.id.startTrackingSign);
        startTrackingSign2 = (TextView) findViewById(R.id.startTrackingSign2);
        startTrackingSign2.setVisibility(View.GONE);
        mImageView.setImageResource(R.drawable.onlevel);

        mapmatchingicon.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                new LoadGeoJson(MainActivity.this).execute();
            }
        });

        btnStartStopTracking.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            public void onClick(View v) {
                set_isLogging(!m_isLogging); //clear
                int a = 14;
                Log.d("tracking", String.valueOf(a));
                set_isCalibrating(!m_isCalibrating, true);
                startLogger();
                startTrackingSign.setVisibility(View.GONE);
                startTrackingSign2.setVisibility(View.VISIBLE);
                mlocManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    Activity#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for Activity#requestPermissions for more details.
                    return;
                }
                lastLocation = mlocManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.lezh1k.locomotion.custom.intent.Callback");
        mReceiver = new MyReceiver();
        this.registerReceiver(mReceiver, filter);

        startService(new Intent(this, SensorService.class));
        // bind to it
        bindService(new Intent(this,
                SensorService.class), mConnection, Context.BIND_AUTO_CREATE);
        Log.d("isrunning", String.valueOf(isMyServiceRunning(SensorService.class)));



        if (mSettings.getBoolean(cPREF_DEBUG, false))
            Toast.makeText(MainActivity.this, R.string.debug_create_finished, Toast.LENGTH_SHORT).show();
        // we need access to SD-Card
        if (mSettings.getBoolean("mReqSDPermission", true))
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);


        CheckBox cbGps, cbFilteredKalman, cbFilteredKalmanGeo;
        cbGps = (CheckBox) findViewById(R.id.cbGPS);
        cbFilteredKalman = (CheckBox) findViewById(R.id.cbFilteredKalman);
        cbFilteredKalmanGeo = (CheckBox) findViewById(R.id.cbFilteredKalmanGeo);
        CheckBox cb[] = {cbFilteredKalman, cbFilteredKalmanGeo, cbGps};
        for (int i = 0; i < 3; ++i) {
            if (cb[i] == null)
                continue;
            cb[i].setBackgroundColor(ContextCompat.getColor(this, routeColors[i]));
        }

        File esd = Environment.getExternalStorageDirectory();
        String storageState = Environment.getExternalStorageState();
        if (storageState != null && storageState.equals(Environment.MEDIA_MOUNTED)) {
            xLogFolderPath = String.format("%s/%s/", esd.getAbsolutePath(), "SensorDataCollector");
            Printer androidPrinter = new AndroidPrinter();             // Printer that print the log using android.util.Log
            initXlogPrintersFileName();
            Printer xLogFilePrinter = new FilePrinter
                    .Builder(xLogFolderPath)
                    .fileNameGenerator(xLogFileNameGenerator)
                    .backupStrategy(new FileSizeBackupStrategy(1024 * 1024 * 100)) //100MB for backup files
                    .build();
            XLog.init(LogLevel.ALL, androidPrinter, xLogFilePrinter);
        } else {
            //todo set some status
        }

    }

    Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            try {
//                updateStatus(); //this function can change value of mInterval.
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                mHandler.postDelayed(mStatusChecker, mInterval);
            }
        }
    };

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent receive) {
            // set all output fields
            Float height = receive.getFloatExtra("Height", 997F);
            mHeightText.setText(String.format(Locale.getDefault(), "%.1f m", height));
            mRunning = receive.getBooleanExtra("Registered", false);

        }

    }

    @Override
    protected void onStart() {
        super.onStart();

        // Set preferences data
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        initActivity();
        if (m_mapView != null) {
            m_mapView.onStart();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (m_mapView != null) {
            m_mapView.onStop();
        }
    }


    ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceDisconnected(ComponentName name) {
            if (mSettings.getBoolean(cPREF_DEBUG, false))
                Toast.makeText(MainActivity.this, R.string.debug_service_disconnected, Toast.LENGTH_SHORT).show();
            mBounded = false;
            mSensService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            if (mSettings.getBoolean(cPREF_DEBUG, false))
                Toast.makeText(MainActivity.this, R.string.debug_service_connected, Toast.LENGTH_SHORT).show();
            mBounded = true;
            LocalBinder mLocalBinder = (LocalBinder) service;
            mSensService = mLocalBinder.getServerInstance();

            mSensService.getValues();
            getHeightRegulary();
            mHandler = new Handler();
//            startRepeatingTask();
        }
    };


    @Override
    protected void onResume() {
        super.onResume();
        if (m_mapView != null) {
            m_mapView.onResume();
        }
        m_refreshTask = new RefreshTask(1000, this);
        m_refreshTask.needTerminate = false;
        m_refreshTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        startTrackingSign2.setVisibility(View.GONE);

        if (mSensService != null) {
            mSensService.getValues();

            mSave = new SaveData(this);
            if (!(new CheckSDCard(this).checkWriteSDCard())) {
//                mFloatingButton.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, R.string.cant_write_sdcard, Toast.LENGTH_LONG).show();

            } else if (getDetailSave("m")) {

                    }

            }
        }


    @Override
    protected void onPause() {
        super.onPause();
        if (m_mapView != null) {
            m_mapView.onPause();
        }

        m_refreshTask.needTerminate = true;
        m_refreshTask.cancel(true);
        if (m_sensorCalibrator != null) {
            m_sensorCalibrator.stop();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (m_mapView != null) {
            m_mapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (m_mapView != null) {
            m_mapView.onLowMemory();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (m_mapView != null) {
            m_mapView.onDestroy();
        }
        if (mSensService != null) {
            unbindService(mConnection);
            unregisterReceiver(mReceiver);
            mBounded = false;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }


    // action for start button: starting measurement
    private void startLogger() {
        boolean succ = mSensService.startListeners();
        if(succ && mSettings.getBoolean(cPREF_DEBUG,false))
            Toast.makeText(MainActivity.this, R.string.debug_listener_started, Toast.LENGTH_SHORT).show();
    }

    // action for stop button: stopping measurement
    private void stopLogger() {
        /*
           Todo: Service could be stopped completely. Keeping service alive is a relict
                  from previous versions where I didn't have implemented the persistency.
                  Stopping of service would free memory ressources. But I have some features
                  which depend on running service even if no measurement is running.
                  (e.g. height calibration and reset)
         */

        /*
          Unregister sensors and save actual steps to evaluate pause steps later
        */
        if (mSensService != null) mSensService.stopListeners();
        if (mSettings.getBoolean(cPREF_DEBUG, false)) mStatusText.setText(R.string.sensor_pause);
    }

    public void resetData(View view) {
        // Todo: Rework necessary if service will be stopped
        //        at the moment reset can be done even without service
        if (mSensService != null) mSensService.resetData();
        mStepText.setText(R.string.zero);
        mHeightText.setText(R.string.height_init1);
        mHeightaccText.setText(R.string.zero_m);
        mStatusText.setText("");
        mCalibrateIn.setText("");
        mCalibrateIn.setHint(R.string.height_m);
    }

    public void calibrateLevel(View view) {
        // Todo: Rework necessary if service will be stopped
        //      Currently calibration is possible even if measurement is not started yet
        //       but we need a running service for this
        startTrackingSign2.setVisibility(View.GONE);
        if (mSensService != null) {
            float height = 0;
            int levelNow;
            try {
                levelNow = Integer.parseInt(mCalibrateIn.getText().toString());
            } catch (NumberFormatException nfe) {
                return;
            }
            //call Service calibrate
            mSensService.calibrateHeight(height);
            mSensService.calibrateLevel(levelNow);
            mHeightText.setText(String.format(Locale.getDefault(), "%.1f m", height));
            mCalibrateIn.getText().clear();
                mCalibrateIn.setFocusable(false);

        } else if (mSettings.getBoolean(cPREF_DEBUG, false))
            Toast.makeText(MainActivity.this, R.string.service_not_started, Toast.LENGTH_LONG).show();

        // Closing keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    // as long as service is running, we update height information regulary
    private void getHeightRegulary() {
        if (mSensService != null) {
            mHeightText.setText(String.format(Locale.getDefault(), "%.1f m", mSensService.getHeight()));
            String movement = mSensService.getMovement();
            getImage(movement);
//            movementOfUser.setText(mSensService.getMovement());
            levelOfUser.setText(mSensService.getLevel());
            Handler handler = new Handler();
            handler.postDelayed(this::getHeightRegulary, cINTERVAL_UPDATE_HEIGHT);

        }
    }

    private void getImage(String movement){
        if (movement == "You are taking the lift UP") {
            mImageView.setImageResource(R.drawable.liftup);
        }
        else if (movement == "You are taking the lift DOWN") {
            mImageView.setImageResource(R.drawable.liftdown);
            Log.d("Hi", "hi");
        }

        else if (movement == "You are on the same level") {
            mImageView.setImageResource(R.drawable.onlevel);
        }
        else if (movement == "You are walking DOWN") {
            mImageView.setImageResource(R.drawable.walkdown);
        }
        else if (movement == "You are walking UP") {
            mImageView.setImageResource(R.drawable.walkup);
        }
    }
    private boolean getDetailSave(String identifier){
        Set<String> detail_multi = mSettings.getStringSet(cPREF_STAT_DETAIL_MULTI, cPREF_STAT_DETAIL_MULTI_DEFAULT);

        boolean ret = false;
        for (String s:  detail_multi ) {
            ret = ret || s.equals(identifier);
        }
        return ret;
    }

}