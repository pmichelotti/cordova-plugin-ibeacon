package com.unarin.cordova.beacon;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import org.altbeacon.beacon.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;

public class IBeaconMonitorService extends Service implements BeaconConsumer {

    public static final String TAG = "com.unarin.cordova.beacon";

    public static final String REGION_EXTRA = "com.unarin.cordova.beacon.IBeaconMonitorService.REGION";

    private BeaconManager mBeaconManager;
    private String deviceId;
    private BeaconRegionDbHelper beaconRegionDbHelper;

    @Override
    public void onCreate() {
        Log.d(TAG, "Creating IBeaconMonitorService");
        deviceId = Settings.Secure.getString(this.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        beaconRegionDbHelper = new BeaconRegionDbHelper(this);
        Log.d(TAG, "Created IBeaconMonitorService for device " + deviceId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (mBeaconManager == null) {
            mBeaconManager = BeaconManager.getInstanceForApplication(this);
            //TODO: Add parser for iBeacon?
            mBeaconManager.bind(this);
            new StartMonitoringStoredRegionsTask().execute();

            mBeaconManager.setMonitorNotifier(new MonitorNotifier() {
                @Override
                public void didEnterRegion(Region region) {
                    sendLocationUpdate(region, "enter");
                }

                @Override
                public void didExitRegion(Region region) {
                    sendLocationUpdate(region, "exit");
                }

                @Override
                public void didDetermineStateForRegion(int i, Region region) {
                    //TODO: Verify whether we care what happens here
                }

                private void sendLocationUpdate(Region region, String eventType) {
                    //Note - Unique ID is the text ID provided on beacon registration, ID1 is the UUID, ID2 is the Major ID, ID3 is the Minor ID
                    Log.d(TAG, "Sending event for region " + region.getId1() + " - " + region.getId2() + " - " + region.getId3());
                    new SendLocationEventTask().execute(new LocationEvent(eventType, region.getUniqueId()));
                }
            });
        }

        if (intent != null && intent.hasExtra(REGION_EXTRA)) {
            Region regionToMonitor = (Region) intent.getParcelableExtra(REGION_EXTRA);
            Log.d(TAG, "Region extra included for region " + regionToMonitor.getUniqueId());
            try {
                mBeaconManager.startMonitoringBeaconsInRegion(regionToMonitor);
                new StoreRegionTask().execute(regionToMonitor);
            } catch (RemoteException e) {
                Log.e(TAG, "Monitoring failed for region " + regionToMonitor.getUniqueId() + " - " + e.getCause());
            }
        }

        return START_STICKY;

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onBeaconServiceConnect() {

    }

    private class StartMonitoringStoredRegionsTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... voids) {
            Log.d(TAG, "Retrieving regions from SQLite");
            SQLiteDatabase db = beaconRegionDbHelper.getReadableDatabase();
            Cursor resultCursor = db.query(
                    BeaconRegionContract.RegionEntry.TABLE_NAME,
                    new String[] {BeaconRegionContract.RegionEntry.BEACON_ID, BeaconRegionContract.RegionEntry.REGION_ID},
                    null,
                    null,
                    null,
                    null,
                    null
            );

            while (resultCursor.moveToNext()) {
                String currentBeaconId = resultCursor.getString(resultCursor.getColumnIndexOrThrow(BeaconRegionContract.RegionEntry.BEACON_ID));
                String currentRegionId = resultCursor.getString(resultCursor.getColumnIndexOrThrow(BeaconRegionContract.RegionEntry.REGION_ID));
                Log.d(TAG, "Reinitializing monitoring of region " + currentRegionId + " based on database contents");
                try {
                    mBeaconManager.startMonitoringBeaconsInRegion(new Region(currentRegionId, Identifier.parse(currentBeaconId), null, null));
                } catch (RemoteException e) {
                    Log.e(TAG, "Monitoring failed for region " + currentRegionId + " during database lookup - " + e.getCause());
                }
            }
            resultCursor.close();

            return null;
        }

    }

    private class StoreRegionTask extends AsyncTask<Region, Void, String> {

        @Override
        protected String doInBackground(Region... regions) {
            Log.d(TAG, "Storing region " + regions[0].getId1() + " to SQLite");
            SQLiteDatabase db = beaconRegionDbHelper.getWritableDatabase();
            ContentValues regionValues = new ContentValues();
            regionValues.put(BeaconRegionContract.RegionEntry._ID, regions[0].getId1().toString());
            regionValues.put(BeaconRegionContract.RegionEntry.BEACON_ID, regions[0].getId1().toString());
            regionValues.put(BeaconRegionContract.RegionEntry.REGION_ID, regions[0].getUniqueId());
            long insertResult = db.insert(BeaconRegionContract.RegionEntry.TABLE_NAME, null, regionValues);

            if (insertResult == -1L) {
                Log.i(TAG, "Unable to insert row for Beacon " + regions[0].getUniqueId() + " - this beacon probably already exists in the database");
            }
            Log.d(TAG, "Completed storing region " + regions[0].getId1().toString());
            return null;
        }

    }

    private class SendLocationEventTask extends AsyncTask<LocationEvent, Void, String> {

        @Override
        protected String doInBackground(LocationEvent... locationEvents) {
            Log.d(TAG, "Starting processing of location event for " + locationEvents[0].getLocation());
            try {
                postJson("http://circuit-2015-services-p.elasticbeanstalk.com/devices/" + deviceId + "/locationEvents", "{ \"location\": \"" + locationEvents[0].getLocation() + "\", \"type\": \"" + locationEvents[0].getType() + "\", \"occurredAt\": " + locationEvents[0].getTimestamp() + " }");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

    }

    private static void postJson(String endpoint, String json) throws IOException {
        URL url;
        try {
            url = new URL(endpoint);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Malformed URL Exception encountered for url " + endpoint, e);
            throw new IllegalArgumentException("invalid url: " + endpoint);
        }

        Log.d(TAG, "About to send " + json + " to " + endpoint);

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setChunkedStreamingMode(0);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type",
                    "application/json;charset=UTF-8");
            conn.setRequestProperty("Content-Length",
                    Integer.toString(json.getBytes().length));
            // post the request
            OutputStream out = conn.getOutputStream();
            out.write(json.getBytes());
            out.close();
            // handle the response
            int status = conn.getResponseCode();
            if (status == 500) {
                throw new IOException("Post failed with error code " + status);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static class LocationEvent {
        private final String type;
        private final String location;
        private final long timestamp;

        private LocationEvent(String type, String location) {
            this.type = type;
            this.location = location;
            Calendar calendar = Calendar.getInstance();
            timestamp = calendar.getTimeInMillis();
        }

        public String getType() {
            return type;
        }

        public String getLocation() {
            return location;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }



}
