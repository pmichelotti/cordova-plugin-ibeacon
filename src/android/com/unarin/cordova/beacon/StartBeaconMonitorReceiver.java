package com.unarin.cordova.beacon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StartBeaconMonitorReceiver extends BroadcastReceiver {

    public static final String TAG = "com.unarin.cordova.beacon";

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.i(TAG, "Received intent " + intent.getType() + " - attempting to start service");
        //Make sure the service is up and running and doing its thing
        context.startService(new Intent(context, IBeaconMonitorService.class));
        Log.i(TAG, "Service should be started");

    }

}
