package localhost.krautcat.ibeacon.activities;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.service.ArmaRssiFilter;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import localhost.krautcat.ibeacon.R;

public class NavigationActivity extends AppCompatActivity implements BeaconConsumer {

    final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private BluetoothReceiver btReceiver;

    private ArrayList<Beacon> beaconsList;
    private BeaconManager beaconManager;

    private DrawView dview;                             // view, отвечающая за рисование карты
    private SurfaceMovingPinView surfaceView;           // view для определения координат
    private ImageView pinView;                          // view, реализующая положение человека

    // xml-файл с параметрами карты
    File mapFile;
    String mapPath;

    // параметры карты
    final Map<Integer, Float> mapPointsX = new LinkedHashMap<>();
    final Map<Integer, Float> mapPointsY = new LinkedHashMap<>();
    final Map<BeaconClass, Float[]> coordBeacons = new HashMap<>();
    final Map<BeaconClass, Float[]> rawCoords = new HashMap<>();

    private static int REQUEST_ENABLE_BT = 1;
    private static Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

    private static float meterToPoints;
    private static float pointToPixel;

    /** ################################################################### **/
    /** ################################################################### **/
    /** ################################################################### **/

    /** ------------------------------------------------------------------- **/
    /**         методы, выполняющиеся при жизненном цикле Activity          **/
    /** ------------------------------------------------------------------- **/

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // при создании - сохраненное состояние + выбираем соответствующий layout
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        verifyBluetooth();
        // непонятный кусок кода, отвечающий за разрешения на Android M
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons in the background.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                    @TargetApi(23)
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                PERMISSION_REQUEST_COARSE_LOCATION);
                    }

                });
                builder.show();
            }
        }

        configBeaconManager();

        // Register for broadcasts on BluetoothAdapter state change
        btReceiver = new BluetoothReceiver();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(btReceiver, filter);

        // пытаемся парсить xml с картой, при неудаче возвращаемся на главный экран и завершаем
        // текущий activity
        try {
            prepareMap();
        } catch (Throwable t) {
            Toast.makeText(this, "Ошибка при загрузке XML-документа: " + t.toString(),
                    Toast.LENGTH_LONG).show();
            // помещаем домашний activity в вершину стека, если этот activity существует
            Intent homeIntent = new Intent(this, MainActivity.class);
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(homeIntent);
            finish();
        }

        // добавляем view для карты во FrameLayout и задаем параметры, чтобы карта занимала
        // всё пространство в layout
        final FrameLayout DrawViewLayout = (FrameLayout) findViewById(R.id.DrawViewLayout);
        dview = new DrawView(this);
        dview.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        /** ждём, пока нарисуется Большая Клёвая Кнопка, вешая на неё listener, затем           **/
        /** изменяем параметры layout, в который вложен DrawView и удаляем listener после       **/
        /** однократного вызова                                                                 **/
        final ViewTreeObserver observer = findViewById(R.id.fab).getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // минимальные паддинги для layout
                int minBottomPaddingLayoutGroup = findViewById(R.id.fab).getHeight() + 2 * (int) getResources().getDimension(R.dimen.fab_margin);
                int minTopPaddingLayoutGroup = (int) getResources().getDimension(R.dimen.fab_margin);
                int minHorizPaddingLayoutGroup = (int) getResources().getDimension(R.dimen.fab_margin);

                // виртуальный внутренний паддинг для карты, которая будет рисоваться
                int internalPadding = (int) getResources().getDimension(R.dimen.internal_map_margin);

                // добавочные внешние паддинги, которые сжимают layout до необходимого уровня
                int addVertPaddingLayoutGroup = 0;
                int addHorizPaddingLayoutGroup = 0;

                // реальный паддинг для layout
                int actualBottomPaddingLayoutGroup;
                int actualTopPaddingLayoutGroup;
                int actualHorizPaddingLayoutGroup;

                // параметры для карты, преобразующий координаты в пиксели
                float pointToPixel;

                // максимальные координаты карты
                Float maxMapCoordX = Collections.max(mapPointsX.values());
                Float maxMapCoordY = Collections.max(mapPointsY.values());

                // вычисляем дополнительные паддинги и ptp - количество пикселей в единичной координате
                if (maxMapCoordX > maxMapCoordY) {
                    pointToPixel = (findViewById(R.id.ContentLayout).getWidth() -
                            minHorizPaddingLayoutGroup * 2 - internalPadding * 2) / maxMapCoordX;
                    addVertPaddingLayoutGroup = (int) (findViewById(R.id.ContentLayout).getHeight() -
                            findViewById(R.id.toolbar_map).getHeight() -
                            (minBottomPaddingLayoutGroup + minTopPaddingLayoutGroup) -
                            maxMapCoordY * pointToPixel - internalPadding * 2) / 2;
                } else {
                    pointToPixel = (findViewById(R.id.ContentLayout).getHeight() -
                            minBottomPaddingLayoutGroup - minTopPaddingLayoutGroup -
                            internalPadding * 2) / maxMapCoordY;
                    addHorizPaddingLayoutGroup = (int) (findViewById(R.id.ContentLayout).getWidth() -
                            (2 * minHorizPaddingLayoutGroup) - maxMapCoordX * pointToPixel -
                            internalPadding * 2) / 2;
                }
                // задаем в параметры карты значение ptp и максимального Y (необходимо для рисования)
                dview.setPointToPixel(pointToPixel);
                dview.setMaxCoordY(maxMapCoordY);

                // задаем актуальный паддинг для layout
                actualBottomPaddingLayoutGroup = minBottomPaddingLayoutGroup + addVertPaddingLayoutGroup;
                actualTopPaddingLayoutGroup = minTopPaddingLayoutGroup + addVertPaddingLayoutGroup;
                actualHorizPaddingLayoutGroup = minHorizPaddingLayoutGroup + addHorizPaddingLayoutGroup;

                DrawViewLayout.setPadding(actualHorizPaddingLayoutGroup, actualTopPaddingLayoutGroup,
                        actualHorizPaddingLayoutGroup, actualBottomPaddingLayoutGroup);
                DrawViewLayout.requestLayout();

                NavigationActivity.pointToPixel = pointToPixel;
                // устанавливаем координаты на холсте для маячков
                setBeaconsCoords(rawCoords, actualHorizPaddingLayoutGroup, actualTopPaddingLayoutGroup);

                DrawViewLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
        DrawViewLayout.addView(dview);

        pinView = (ImageView) findViewById(R.id.locationPinView);
        final ViewTreeObserver observerPinView = pinView.getViewTreeObserver();
        observerPinView.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                findViewById(R.id.ContentLayout).getViewTreeObserver().removeOnGlobalLayoutListener(this);

                int[] lc = new int[2];
                pinView.getLocationInWindow(lc);
                pinView.setX( -1 * pinView.getWidth() / 2 );
                pinView.setY( -1 * pinView.getHeight() );
                Bitmap locationPinBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.point_marker_icon);
                pinView.setImageBitmap(createInvertedBitmap(locationPinBitmap));
                pinView.setVisibility(View.VISIBLE);
            }
        });

        surfaceView = new SurfaceMovingPinView(this);
        surfaceView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout SurfaceViewLayout = (FrameLayout) findViewById(R.id.SurfaceViewLayout);
        SurfaceViewLayout.addView(surfaceView);
        final ViewTreeObserver observerSurfaceView = SurfaceViewLayout.getViewTreeObserver();
        observerSurfaceView.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                findViewById(R.id.SurfaceViewLayout).getViewTreeObserver().removeOnGlobalLayoutListener(this);

                surfaceView.setX(0f);
                surfaceView.setY(0f);
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            pinView.setZ(surfaceView.getZ() + 5f);
        }
//        dview.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
//            @Override
//            public void onGlobalLayout() {
//                dview.getViewTreeObserver().removeOnGlobalLayoutListener(this);
//
//                startY = (int) (0);
//                int stopY = (int) (5);
//                segmentLength = stopY - startY;
//
//                dotView.setVisibility(View.VISIBLE);
////                dotView.setTranslation();
//            }
//        });
//        setContentView(new DrawView(this));
//

        // создаем тулбар
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_map);
        setSupportActionBar(toolbar);
        assert getSupportActionBar() != null;
        getSupportActionBar().setTitle(getTitle());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
//
//        // плавающая кнопка
//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View view) {
//                    Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                            .setAction("Action", null).show();
//                }
//            }
//        );
//        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (beaconManager.isBound(this)) beaconManager.setBackgroundMode(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (beaconManager.isBound(this)) beaconManager.setBackgroundMode(true);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        beaconManager.unbind(this);
        // Unregister broadcast listeners
        unregisterReceiver(btReceiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            switch (resultCode) {
                case RESULT_OK: {
                    Toast.makeText(this, R.string.toastNavigationActivityBluetoothEnabled, Toast.LENGTH_LONG);
                    break;
                }
                case RESULT_CANCELED: {
                    Toast.makeText(this, "MDA", Toast.LENGTH_SHORT).show();
                    // помещаем домашний activity в вершину стека, если этот activity существует
                    Intent homeIntent = new Intent(this, MainActivity.class);
                    homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(homeIntent);
                    finish();
                    break;
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                // помещаем домашний activity в вершину стека, если этот activity существует
                Intent homeIntent = new Intent(this, MainActivity.class);
                homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(homeIntent);
                finish();
                return true;
        }
        return (super.onOptionsItemSelected(menuItem));
    }

    // проверяем доступность Bluetooth
    private void verifyBluetooth() {
        try {
            if (!BeaconManager.getInstanceForApplication(this).checkAvailability()) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Bluetooth not enabled");
                builder.setMessage("Please enable bluetooth in settings and restart this application.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        finish();
                        Intent homeIntent = new Intent(NavigationActivity.this, MainActivity.class);
                        homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(homeIntent);
                    }
                });
                builder.show();
                // помещаем домашний activity в вершину стека, если этот activity существует
            }
        } catch (RuntimeException e) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Bluetooth LE not available");
            builder.setMessage("Sorry, this device does not support Bluetooth LE.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                @Override
                public void onDismiss(DialogInterface dialog) {
                    finish();
                    System.exit(0);
                }

            });
            builder.show();
        }
    }

    // проверяем разрешения - требуется для Android M для поиска маячков в фоне
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("MDA", "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
            }
        }
    }

    // конфигурируем beaconmanager
    private void configBeaconManager() {
        // получаем инстанс для данного приложения
        beaconManager = BeaconManager.getInstanceForApplication(this);

        BeaconManager.setRssiFilterImplClass(ArmaRssiFilter.class);
        beaconManager.setForegroundScanPeriod(1401);
        // добавляем парсер iBeacon'ов
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24"));
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));

        // When binding to the service, we return an interface to our messenger for sending messages to the service.
        beaconManager.bind(this);

        // инициализируем массив beacons
        beaconsList = new ArrayList<>();
    }

    // метод, который выполняет при коннекте к сервису маяков
    @Override
    public void onBeaconServiceConnect() {
        final Toast toastOnNavigation = Toast.makeText(this, R.string.toastNavigationActivityFindLocation, Toast.LENGTH_SHORT);
        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(final Collection<Beacon> beacons, org.altbeacon.beacon.Region region) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        beaconsList.clear();
                        if (beacons.size() > 2) {
                            beaconsList.addAll(beacons);
                            toastOnNavigation.cancel();
                            movePinView();
                        } else {
                            // TODO затемнение экрана и сокрытие view в противном случае
                            toastOnNavigation.show();
                        }
                    }
                });
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new org.altbeacon.beacon.Region("0x00000000000000005545600000000000", null, null, null));
        } catch (RemoteException e) {
            Toast.makeText(this, R.string.toastNavigationActivityBeaconManagerRegionException, Toast.LENGTH_LONG).show();
        }
    }

    // подготавливаем карту из xml-файла
    private void prepareMap() throws IOException, XmlPullParserException {
        int numStart = 1;
        int numFinish = 2;
        float meters = 1;
        float coordXStart;
        float coordYStart;
        float coordXFinish;
        float coordYFinish;

        // подготовка парсера
        mapPath = getFilesDir().getParent() + File.separator +
                "maps" + File.separator + "map.xml";
        mapFile = new File(mapPath);
        XmlPullParser xpp = prepareXpp(mapFile);

        // устанавливаем переменные для типа события при парсинге, имении тега + номера с координатой
        int eventType = xpp.getEventType();
        String currentTag;
        int num = 0;
        float coord = 0;

        // парсим потегово, костыль снизу -- так как парсер воспринимает whitespaces как текст
        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG: {
                    currentTag = xpp.getName();
                    switch (currentTag) {
                        case "point": {
                            num = Integer.parseInt(xpp.getAttributeValue(0));
                            break;
                        }
                        case "beacon": {
                            BeaconClass tempBeacon = new BeaconClass(
                                    xpp.getAttributeValue(0),
                                    Integer.parseInt(xpp.getAttributeValue(1)),
                                    Integer.parseInt(xpp.getAttributeValue(2))
                            );
                            Float[] tempCoord = new Float[] {
                                    Float.parseFloat(xpp.getAttributeValue(3)),
                                    Float.parseFloat(xpp.getAttributeValue(4))
                            };
                            rawCoords.put(tempBeacon, tempCoord);
                            break;
                        }
                        case "metertopoint": {
                            numStart = Integer.parseInt(xpp.getAttributeValue(0));
                            numFinish = Integer.parseInt(xpp.getAttributeValue(1));
                            meters = Float.parseFloat(xpp.getAttributeValue(2));
                            break;
                        }
                        default: {
                            break;
                        }
                    }
                    break;
                }
                case XmlPullParser.END_TAG: {
                    currentTag = xpp.getName();
                    switch (currentTag) {
                        case "x": {
                            mapPointsX.put(num, coord);
                            break;
                        }
                        case "y": {
                            mapPointsY.put(num, coord);
                            break;
                        }
                        default:
                            break;
                    }
                    break;
                }
                case XmlPullParser.TEXT: {
                    if (!(xpp.getText().matches("\\n\\s*"))) {
                        coord = Float.parseFloat(xpp.getText());
                    }
                    break;
                }
                default: {
                    break;
                }
            }
            eventType = xpp.next();
        }

        coordXStart = mapPointsX.get(numStart);
        coordYStart = mapPointsY.get(numStart);
        coordXFinish = mapPointsX.get(numFinish);
        coordYFinish = mapPointsY.get(numFinish);

        meterToPoints = (float) Math.sqrt(Math.pow(( coordXStart - coordXFinish ), 2) +
                Math.pow(( coordYStart - coordYFinish ), 2)) / meters;
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

    public void setBeaconsCoords(Map<BeaconClass, Float[]> mapBeacons, float rightPaddingMap, float topPaddingMap) {
        Float correctX = (float) dview.mapMargin;                                   // корректирующее значение по X
        Float correctY = dview.maxMapCoordY * dview.pointToPixel + dview.mapMargin; // корректирующее значение по Y
        Float[] locCoord = new Float[] {
            rightPaddingMap,
            topPaddingMap
        };

        Iterator bufIt = mapBeacons.entrySet().iterator();
        Map.Entry bufEntry;

        while (bufIt.hasNext()) {
            bufEntry = (Map.Entry) bufIt.next();
            float x = correctX + ((Float[]) bufEntry.getValue())[0] * pointToPixel + locCoord[0];
            float y = correctY - ((Float[]) bufEntry.getValue())[1] * pointToPixel + locCoord[1];
            coordBeacons.put((BeaconClass) bufEntry.getKey(), new Float[]{ x, y });
        }
    }

    private void movePinView() {
        BeaconClass bufferBeaconItem = new BeaconClass("t", 0, 0);

        Iterator<Beacon> bufferIterator = beaconsList.iterator();

        Float[] coordForCircles;
        Float distance;

        int i = 0;
        int undefinedBeacons = 0;

        surfaceView.clearArrayCircles();
        while ( i < beaconsList.size() ) {
            Beacon beacon = bufferIterator.next();

            bufferBeaconItem.UUID = beacon.getId1().toString();
            bufferBeaconItem.Major = beacon.getId2().toInt();
            bufferBeaconItem.Minor = beacon.getId3().toInt();

            coordForCircles = coordBeacons.get(bufferBeaconItem);
            distance = (float) ( beacon.getDistance() * meterToPoints * pointToPixel );

            if ( coordForCircles != null ) {
                surfaceView.addToArrayCircles(coordForCircles[0], coordForCircles[1], distance);
            } else {
                undefinedBeacons++;
            }
            i++;
        }

        surfaceView.invalidate();
        float tempCoord[] = surfaceView.getCoordXY();
        if ( ( ( beaconsList.size() - undefinedBeacons ) >= 3 ) && ( tempCoord[0] != Integer.MIN_VALUE ) ) {
            ViewPropertyAnimator ooo = pinView.animate();
            float t1 = computeX(tempCoord[0]);
            float t2 = computeY(tempCoord[1]);
            ooo.translationXBy(t1)
                .translationYBy(t2).start();
        } else {
            Toast.makeText(this, R.string.toastNavigationActivityFindLocation, Toast.LENGTH_SHORT).show();
            // TODO если число известных beacon не превышает три, то ничего не выводим
        }
    }

    private float computeX(float v) {
        float r = pinView.getX();
        return v - ( r + ( pinView.getWidth() / 2 ) );
    }

    private float computeY(float v) {
        return v - ( pinView.getY() + pinView.getHeight() );
    }


    //    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Handle app bar item clicks here. The app bar
//        // automatically handles clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//        if (id == R.id.action_settings) {
//            return true;
//        }
//        return super.onOptionsItemSelected(item);
//    }

    private Bitmap createInvertedBitmap(Bitmap src) {
        ColorMatrix colorMatrixInverted =  new ColorMatrix(new float[] {
                        -1,  0,  0,  0, 255,
                         0, -1,  0,  0, 255,
                         0,  0, -1,  0, 255,
                         0,  0,  0,  1,   0     });

        ColorFilter colorFolterInverse = new ColorMatrixColorFilter(colorMatrixInverted);

        Bitmap bitmap = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();

        paint.setColorFilter(colorFolterInverse);
        canvas.drawBitmap(src, 0, 0, paint);

        return bitmap;
    }

    class DrawView extends View {

        Canvas mCanvas;
        Bitmap mBitmap;

        private float pointToPixel; // параметр для конвертации точки в пиксели
        private float maxMapCoordY; // максимальная координата для перемещения
        int mapMargin = (int) getResources().getDimension(R.dimen.internal_map_margin); // виртуальный отступ для карты внутри View

        Paint paint;

        public DrawView(Context context) {
            super(context);
            paint = new Paint();
            paint.setStrokeWidth(10);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.WHITE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(ContextCompat.getColor(NavigationActivity.this, R.color.colorBackground));

            drawMap(canvas);
        }

        private void drawMap(Canvas canvas) {
            Matrix matrix = new Matrix();
            matrix.setScale(1, -1);
            matrix.postTranslate(mapMargin, mapMargin + maxMapCoordY * pointToPixel);

            Paint paintMap = new Paint();
            paintMap.setStrokeWidth(10);
            paintMap.setStyle(Paint.Style.STROKE);
            paintMap.setColor(Color.RED);

            Path mapPath = new Path();
            int sizeMapPoints = mapPointsX.size();
            mapPath.moveTo(mapPointsX.get(1) * pointToPixel, mapPointsY.get(1) * pointToPixel);
            for (int i = 2; i <= sizeMapPoints; i++) {
                mapPath.lineTo(mapPointsX.get(i) * pointToPixel, mapPointsY.get(i) * pointToPixel);
            }
            mapPath.close();
            mapPath.transform(matrix);
            matrix.reset();

            canvas.drawPath(mapPath, paintMap);
        }

        // override onSizeChanged
        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);

            // холст рисует на конкретный битмап
            mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
        }

        public void setPointToPixel(float pointToPixel) {
            this.pointToPixel = pointToPixel;
        }

        public void setMaxCoordY(Float maxMapCoordY) {
            this.maxMapCoordY = maxMapCoordY;
        }

    }

    class SurfaceMovingPinView extends View {

        Canvas mCanvas;
        Bitmap mBitmap;

        Paint paint;

        ArrayList<Float[]> arrayCircles = new ArrayList<>();
        boolean status = !arrayCircles.isEmpty();

        float coordXNew = Integer.MIN_VALUE;
        float coordYNew = Integer.MIN_VALUE;

        public SurfaceMovingPinView(Context context) {
            super(context);

            paint = new Paint();
            paint.setStrokeWidth(10);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.TRANSPARENT);

            if (status)
                drawCircles(canvas);
        }

        // переопределяем  onSizeChanged
        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);

            // холст рисует на конкретный битмап
            mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            mCanvas = new Canvas(mBitmap);
        }

        private void drawCircles(Canvas canvas) {
            int height = this.getHeight();
            int width = this.getWidth();
            int x0 = 0;
            int y0 = 0;
            Path bufPath = new Path();

            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.YELLOW);

            // создание региона
            Region region = new Region(x0, y0, x0 + width, y0 + height);
            Region region2 = new Region(x0, y0, x0 + width, y0 + height);
            for ( Float[] temp : arrayCircles ) {
                // сбрасываем область круга + добавляем заново
                bufPath.reset();
                bufPath.addCircle(temp[0], temp[1], temp[2], Path.Direction.CW);
                // делаем из региона2 окружность
                region2.set(x0, y0, x0 + width, y0 + height);
                region2.setPath(bufPath, region2);
                canvas.drawPath(bufPath, paint);
                // пересекаем регион и регион2
                region.op(region2, Region.Op.INTERSECT);
            }

            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            bufPath.reset();
            bufPath.addPath(region.getBoundaryPath());
            canvas.drawPath(bufPath, paint);
            Rect tempRect = region.getBounds();
            paint.setColor(Color.CYAN);

            if ( tempRect.isEmpty() ) {
                setCoordXY(Integer.MIN_VALUE, Integer.MIN_VALUE);
            } else {
                setCoordXY(tempRect.exactCenterX(), tempRect.exactCenterY());
                canvas.drawPoint(tempRect.exactCenterX(), tempRect.exactCenterY(), paint);
            }
        }

        private void setCoordXY(float newX, float newY) {
            this.coordXNew = newX;
            this.coordYNew = newY;
        }

        public float[] getCoordXY() {
            return new float[] {
                    this.coordXNew,
                    this.coordYNew
            };
        }

        public void clearArrayCircles() {
            arrayCircles.clear();
        }

        public void addToArrayCircles(float x, float y, float rad) {
            status = true;
            arrayCircles.add(new Float[]{x, y, rad});
        }

    }


    private class BeaconClass {
        public String UUID;
        public int Major;
        public int Minor;

        public BeaconClass(String id1, int id2, int id3) {
            this.UUID = id1;
            this.Major = id2;
            this.Minor = id3;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = (prime * result) + (this.Major * 100000) + this.Minor;
            return result;
        }

        @TargetApi(Build.VERSION_CODES.KITKAT)
        @Override
        public boolean equals(Object obj) {

            if (this == obj) {
                return true;
            }

            if (obj == null) {
                return false;
            }

            if ((getClass() != obj.getClass())) {
                return false;
            }
            BeaconClass other = (BeaconClass) obj;

            //noinspection RedundantIfStatement
            if (Objects.equals(this.UUID, other.UUID) && (this.Major == other.Major) &&
                    (this.Minor == other.Minor)) {
                return true;
            } else {
                return false;
            }
        }
    }

    private class BluetoothReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_TURNING_OFF : {
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                        break;
                    }
                    default : {
                        break;
                    }
                }
            }
        }

    }

    /** конец класс Activity                                                                    **/
}