package com.noxmedical.blescanner;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.noxmedical.blescanner.models.BleAdvertiseSegment;
import com.noxmedical.blescanner.utils.BluetoothSigConsts;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScanListActivity extends AppCompatActivity {
    private static final String LOG_TAG = "ScanListActivity";
    private static final boolean FORCE_OLD = false;
    private ToggleButton leScanButton;
    private ToggleButton advertiseButton;
    private ToggleButton classicScanButton;
    private ListView deviceListView;
    private TextView tvStatusHeading;
    private TextView tvStatus;
    private TextView tvDeviceInfo;
    private String curBda = null;
    private ArrayAdapter<String> devicesAdapter;
    private BluetoothAdapter btAdapter;
    /** 0xFFFF is specified by Bluetooth SIG as a dev code */
    private static final int DUMMY_MANUFACTURER_CODE = 0xFFFF;
    private static final int SEGMENT_TYPE_MANUFACTURER_SPECIFIC = 0xFF;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_list);

        leScanButton = (ToggleButton) findViewById(R.id.sla_toggle_lescan);
        advertiseButton = (ToggleButton) findViewById(R.id.sla_toggle_advertise);
        classicScanButton = (ToggleButton) findViewById(R.id.sla_toggle_classicscan);
        deviceListView = (ListView) findViewById(R.id.sla_list);
        tvStatusHeading = (TextView) findViewById(R.id.sla_tv_status_heading);
        tvStatus = (TextView) findViewById(R.id.sla_tv_status);
        tvDeviceInfo = (TextView) findViewById(R.id.sla_tablet_info);

        tvStatusHeading.setText("");
        tvStatus.setText("Start LE scan and click device to get device info");


        leScanButton.setOnCheckedChangeListener(leScanButtonCheckedChangedListener);
        advertiseButton.setOnCheckedChangeListener(advertiseButtonCheckedChangedListener);
        classicScanButton.setOnCheckedChangeListener(classicScanButtonCheckedChangedListener);
        devicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        deviceListView.setAdapter(devicesAdapter);
        deviceListView.setOnItemClickListener(devicesListViewOnItemClickListener);
        btAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported!", Toast.LENGTH_SHORT).show();
            finish();
        }

        if(btAdapter != null) {
            boolean isMult = btAdapter.isMultipleAdvertisementSupported();
            boolean isOffl = btAdapter.isOffloadedFilteringSupported();
            boolean isOfflScan = btAdapter.isOffloadedScanBatchingSupported();
            final String deviceInfo = String.format(Locale.US, "hasMultiAdv: %b\nhasOfflFilt: %b\nhasOfflScan: %b\n\n", isMult, isOffl, isOfflScan);
            tvDeviceInfo.setText(deviceInfo);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerBluetoothReceiver(getApplicationContext());
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterBluetoothReceiver(getApplicationContext());
    }


    private void gotLeAdvertisement(@NonNull BluetoothDevice device, int rssi, @NonNull byte[] payload) {
        if(devicesAdapter.getPosition(device.getAddress()) == -1) {
            devicesAdapter.add(device.getAddress());
            devicesAdapter.notifyDataSetChanged();
        }
        if(device.getAddress().equals(curBda)) {
            try {
                BleAdvertiseSegment[] segments = BleAdvertiseSegment.segmentize(payload);
                StringBuilder sb = new StringBuilder();
                sb.append(String.format(Locale.US, "No. of segments: %d\n", segments.length));
                for(int i = 0; i < segments.length; i++) {
                    BleAdvertiseSegment segment = segments[i];
                    sb.append(String.format(Locale.US, "Segm. %d-> type:0x%02X (%s), len: %d\n",
                            i, segment.segmentType,
                            BluetoothSigConsts.segmentTypeMap.get(segment.segmentType, "Unknown"),
                            segment.segmentData.length));
                    ByteBuffer bb = ByteBuffer.wrap(segment.segmentData);
                    bb.order(ByteOrder.LITTLE_ENDIAN);
                    if(segment.segmentType == SEGMENT_TYPE_MANUFACTURER_SPECIFIC && bb.remaining() > 2) {
                        int companyId = (bb.getShort() & 0xFFFF);
                        sb.append(String.format(Locale.US, "Company: %s",
                                BluetoothSigConsts.companyMap.get(companyId, "Unknown")));
                    }

                    sb.append("\n");
                }
                tvStatus.setText(sb.toString());
            } catch (BleAdvertiseSegment.InvalidMessageException e) {
                tvStatus.setText(R.string.invalid_ble_message);
            }

        }

    }

    private BluetoothAdapter.LeScanCallback leOldScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            gotLeAdvertisement(device, rssi, scanRecord != null ? scanRecord : new byte[] {});
        }
    };

    private ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            super.onScanResult(callbackType, result);
            gotLeAdvertisement(result.getDevice(), result.getRssi(), result.getScanRecord() != null ? result.getScanRecord().getBytes() : new byte[] {});
        }

        public void onBatchScanResults(List<ScanResult> results) {
            Log.d(LOG_TAG, "onBatchScanResults");
        }

        /**
         * Callback when scan could not be started.
         *
         * @param errorCode Error code (one of SCAN_FAILED_*) for scan failure.
         */
        public void onScanFailed(int errorCode) {
            Log.d(LOG_TAG, "onScanFailed");
        }
    } ;

    private CompoundButton.OnCheckedChangeListener leScanButtonCheckedChangedListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if(!isBluetoothOk()) {
                return;
            }
            if(!checkBluetoothPermission()) {
                buttonView.setChecked(false);
                return;
            }
            if (isChecked) {
                Log.d(LOG_TAG, "Starting BLE scan");
                devicesAdapter.clear();
                devicesAdapter.notifyDataSetChanged();

                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !FORCE_OLD) {
                    // Android 5.0 and newer
                    BluetoothLeScanner bls = btAdapter.getBluetoothLeScanner();
                    ScanSettings.Builder ssBuilder = new ScanSettings.Builder();
                    ssBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
                    bls.startScan(null, ssBuilder.build(), bleScanCallback);
                } else {
                    Log.d(LOG_TAG, "OLD API start scan");
                    // Android 4.4 and older
                    btAdapter.startLeScan(leOldScanCallback);
                }

            } else {
                Log.d(LOG_TAG, "Stopping BLE scan");
                // btAdapter.stopLeScan(leOldScanCallback);
                devicesAdapter.notifyDataSetChanged();
                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !FORCE_OLD) {
                    BluetoothLeScanner bls = btAdapter.getBluetoothLeScanner();
                    bls.stopScan(bleScanCallback);
                } else {
                    Log.d(LOG_TAG, "OLD API stop scan");
                    // Android 4.4 and older
                    btAdapter.stopLeScan(leOldScanCallback);
                }
            }
        }
    };

    private boolean isBluetoothOk() {
        if (btAdapter == null) {
            Toast.makeText(getApplicationContext(), "Your device does not support Bluetooth", Toast.LENGTH_LONG).show();
            return false;
        } else {
            if (!btAdapter.isEnabled()) {
                Toast.makeText(getApplicationContext(), "Please enable Bluetooth", Toast.LENGTH_LONG).show();
                return false;
            }
        }
        return true;
    }

    private CompoundButton.OnCheckedChangeListener classicScanButtonCheckedChangedListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if(!isBluetoothOk()) {
                return;
            }
            if(!checkBluetoothPermission()) {
                buttonView.setChecked(false);
                return;
            }
            if(isChecked) {
                Log.d(LOG_TAG, "Starting classic scan");
                devicesAdapter.clear();
                devicesAdapter.notifyDataSetChanged();
                startClassicScan();

            } else {
                Log.d(LOG_TAG, "Stopping classic scan");
                // btAdapter.stopLeScan(leOldScanCallback);
                devicesAdapter.notifyDataSetChanged();
                stopClassicScan();
            }
        }
    };


    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(LOG_TAG, "Ad start success");
        }

        @Override
        public void onStartFailure(int errCode) {
            super.onStartFailure(errCode);
            Log.d(LOG_TAG, "Ad start failure: " + errCode);
        }
    };

    private CompoundButton.OnCheckedChangeListener advertiseButtonCheckedChangedListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if(!isBluetoothOk()) {
                return;
            }
            if(!checkBluetoothPermission()) {
                buttonView.setChecked(false);
                return;
            }
            BluetoothLeAdvertiser bleAdv = btAdapter.getBluetoothLeAdvertiser();

            if(isChecked) {
                Log.d(LOG_TAG, "Starting BLE advertise");
                bleAdv.startAdvertising(getAdvertiseSettings(), getAdvertiseData(), advertiseCallback);


            } else {
                Log.d(LOG_TAG, "Stopping BLE advertise");
                bleAdv.stopAdvertising(advertiseCallback);
            }
        }
    };

    protected AdvertiseSettings getAdvertiseSettings() {
        AdvertiseSettings.Builder mBuilder = new AdvertiseSettings.Builder();
        mBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
        mBuilder.setConnectable(false);
        mBuilder.setTimeout(0);
        mBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
        return mBuilder.build();
    }

    private AdvertiseData getAdvertiseData() {
        AdvertiseData.Builder mBuilder = new AdvertiseData.Builder();
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.put((byte)(bb.array().length - 1 & 0xFF)); // len
        bb.put((byte)SEGMENT_TYPE_MANUFACTURER_SPECIFIC);
        bb.put((byte)(0x12 & 0xFF)); // some fake data
        bb.put((byte)(0x34 & 0xFF));
        mBuilder.addManufacturerData(DUMMY_MANUFACTURER_CODE, bb.array());
        return mBuilder.build();
    }

    /**
     *
     * @return false when not to proceed
     */
    private boolean checkBluetoothPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(ScanListActivity.this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        1);
                return false;
            }
        }
        return true;
    }

    private AdapterView.OnItemClickListener devicesListViewOnItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            curBda = devicesAdapter.getItem(position);
            tvStatusHeading.setText(curBda);
            tvStatus.setText("");


        }
    };
    /**
     * Return true if adapter was available, false otherwise
     */
    public void startClassicScan() {
        if (btAdapter.isDiscovering()) {
            btAdapter.cancelDiscovery();
        }

        // Request discover from BluetoothAdapter
        btAdapter.startDiscovery();
    }

    /**
     * Return true if adapter was available, false otherwise
     */
    public void stopClassicScan() {
        btAdapter.cancelDiscovery();
        Log.i(LOG_TAG, "Bluetooth scanning stopped");
    }

    public final void registerBluetoothReceiver(Context ctx) {
        ctx.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        ctx.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
        ctx.registerReceiver(mReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
    }

    public final void unregisterBluetoothReceiver(Context ctx) {
        ctx.unregisterReceiver(mReceiver);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(LOG_TAG,"Bluetooth discovery finished");
            } else if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Log.d(LOG_TAG,"Bluetooth discovery finished");
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(LOG_TAG, String.format("Device found, %s (%s)", device.getName() != null ? device.getName() : "", device.getAddress()));
                devicesAdapter.add(device.getAddress());
            }
        }
    };

}
