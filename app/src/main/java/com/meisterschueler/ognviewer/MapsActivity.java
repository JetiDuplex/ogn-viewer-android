package com.meisterschueler.ognviewer;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.google.android.gms.location.LocationListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.ui.IconGenerator;
import com.meisterschueler.ognviewer.common.ReceiverBundle;
import com.meisterschueler.ognviewer.common.Utils;

import org.ogn.commons.beacon.AddressType;
import org.ogn.commons.beacon.AircraftBeacon;
import org.ogn.commons.beacon.AircraftDescriptor;
import org.ogn.commons.beacon.AircraftType;
import org.ogn.commons.beacon.ReceiverBeacon;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleMap.OnCameraMoveStartedListener, GoogleMap.OnCameraIdleListener {
    private OgnService ognService;
    private Circle rangeCircle;
    private BroadcastReceiver aircraftReceiver;
    private BroadcastReceiver receiverReceiver;
    private Map<String, Marker> aircraftMarkerMap = new HashMap<>();
    private Map<String, Marker> receiverMarkerMap = new HashMap<>();
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder binder) {
            OgnService.LocalBinder localBinder = (OgnService.LocalBinder) binder;
            ognService = localBinder.getService();
            for (final OgnService.AircraftBundle bundle : ognService.aircraftBundleMap.values()) {
                AircraftBeacon aircraftBeacon = bundle.aircraftBeacon;
                AircraftDescriptor aircraftDescriptor = bundle.aircraftDescriptor;
                updateAircraftBeaconMarker(aircraftBeacon, aircraftDescriptor);
            }

            for (final ReceiverBeacon receiver : ognService.receiverBundleMap.values()) {
                updateReceiverBeaconMarker(receiver);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            ognService = null;
        }
    };


    private BroadcastReceiver actionReceiver;
    private void updateKnownAircrafts(final Map<String, AircraftBundle> aircraftMap) {
        for (final AircraftBundle bundle : aircraftMap.values()) {
            updateAircraftBeaconMarker(bundle);
        }
    }

    private void updateKnownReceivers(final Map<String, ReceiverBundle> receiverMap) {
        for (final ReceiverBundle bundle : receiverMap.values()) {
            updateReceiverBeaconMarker(bundle);
        }
    }

    boolean mapUpdating = false;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final int id = item.getItemId();

        switch (id) {
            case R.id.action_settings:
                Intent i = new Intent(this, PrefsActivity.class);
                startActivityForResult(i, 2); //TODO: replace 2 with constant

                Boolean showreceivers = sharedPreferences.getBoolean(getString(R.string.key_showreceivers_preference), true);
                for (Marker m : receiverMarkerMap.values()) {
                    m.setVisible(showreceivers);
                }

                break;
            case R.id.action_manageids:
                Intent i2 = new Intent(this, ManageIDsActivity.class);
                startActivity(i2);
                break;
            case R.id.action_currentlocation:
                LocationManager locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

                LocationListener locationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

                        CameraPosition cameraPosition = new CameraPosition.Builder()
                                .target(latLng)
                                .zoom(7)
                                .build();
                        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                    }
                };

                Location location = locManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (location != null) {
                    locationListener.onLocationChanged(location);
                } else {
                    //locManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, null);
                }

                break;

            case R.id.action_about:
                String versionName = "";
                try {
                    versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }

                AlertDialog alertDialog = new AlertDialog.Builder(MapsActivity.this).create();
                alertDialog.setTitle("About");
                alertDialog.setMessage("OGN Viewer " + versionName);
                alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //asdf;
                    }
                });
                alertDialog.show();
                break;
            case R.id.action_exit:
                stopService(new Intent(getBaseContext(), OgnService.class));
                LocalBroadcastManager.getInstance(this).unregisterReceiver(aircraftReceiver); //TODO: also unregister other services?
                finish();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        // check if the request code is same as what is passed  here it is 2
        if(requestCode==2)
        {
            String message=data.getStringExtra("MESSAGE");
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String aprsFilter = sharedPreferences.getString(getString(R.string.key_aprsfilter_preference), "");
            updateAprsFilterRange(aprsFilter);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.fragment_maps);
        checkSetUpMap();


        aircraftReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // AircraftBeacon
                String receiverName = intent.getStringExtra("receiverName");
                AddressType addressType = AddressType.forValue(intent.getIntExtra("addressType", 0));
                String address = intent.getStringExtra("address");
                AircraftType aircraftType = AircraftType.forValue(intent.getIntExtra("aircraftType", 0));
                boolean stealth = intent.getBooleanExtra("stealth", false);
                float climbRate = intent.getFloatExtra("climbRate", 0);
                float turnRate = intent.getFloatExtra("turnRate", 0);
                float signalStrength = intent.getFloatExtra("signalStrength", 0);
                float frequencyOffset = intent.getFloatExtra("frequencyOffset", 0);
                String gpsStatus = intent.getStringExtra("gpsStatus");
                int errorCount = intent.getIntExtra("errorCount", 0);
                //String[] getHeardAircraftIds();

                // OgnBeacon
                String id = intent.getStringExtra("id");
                long timestamp = intent.getLongExtra("timestamp", 0);
                double lat = intent.getDoubleExtra("lat", 0);
                double lon = intent.getDoubleExtra("lon", 0);
                float alt = intent.getFloatExtra("alt", 0);
                int track = intent.getIntExtra("track", 0);
                float groundSpeed = intent.getFloatExtra("groundSpeed", 0);
                String rawPacket = intent.getStringExtra("rawPacket");

                // AircraftDescriptor
                boolean known = intent.getBooleanExtra("known", false);
                String regNumber = intent.getStringExtra("regNumber");
                String CN = intent.getStringExtra("CN");
                String owner = intent.getStringExtra("owner");
                String homeBase = intent.getStringExtra("homeBase");
                String model = intent.getStringExtra("model");
                String freq = intent.getStringExtra("freq");
                boolean tracked = intent.getBooleanExtra("tracked", false);
                boolean identified = intent.getBooleanExtra("identified", false);

                boolean isOgnPrivate = known && (!tracked || !identified);
                if (!isOgnPrivate) {
                    updateAircraftBeaconMarker(address, aircraftType, climbRate, lat, lon, alt, (int) groundSpeed, regNumber, CN, model);
                }
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver((aircraftReceiver), new IntentFilter("AIRCRAFT-BEACON"));

        receiverReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // ReceiverBeacon
                float recInputNoise = intent.getFloatExtra("recInputNoise", 0);
                String version = intent.getStringExtra("version");
                String platform = intent.getStringExtra("platform");
                int numericVersion = intent.getIntExtra("numericVersion", 0);

                // OgnBeacon
                String id = intent.getStringExtra("id");
                long timestamp = intent.getLongExtra("timestamp", 0);
                double lat = intent.getDoubleExtra("lat", 0);
                double lon = intent.getDoubleExtra("lon", 0);
                float alt = intent.getFloatExtra("alt", 0);

                // Computed values
                int aircraftCounter = intent.getIntExtra("aircraftCounter", 0);
                int maxAircraftCounter = intent.getIntExtra("maxAircraftCounter", 0);

                int beaconCounter = intent.getIntExtra("beaconCounter", 0);
                int maxBeaconCounter = intent.getIntExtra("maxBeaconCounter", 0);
                if (lat != 0 && lon != 0)
                updateReceiverBeaconMarkerOnMap(id, lat, lon, alt, recInputNoise);
            }
        };

        LocalBroadcastManager.getInstance(this).registerReceiver((receiverReceiver), new IntentFilter("RECEIVER-BEACON"));

        //action receiver
        actionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getStringExtra("AIRCRAFT_ACTION");
                if (action.equals("REMOVE_AIRCRAFT")) {
                    String address = intent.getStringExtra("address");
                    if (address != null) {
                        removeAircraftFromMap(address);
                    }
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver((actionReceiver), new IntentFilter("AIRCRAFT_ACTION"));


        if (savedInstanceState == null) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String aprsFilter = sharedPreferences.getString(getString(R.string.key_aprsfilter_preference), "");
            if (aprsFilter.equals("")) {
                LocationManager locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (locManager != null) {
                    Location location = locManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    if (location != null) {
                        aprsFilter = AprsFilterManager.latLngToAprsFilter(location.getLatitude(), location.getLongitude());
                    }
                }
                editEmptyAprsFilter(aprsFilter);

            } else {
                startService(new Intent(getBaseContext(), OgnService.class));
            }
        }
    }

    private void updateReceiverBeaconMarker(ReceiverBeacon receiver) {
        String receiverName = receiver.getId();
        double lat = receiver.getLat();
        double lon = receiver.getLon();
        float alt = receiver.getAlt();
        float recInputNoise = receiver.getRecInputNoise();

        updateReceiverBeaconMarkerOnMap(receiverName, lat, lon, alt, recInputNoise);
    private void updateReceiverBeaconMarker(ReceiverBundle bundle) {
        ReceiverBeacon beacon = bundle.receiverBeacon;
        updateReceiverBeaconMarker(beacon.getId(), beacon.getLat(), beacon.getLon(), beacon.getAlt(), beacon.getRecInputNoise(), bundle.aircrafts.size(), ReceiverBundle.maxAircraftCounter, bundle.beaconCount, ReceiverBundle.maxBeaconCounter);
    }

    private void updateReceiverBeaconMarkerOnMap(String receiverName, double lat, double lon, float altitude, float recInputNoise) {
        Marker m;
        boolean infoWindowShown = false;
        if (!receiverMarkerMap.containsKey(receiverName)) {
            m = mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lon)));
            receiverMarkerMap.put(receiverName, m);
        } else {
            m = receiverMarkerMap.get(receiverName);
            infoWindowShown = m.isInfoWindowShown();
            m.setPosition(new LatLng(lat, lon));
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Boolean showReceivers = sharedPreferences.getBoolean(getString(R.string.key_showreceivers_preference), false);
        Boolean isActive = (sharedPreferences.getBoolean(getString(R.string.key_shownotactive_preference), true) || aircraftCounter > 0 || beaconCounter > 0);

        m.setVisible(showReceivers && isActive);

        String title = receiverName + " (" + alt + "m)";
        String content = "Aircrafts: " + aircraftCounter + ", Beacons: " + beaconCounter;

        m.setTitle(title);
        m.setSnippet(content);

        float hue;
        String colorisation = sharedPreferences.getString(getString(R.string.key_receiver_colorisation_preference), getString(R.string.aircraft_count));
        if (colorisation.equals(getString(R.string.aircraft_count))) {
            hue = Utils.getHue(aircraftCounter, 0, maxAircraftCounter, 0, 270);
        } else if (colorisation.equals(getString(R.string.beacon_count))) {
            hue = Utils.getHue(beaconCounter, 0, maxBeaconCounter, 0, 270);
        } else {
            hue = Utils.getHue(alt, 0, 3000, 0, 270);
        }

        //m.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        IconGenerator iconGenerator = new IconGenerator(this);
        iconGenerator.setContentPadding(0, 0, 0, 0);
        Bitmap icon = iconGenerator.makeIcon(receiverName);

        int iconMinSize = 72;   // sufficient for "808"
        int delta = Math.max(0, iconMinSize - icon.getWidth());
        iconGenerator.setContentPadding(delta / 2, 0, delta / 2, 0);
        iconGenerator.setColor(Color.HSVToColor(new float[]{hue, (float)255, (float)255}));
        iconGenerator.setTextAppearance(R.style.TextColorBlack);
        icon = iconGenerator.makeIcon(receiverName);

        m.setIcon(BitmapDescriptorFactory.fromBitmap(icon));

        // (re)open the infoWindow
        if (infoWindowShown) {
            m.showInfoWindow();
        }
    }

    private void updateAircraftBeaconMarker(AircraftBundle bundle) {
        AircraftBeacon aircraftBeacon = bundle.aircraftBeacon;
        AircraftDescriptor aircraftDescriptor = bundle.aircraftDescriptor;

        boolean isOgnPrivate = aircraftDescriptor.isKnown() && (!aircraftDescriptor.isTracked() || !aircraftDescriptor.isIdentified());
        if (!isOgnPrivate) {
        }
        updateAircraftBeaconMarkerOnMap(aircraftBeacon.getAddress(), aircraftBeacon.getAircraftType(),
                aircraftBeacon.getClimbRate(), aircraftBeacon.getLat(), aircraftBeacon.getLon(),
                aircraftBeacon.getAlt(), aircraftBeacon.getGroundSpeed(), aircraftDescriptor.getRegNumber(),
                aircraftDescriptor.getCN(), aircraftDescriptor.getModel(), isOgnPrivate, aircraftBeacon.getReceiverName(),
                aircraftBeacon.getTrack());
    }




    private void pauseUpdatingMap() {
        //mapTimer.cancel();
        //mapTimer.purge();
        if (ognService != null) {
            ognService.pauseUpdatingMap();
        }
    }

    private void updateAircraftBeaconMarkerOnMap(String address, AircraftType aircraftType, float climbRate,
                                            double lat, double lon, float alt, float groundSpeed,
                                            String regNumber, String CN, String model, boolean isOgnPrivate,
                                            String receiverName, int track) {
        ognService.mapUpdatingStatus(true);
        Marker m;
        boolean infoWindowShown = false;

        System.out.println("updateMarker for address: " + address); //debug

        if (!aircraftMarkerMap.containsKey(address)) {
            m = mMap.addMarker(new MarkerOptions().position(new LatLng(lat, lon)));
            aircraftMarkerMap.put(address, m);
        } else {
            m = aircraftMarkerMap.get(address);

            infoWindowShown = m.isInfoWindowShown();
            m.setPosition(new LatLng(lat, lon));
        }
        m.setRotation(track + 180); //with 180 the pin shows to north on 0 degree from track

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String colorisation = sharedPreferences.getString(getString(R.string.key_aircraft_colorisation_preference), getString(R.string.altitude));
        Boolean showaircrafts = sharedPreferences.getBoolean(getString(R.string.key_showaircrafts_preference), true);
        Boolean shownonmoving = sharedPreferences.getBoolean(getString(R.string.key_shownonmoving_preference), true);
        Boolean showregistration = sharedPreferences.getBoolean(getString(R.string.key_showregistration_preference), true);

        if (!showaircrafts || !shownonmoving && groundSpeed < 5) {
            m.setVisible(false);
            ognService.mapUpdatingStatus(false);
            return;
        } else {
            m.setVisible(true);
        }


        // make snippet
        String title;
        if (regNumber != null && !regNumber.isEmpty()) {
            title = regNumber;
            if (model != null && !model.isEmpty()) {
                title += " (" + model + ")";
            }
        } else {
            title = address;
        }
        String content = String.format(Locale.US,"alt:%d gs:%d, vs:%.1f",  (int) alt, (int) groundSpeed, climbRate);
        m.setSnippet("alt:" + (int) alt + " gs:" + groundSpeed + " vs:" + String.format("%.1f", climbRate)
        + " Receiver:" + receiverName);

        m.setTitle(title);
        m.setSnippet(content);


        // make color of the marker
        float hue = 0;
        int color = Color.rgb(255, 255, 255);
        if (colorisation.equals(getString(R.string.altitude))) {
            final float minAlt = 500.0f;
            final float maxAlt = 3000.0f;
            hue = Utils.getHue(alt, minAlt, maxAlt, 0, 270);
        } else if (colorisation.equals(getString(R.string.speed))) {
            final float minSpeed = 50.0f;
            final float maxSpeed = 285.0f;
            hue = Utils.getHue(groundSpeed, minSpeed, maxSpeed, 0, 270);
        } else if (colorisation.equals(getString(R.string.aircraft_type))) {
            switch (aircraftType) {
                //case UNKNOWN:
                //    break;
                case GLIDER:
                    hue = BitmapDescriptorFactory.HUE_YELLOW;
                    color = Color.rgb(252, 245, 70);
                    break;
                case TOW_PLANE:
                    hue = BitmapDescriptorFactory.HUE_GREEN;
                    color = Color.rgb(35, 249, 13);
                    break;
                case HELICOPTER_ROTORCRAFT:
                    hue = BitmapDescriptorFactory.HUE_RED;
                    color = Color.rgb(240, 72, 52);
                    break;
                //case PARACHUTE:
                //    break;
                //case DROP_PLANE:
                //    break;
                //case HANG_GLIDER:
                //    break;
                case PARA_GLIDER:
                    hue = BitmapDescriptorFactory.HUE_MAGENTA;  //Pink?
                    color = Color.rgb(254, 191, 193);
                    break;
                //case POWERED_AIRCRAFT:
                //    break;
                //case JET_AIRCRAFT:
                //    break;
                //case UFO:
                //    break;
                //case BALLOON:
                //    break;
                //case AIRSHIP:
                //    break;
                //case UAV:
                //    break;
                //case STATIC_OBJECT:
                //    break;
                default:
                    hue = BitmapDescriptorFactory.HUE_BLUE;
                    color = Color.rgb(25, 159, 238);

                    // gray:
                    // color = Color.rgb(218, 218, 208);
            }
        }


        // make icon
        if (!showregistration || ((regNumber == null || regNumber.isEmpty()) && (CN == null || CN.isEmpty()))) {
            if (m == null) { ///why is this sometimes true?
                ognService.mapUpdatingStatus(false);
                return;
            }
            m.setIcon(BitmapDescriptorFactory.defaultMarker(hue)); //CAUTION: very slow process!
            //m.setIcon(BitmapDescriptorFactory.defaultMarker()); //TODO: fix this workaround
        } else {
            if (CN == null || CN.isEmpty()) {
                if (regNumber.length() > 1) {
                    title = regNumber.substring(regNumber.length() - 2, regNumber.length());
                } else {
                    title = "?";
                }
            } else {
                title = CN;
            }

            IconGenerator iconGenerator = new IconGenerator(this);
            iconGenerator.setContentPadding(0, 0, 0, 0);
            Bitmap icon = iconGenerator.makeIcon(title);

            int iconMinSize = 72;   // sufficient for "808"
            int delta = Math.max(0, iconMinSize - icon.getWidth());
            iconGenerator.setContentPadding(delta / 2, 0, delta / 2, 0);
            iconGenerator.setColor(Color.HSVToColor(new float[]{hue, 255, 255}));
            iconGenerator.setTextAppearance(R.style.TextColorBlack);
            icon = iconGenerator.makeIcon(title);

            if (m == null) { //why is this sometimes true?
                ognService.mapUpdatingStatus(false);
                return;
            }
            m.setIcon(BitmapDescriptorFactory.fromBitmap(icon));
        }


        // (re)open the infoWindow
        if (infoWindowShown) {
            m.showInfoWindow();
        }
        ognService.mapUpdatingStatus(false);
        System.out.println("updatedMarker for address: " + address); //debug
    }

    private void removeAircraftFromMap(String address) {
        if (aircraftMarkerMap.containsKey(address)) {
            Marker m;
            m = aircraftMarkerMap.get(address);
            if (m == null) {
                //continue;
                return;
            }
            m.remove(); //remove marker from mMap
            aircraftMarkerMap.remove(address);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(mConnection);

        // Save current lat, lon, zoom
        if (mMap != null) {
            float lat = (float) mMap.getCameraPosition().target.latitude;
            float lon = (float) mMap.getCameraPosition().target.longitude;
            float zoom = mMap.getCameraPosition().zoom;
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            sharedPreferences.edit().putFloat(getString(R.string.key_latitude_preference), lat).commit();
            sharedPreferences.edit().putFloat(getString(R.string.key_longitude_preference), lon).commit();
            sharedPreferences.edit().putFloat(getString(R.string.key_zoom_preference), zoom).commit();
        }

        pauseUpdatingMap();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindService(new Intent(this, OgnService.class), mConnection, Context.BIND_AUTO_CREATE);

        checkSetUpMap();
        //resumeUpdatingMap(); //start timer for updating map

    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    private void checkSetUpMap() {
        if (mMap == null) {
            SupportMapFragment suppMapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
            suppMapFragment.getMapAsync(this);
        }


    }

    private void setUpMap() {
        mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
        mMap.getUiSettings().setMapToolbarEnabled(false);

        mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {

                if (receiverMarkerMap.containsValue(marker))
                    return;

                String address = "";
                for (Map.Entry<String, Marker> entry : aircraftMarkerMap.entrySet()) {
                    if (entry.getValue().equals(marker)) {
                        address = entry.getKey();
                        break;
                    }
                }

                AircraftDialog.showDialog(MapsActivity.this, address);
            }
        });

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                String aprsFilter = AprsFilterManager.latLngToAprsFilter(latLng.latitude, latLng.longitude);
                editAprsFilter(aprsFilter);
            }
        });
    }

    private void editAprsFilter(final String aprsFilter) {
        View view = getLayoutInflater().inflate(R.layout.dialog_aprsfilter, null);
        final EditText et = view.findViewById(R.id.editTextOwner);
        et.setText(aprsFilter);

        new AlertDialog.Builder(this).setView(view)
                .setTitle(R.string.aprs_filter_title)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        String aprsFilterModified = et.getText().toString();

                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        String aprsFilterSaved = sharedPreferences.getString(getString(R.string.key_aprsfilter_preference), "");

                        if (!aprsFilterModified.equals(aprsFilterSaved)) {
                            sharedPreferences.edit().putString(getString(R.string.key_aprsfilter_preference), aprsFilterModified).apply();
                            startService(new Intent(getBaseContext(), OgnService.class));
                            updateAprsFilterRange(aprsFilterModified);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // do nothing
                    }
                })
                .show();
    }

    private void editEmptyAprsFilter(final String aprsFilter) {
        View view = getLayoutInflater().inflate(R.layout.dialog_aprsfilter, null);
        final EditText et = view.findViewById(R.id.editTextOwner);
        et.setText(aprsFilter);

        new AlertDialog.Builder(this).setView(view)
                .setTitle(R.string.aprs_filter_title)
                .setMessage(R.string.empty_aprs_filter_message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        String aprsFilterModified = et.getText().toString();

                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        sharedPreferences.edit().putString(getString(R.string.key_aprsfilter_preference), aprsFilterModified).apply();
                        startService(new Intent(getBaseContext(), OgnService.class));
                        updateAprsFilterRange(aprsFilterModified);
                    }
                })
                .show();
    }

    private void updateAprsFilterRange(String aprsFilter) {
        if (rangeCircle == null) {
            rangeCircle = mMap.addCircle(new CircleOptions().center(new LatLng(0, 0)).radius(1).strokeColor(Color.RED));
        }
        rangeCircle.setVisible(false);

        AprsFilterManager.Circle circle = AprsFilterManager.parse(aprsFilter);
        if (circle != null) {
            rangeCircle.setCenter(new LatLng(circle.lat, circle.lon));
            rangeCircle.setRadius(circle.radius * 1000);
            rangeCircle.setVisible(true);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (mMap != null) return; //use first loaded map only
        mMap = googleMap;
        mMap.getUiSettings().setRotateGesturesEnabled(false); //do not allow rotating the map
        mMap.setOnCameraIdleListener(this);
        mMap.setOnCameraMoveStartedListener(this);
        setUpMap();

        // Restore lat, lon, zoom
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        float lat = sharedPreferences.getFloat(getString(R.string.key_latitude_preference), 0.0f);
        float lon = sharedPreferences.getFloat(getString(R.string.key_longitude_preference), 0.0f);
        float zoom = sharedPreferences.getFloat(getString(R.string.key_zoom_preference), 2.0f);
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(new LatLng(lat, lon))
                .zoom(zoom)
                .build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));


        //SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String aprsFilter = sharedPreferences.getString(getString(R.string.key_aprsfilter_preference), "");
        updateAprsFilterRange(aprsFilter);
    }

    @Override
    public void onCameraMoveStarted(int i) {
        pauseUpdatingMap();
    }

    @Override
    public void onCameraIdle() {
        //resumeUpdatingMap(); //reconnect service?
        if (ognService != null) {
            ognService.resumeUpdatingMap(mMap.getProjection().getVisibleRegion().latLngBounds);
        }
    }
}
