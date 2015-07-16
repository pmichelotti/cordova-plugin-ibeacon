package com.unarin.cordova.beacon;

import android.provider.BaseColumns;

public final class BeaconRegionContract {

    public BeaconRegionContract() {}

    public static abstract class RegionEntry implements BaseColumns {
        public static final String TABLE_NAME = "regionentry";
        public static final String BEACON_ID = "beaconid";
        public static final String REGION_ID = "regionid";
    }
}
