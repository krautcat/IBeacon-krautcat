package localhost.krautcat.ibeacon.activities;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.os.Handler;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.os.Bundle;
import android.view.View;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.Enumeration;

import localhost.krautcat.ibeacon.R;

public class MainActivity extends AppCompatActivity implements
        NavigationView.OnNavigationItemSelectedListener {

    private static final long DRAWER_CLOSE_DELAY_MS = 250;
    private static final String NAV_ITEM_ID = "navItemId";
    public final static String EXTRA_MESSAGE = "com.localhost.iBeacon.MESSAGE";

    private final Handler navDrawerActionHandler = new Handler();
    private DrawerLayout navDrawerLayout;
    private ActionBarDrawerToggle navDrawerToggle;
    private CharSequence navDrawerTitle;
    private CharSequence navTitle;
    private int navNavItemId;

    private static BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private static int REQUEST_ENABLE_BT = 1;
    private static Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // определяем, что будет показываться
        setContentView(R.layout.activity_main);

        // создаем тулбар
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_main);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(getTitle());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // загружаем существующее состояние, если существует
        if (null == savedInstanceState) {
            navNavItemId = R.id.drawer_item_1;
        } else {
            navNavItemId = savedInstanceState.getInt(NAV_ITEM_ID);
        }

        navDrawerTitle = getTitle();
        navDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        navDrawerToggle = new ActionBarDrawerToggle(
                this,                           /* хост-активити */
                navDrawerLayout,                /* объект DrawerLayout */
                R.string.drawer_open,
                R.string.drawer_close
                ) {

            /** Вызывается, когла drawer установился в польностью закрытое состояние. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                getSupportActionBar().setTitle(navDrawerTitle);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            /** Вызывается, когла drawer установился в польностью открытое состояние. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                getSupportActionBar().setTitle(navDrawerTitle);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

        };
        navDrawerLayout.setDrawerListener(navDrawerToggle);
        navDrawerToggle.syncState();

        // слушаем ивенты в навигационном меню
        NavigationView navigationView = (NavigationView) findViewById(R.id.left_drawer);
        navigationView.setNavigationItemSelectedListener(this);

        // выбираем правильный пункт в навигационном меню
        navigationView.getMenu().findItem(navNavItemId).setChecked(true);

        defaultMapFile();
    }

    private void navigate(final int itemId) {
        // perform the actual navigation logic, updating the main content fragment etc
        switch (itemId) {
            case R.id.drawer_item_1: {
                try {
                    downloadMap();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } break;
            case R.id.drawer_item_2: {
                if (!mBluetoothAdapter.isEnabled()) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    Intent intent = new Intent(this, NavigationActivity.class);
                    startActivity(intent);
                }
            } break;
        }

    }

    @Override
    public boolean onNavigationItemSelected(final MenuItem menuItem) {
        // update highlighted item in the navigation menu
        menuItem.setChecked(true);
        navNavItemId = menuItem.getItemId();

        // allow some time after closing the drawer before performing real navigation
        // so the user can see what is happening
        navDrawerLayout.closeDrawer(GravityCompat.START);
        navDrawerActionHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                navigate(menuItem.getItemId());
            }
        }, DRAWER_CLOSE_DELAY_MS);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (navDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            navDrawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        navDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        navDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            switch (resultCode) {
                case RESULT_OK: {
                    Intent intent = new Intent(this, NavigationActivity.class);
                    startActivity(intent);
                    break;
                }
                case RESULT_CANCELED: {
                    Toast.makeText(this, "MDA", Toast.LENGTH_SHORT).show();
                    break;
                }
            }
        }
    }

/*    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (navDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle your other action bar items...


        return super.onOptionsItemSelected(item);
    }*/

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (navDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(NAV_ITEM_ID, navNavItemId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NavigationView navigationView = (NavigationView) findViewById(R.id.left_drawer);
        navigationView.getMenu().findItem(navNavItemId).setChecked(false);
    }

    private void defaultMapFile() {
        // создаём директорию и помещаем туда файл
        AssetManager assetManager = getAssets();
        File dir = new File(getFilesDir().getParent() + File.separator + "maps");
        if ( !(dir.isDirectory() || dir.exists()) )
            dir.mkdirs();
        File map_file = new File (getFilesDir().getParent() + File.separator + "maps" + File.separator + "map.xml");
        if(!map_file.exists()) {
            try {
                InputStream is = assetManager.open("maps/map.xml");
                String[] files = assetManager.list("Files");
                OutputStream outputStream = new FileOutputStream(map_file);
                byte buffer[] = new byte[1024];
                int length = 0;
                while ((length = is.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
                outputStream.close();
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void downloadMap() throws IOException {
        int totalSize;

        File dir = new File(getFilesDir().getParent() + File.separator + "maps");
        if ( !(dir.isDirectory() || dir.exists()) )
            dir.mkdirs();
        File mapFile = new File (getFilesDir().getParent() + File.separator + "maps" + File.separator + "map.xml");

        String subnetMask = null;
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements())
        {
            NetworkInterface networkInterface = interfaces.nextElement();

            if (networkInterface.isLoopback())
                continue; // Don't want to broadcast to the loopback interface

            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses())
            {
                InetAddress broadcast = interfaceAddress.getBroadcast();

                // InetAddress ip = interfaceAddress.getAddress();
                // interfaceAddress.getNetworkPrefixLength() is another way to express subnet mask

                // Android seems smart enough to set to null broadcast to
                //  the external mobile network. It makes sense since Android
                //  silently drop UDP broadcasts involving external mobile network.
                if (broadcast == null)
                    continue;

                subnetMask = broadcast.toString();
            }
        }

        String ipServer = subnetMask.substring(1, subnetMask.length() - 3) + "38:4567/download";

        URL url = new URL(ipServer);
        HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();

        urlConn.setRequestMethod("GET");
        urlConn.setDoOutput(true);
        urlConn.setConnectTimeout(600000);

        InputStream inputStream = urlConn.getInputStream();
        FileOutputStream fileOutputStream = new FileOutputStream(mapFile);

        totalSize = urlConn.getContentLength();

        byte[] buffer = new byte[1024];
        int bufferLength = 0;

        while ( ( bufferLength = inputStream.read(buffer) ) > 0 ) {
            fileOutputStream.write(buffer, 0, bufferLength);
        }
        fileOutputStream.close();
//        urlConn.setReadTimeout(TIMEOUT_CONNECTION);
//        urlConn.setConnectTimeout(URLConnection.);
    }

}
