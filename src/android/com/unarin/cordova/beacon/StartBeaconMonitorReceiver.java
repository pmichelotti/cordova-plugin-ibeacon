package com.unarin.cordova.beacon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StartBeaconMonitorReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        //Make sure the service is up and running and doing its thing
        context.startService(new Intent(context, IBeaconMonitorService.class));

    }

}
