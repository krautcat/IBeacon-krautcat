package localhost.krautcat.ibeacon.activities;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import localhost.krautcat.ibeacon.R;

public class MapViewActivity extends AppCompatActivity implements BeaconConsumer {

    protected static final String TAG = "MonitoringActivity";
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private ArrayList<Beacon> beaconsList;
    private BeaconManager beaconManager;
    private Beacon beacon;
    private Region region;

    private ArrayList<Double> ListDistances;

    private View dotView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_view);

        // создаем тулбар
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_mapview);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(getTitle());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // конфигурируем BeaconManager.
        beaconManager = BeaconManager.getInstanceForApplication(this);
        // добавляем парсер iBeacon'ов
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24"));
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
        beaconManager.bind(this);
        // массив beacons
        beaconsList = new ArrayList<>();
        ListDistances = new ArrayList<>();

        dotView = findViewById(R.id.dot);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (beaconManager.isBound(this)) beaconManager.setBackgroundMode(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (beaconManager.isBound(this)) beaconManager.setBackgroundMode(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.unbind(this);
    }

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                int i = 0;
                if (beacons.size() > 0) {
                    //EditText editText = (EditText)RangingActivity.this.findViewById(R.id.rangingText);
                    Iterator<Beacon> bufIt = beacons.iterator();
                    while ( i < beacons.size() ) {
                        Beacon bufBeacon = bufIt.next();
                        logToDisplay("The " + (i+1) + " beacon " + bufBeacon.getId1() + " is about " + bufBeacon.getDistance() + " meters away.");
                        i++;
                    }
                }

                beaconsList.clear();
                beaconsList.addAll(beacons);
                setListDistances(beaconsList);
            }

        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("0x00000000000000005545600000000000", null, null, null));
        } catch (RemoteException e) {   }
    }

    public void setListDistances (ArrayList<Beacon> beaconsList) {
        int size = beaconsList.size();
        for ( int i = 0; i < size; i++) {
            Double l = beaconsList.get(i).getDistance();
            ListDistances.add(l);
        }
    }


    private void logToDisplay(final String line) {
        runOnUiThread(new Runnable() {
            public void run() {
                EditText editText = (EditText) MapViewActivity.this.findViewById(R.id.rangingText);
                editText.append(line + "\n");
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                // помещаем домашний activity в вершину стека, если этот activity существует
                Intent homeIntent = new Intent(this, MainActivity.class);
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(homeIntent);
                return true;
            }
        return (super.onOptionsItemSelected(menuItem));
    }
}
