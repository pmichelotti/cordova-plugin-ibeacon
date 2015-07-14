package com.unarin.cordova.beacon;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.os.Process;
import android.provider.Settings;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.Region;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;

public class IBeaconMonitorService extends Service implements BeaconConsumer {

    private BeaconManager mBeaconManager;
    private String deviceId;

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        deviceId = Settings.Secure.getString(this.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        new SendLocationEventTask().execute(new LocationEvent("startup", "dummy-location"));

        if (mBeaconManager == null) {
            mBeaconManager = BeaconManager.getInstanceForApplication(this);
            //TODO: Add parser for iBeacon?
            mBeaconManager.bind(this);
            //TODO: Lookup all prior Regions to monitor

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
                    //TODO: Spin this processing off to another thread
                    //TODO: what the heck are these IDs?
                    new SendLocationEventTask().execute(new LocationEvent(eventType, "dummy-location"));
                }
            });
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

    private class SendLocationEventTask extends AsyncTask<LocationEvent, Void, String> {

        @Override
        protected String doInBackground(LocationEvent... locationEvents) {
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
            throw new IllegalArgumentException("invalid url: " + endpoint);
        }

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
