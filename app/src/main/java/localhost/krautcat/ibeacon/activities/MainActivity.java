package localhost.krautcat.ibeacon.activities;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.os.Bundle;
import android.view.View;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;

import localhost.krautcat.ibeacon.R;

public class MainActivity extends AppCompatActivity implements
        NavigationView.OnNavigationItemSelectedListener {

    private static final long DRAWER_CLOSE_DELAY_MS = 250;
    private static final String NAV_ITEM_ID = "navItemId";

    private final Handler navDrawerActionHandler = new Handler();
    private DrawerLayout navDrawerLayout;
    private ActionBarDrawerToggle navDrawerToggle;
    private CharSequence navDrawerTitle;
    private int navNavItemId;

    private static BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private static int REQUEST_ENABLE_BT = 1;
    private static Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

    private Boolean isDownloadDone = true;

    // xml-файл с параметрами карты
    File metaFile;
    String metaPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // определяем, что будет показываться
        setContentView(R.layout.activity_main);

        // создаем тулбар
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_main);
        setSupportActionBar(toolbar);
        assert getSupportActionBar() != null;
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
        try {
            setMetaInfo();
        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
        }

    }

    private void navigate(final int itemId) {
        // perform the actual navigation logic, updating the main content fragment etc
        switch (itemId) {
            case R.id.drawer_item_1: {
                if (!mBluetoothAdapter.isEnabled()) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    Intent intent = new Intent(this, NavigationActivity.class);
                    startActivity(intent);
                }
            }
            case R.id.drawer_item_2: {
                if (isDownloadDone) {
                    DonwloadAsyncTask downloadThread = new DonwloadAsyncTask();
                    downloadThread.execute();
                } else {
                    Snackbar.make(findViewById(R.id.main_activity_layout), R.string.snackbarMainActivityDownloadUndone,
                            Snackbar.LENGTH_SHORT).show();
                }
            } break;
            break;
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
                    Toast.makeText(this, R.string.toastMainActivityBluetoothRefused, Toast.LENGTH_SHORT).show();
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
        return navDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
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
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File map_file = new File (getFilesDir().getParent() + File.separator + "maps" + File.separator + "map.xml");
        File meta_file = new File (getFilesDir().getParent() + File.separator + "maps" + File.separator + "meta.xml");
        if ( !map_file.exists() || !meta_file.exists() ) try {
            InputStream is = assetManager.open("maps/map.xml");
            InputStream isMeta = assetManager.open("maps/meta.xml");
            FileOutputStream outputStream = new FileOutputStream(map_file);
            FileOutputStream outputStreamMeta = new FileOutputStream(meta_file);
            byte buffer[] = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            is.close();
            while ((length = isMeta.read(buffer)) > 0) {
                outputStreamMeta.write(buffer, 0, length);
            }
            outputStreamMeta.close();
            isMeta.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // подготовка парсера
    private XmlPullParser prepareXpp(File inpFile) throws IOException, XmlPullParserException {
        // получаем фабрику и включаем поддержку namespace (по умолчанию выключена)
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        // создаем парсер и даем парсеру на вход FileReader
        XmlPullParser xpp = factory.newPullParser();
        xpp.setInput(new FileReader(inpFile));
        return xpp;
    }

    private void setMetaInfo() throws IOException, XmlPullParserException {

        TextView textMainActivityInfoMap = (TextView) findViewById(R.id.textMainActivityInfoMap);
        TextView textMainActivityModifiedMap = (TextView) findViewById(R.id.textMainActivityModifiedMap);

        String text = "XML file is invalidate";

        // подготовка парсера
        metaPath = getFilesDir().getParent() + File.separator +
                "maps" + File.separator + "meta.xml";
        metaFile = new File(metaPath);
        XmlPullParser xpp = prepareXpp(metaFile);

        // устанавливаем переменные для типа события при парсинге, имении тега + номера с координатой
        int eventType = xpp.getEventType();
        String currentTag;

        // парсим потегово, костыль снизу -- так как парсер воспринимает whitespaces как текст
        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG: {
                    break;
                }
                case XmlPullParser.END_TAG: {
                    currentTag = xpp.getName();
                    switch (currentTag) {
                        case "info": {
                            textMainActivityInfoMap.setText(text);
                            break;
                        }
                        case "modified": {
                            textMainActivityModifiedMap.setText(text);
                            break;
                        }
                        default:
                            break;
                    }
                    break;
                }
                case XmlPullParser.TEXT: {
                    if (!(xpp.getText().matches("\\n\\s*"))) {
                        text = xpp.getText();
                    }
                    break;
                }
                default: {
                    break;
                }
            }
            eventType = xpp.next();
        }
    }

    class DonwloadAsyncTask extends AsyncTask<Void, Integer, Void> {

//        int progress_status;
        boolean downloadStatus = false;

        @Override
        protected void onPreExecute() {
            isDownloadDone = false;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                downloadMap();
                downloadStatus = true;
            } catch (IOException e) {
                downloadStatus = false;
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {

        }

        @Override
        protected void onPostExecute(Void result) {
            try {
                setMetaInfo();
            } catch (IOException | XmlPullParserException e) {
                e.printStackTrace();
            }
            isDownloadDone = true;
            if (downloadStatus) {
                Toast.makeText(MainActivity.this, R.string.toastMainActivityDownloadSuccess,
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, R.string.toastMainActivityDownloadFail,
                        Toast.LENGTH_SHORT).show();
            }
        }

        private void downloadMap() throws IOException {

            File dir = new File(getFilesDir().getParent() + File.separator + "maps");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();

            ArrayList<File> arrayFiles = new ArrayList<>(2);
            arrayFiles.add(new File (getFilesDir().getParent() + File.separator + "maps" + File.separator + "map.xml"));
            arrayFiles.add(new File (getFilesDir().getParent() + File.separator + "maps" + File.separator + "meta.xml"));

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

            ArrayList<String> arrayStringURLs = new ArrayList<>(2);
            arrayStringURLs.add("http://" + (subnetMask != null ? subnetMask.substring(1, (subnetMask.length()) - 3) : null) + "38:4567/download/map");
            arrayStringURLs.add("http://" + (subnetMask != null ? subnetMask.substring(1, (subnetMask.length()) - 3) : null) + "38:4567/download/meta");

            ArrayList<HttpURLConnection> arrayConnections = new ArrayList<>(2);
            for (int i = 0; i < arrayStringURLs.size(); i++) {
                arrayConnections.add( (HttpURLConnection) (new URL(arrayStringURLs.get(i))).openConnection() );
            }
            for (HttpURLConnection temp : arrayConnections) {
                temp.setRequestMethod("GET");
                temp.setConnectTimeout(15000);
            }

            ArrayList<InputStream> arrayInStreams = new ArrayList<>(2);
            for (int i = 0; i < arrayStringURLs.size(); i++) {
                arrayInStreams.add( arrayConnections.get(i).getInputStream() );
            }

            ArrayList<FileOutputStream> arrayFOutStreams = new ArrayList<>(2);
            for (int i = 0; i < arrayStringURLs.size(); i++) {
                arrayFOutStreams.add( new FileOutputStream(arrayFiles.get(i)) );
            }

            byte[] buffer = new byte[1024];
            int bufferLength;

            for (int i = 0; i < arrayFOutStreams.size(); i++) {
                while ((bufferLength = arrayInStreams.get(i).read(buffer)) > 0) {
                    arrayFOutStreams.get(i).write(buffer, 0, bufferLength);
                }
                arrayFOutStreams.get(i).close();
            }
        }
    }




}
