package com.example.lezh1k.locomotion;
import android.content.ContentValues;
import android.content.Context;
//import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.util.Log;

//import com.orderssupportapp.database.dbContract;
//import com.orderssupportapp.database.dbHelper;

import mad.location.manager.lib.Filters.GeoHash;

public class CGeohashRTFilter {
    private static final double COORD_NOT_INITIALIZED = 361.0;
    private int ppCompGeoHash = 0;
    private int ppReadGeoHash = 1;

    private long geoHashBuffers[];
    private int pointsInCurrentGeohashCount;

    private CGeoPoint currentGeoPoint;

    private boolean isFirstCoordinate = true;

    private int m_geohashPrecision;
    private int m_geohashMinPointCount;

//    private dbHelper mDbHelper;
//    private SQLiteDatabase db;
//    private SimpleDateFormat dateFormat;

    public CGeohashRTFilter(Context context, int geohashPrecision, int geohashMinPointCount) {
        m_geohashPrecision = geohashPrecision;
        m_geohashMinPointCount = geohashMinPointCount;

//        mDbHelper = new dbHelper( context );
//        db = mDbHelper.getWritableDatabase();
//        dateFormat = new SimpleDateFormat("yyyyMMddHHmmss"); //"dd.MM.yyyy HH:mm:ss");

        reset();
    }


    public void reset() {
        geoHashBuffers = new long[2];
        pointsInCurrentGeohashCount = 0;
        currentGeoPoint = new CGeoPoint(COORD_NOT_INITIALIZED, COORD_NOT_INITIALIZED, 0);

        isFirstCoordinate = true;
    }

    public void filter(Location loc) {

        CGeoPoint pi = new CGeoPoint(loc.getLatitude(), loc.getLongitude(), loc.getSpeed());
        if (isFirstCoordinate) {
            geoHashBuffers[ppCompGeoHash] = GeoHash.encode_u64(pi.Latitude, pi.Longitude, m_geohashPrecision);
            currentGeoPoint.Latitude = pi.Latitude;
            currentGeoPoint.Longitude = pi.Longitude;
            currentGeoPoint.Velocity = pi.Velocity;
            pointsInCurrentGeohashCount = 1;


            isFirstCoordinate = false;
            return;
        }

        geoHashBuffers[ppReadGeoHash] = GeoHash.encode_u64(pi.Latitude, pi.Longitude, m_geohashPrecision);
        if (geoHashBuffers[ppCompGeoHash] != geoHashBuffers[ppReadGeoHash]) {
            if (pointsInCurrentGeohashCount >= m_geohashMinPointCount) {

                ContentValues values = new ContentValues();

//                values.put( dbContract.gpsEntry.COLUMN_USED, 0 );
//                values.put( dbContract.gpsEntry.COLUMN_DATE, dateFormat.format(loc.getTime()).toString() );
//                values.put( dbContract.gpsEntry.COLUMN_LATITUDE, currentGeoPoint.Latitude/pointsInCurrentGeohashCount );
//                values.put( dbContract.gpsEntry.COLUMN_LONGITUDE, currentGeoPoint.Longitude/pointsInCurrentGeohashCount );
//                values.put( dbContract.gpsEntry.COLUMN_VELOCITY, currentGeoPoint.Velocity/pointsInCurrentGeohashCount );
//
//                long newRowId = db.insert( dbContract.gpsEntry.TABLE_NAME, null, values );
            }

            pointsInCurrentGeohashCount = 1;
            currentGeoPoint.Latitude = pi.Latitude;
            currentGeoPoint.Longitude = pi.Longitude;
            currentGeoPoint.Velocity = pi.Velocity;
            //swap buffers
            int swp = ppCompGeoHash;
            ppCompGeoHash = ppReadGeoHash;
            ppReadGeoHash = swp;
            return;
        }

        currentGeoPoint.Latitude += pi.Latitude;
        currentGeoPoint.Longitude += pi.Longitude;
        currentGeoPoint.Velocity += pi.Velocity;

        ++pointsInCurrentGeohashCount;
    }

    public void stop() {
        if (pointsInCurrentGeohashCount >= m_geohashMinPointCount) {

            ContentValues values = new ContentValues();

//            values.put( dbContract.gpsEntry.COLUMN_USED, 0 );
//            values.put( dbContract.gpsEntry.COLUMN_DATE, dateFormat.format(new Date()).toString() );
//            values.put( dbContract.gpsEntry.COLUMN_LATITUDE, currentGeoPoint.Latitude/pointsInCurrentGeohashCount );
//            values.put( dbContract.gpsEntry.COLUMN_LONGITUDE, currentGeoPoint.Longitude/pointsInCurrentGeohashCount );
//            values.put( dbContract.gpsEntry.COLUMN_VELOCITY, currentGeoPoint.Velocity/pointsInCurrentGeohashCount );
//
//            long newRowId = db.insert( dbContract.gpsEntry.TABLE_NAME, null, values );

            currentGeoPoint.Velocity = 0;
            currentGeoPoint.Latitude = currentGeoPoint.Longitude = 0.0;
        }
    }
}
