package com.example.wkl04.BLEScan;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.util.Log;

import com.example.wkl04.BLEScan.MainActivity;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by wkl04 on 27-11-2016.
 */

public class LocusScanner {
    private class ScanEntry
    {
        public float sum_rssi;
        public int count;

        public ScanEntry() {
            sum_rssi = 0;
            count = 0;
        }

        public void addRssi(int rssi) {
            sum_rssi += rssi;
            count += 1;
        }

        public float getAvgRssi() {
            return sum_rssi / count;
        }

        public int getCount() { return count; }

        public void dumpEntries() {

        }
    }

    private class PostDataTask extends AsyncTask<String, String, String> {
        private String username;
        private String password;

        public PostDataTask(String username, String password) {
            this.username = username;
            this.password = password;
        }

        protected String doInBackground(String... payloads) {
            for (int i = 0; i < payloads.length; i++) {
                try {
                    URL url = new URL("https://locuspositioning.com/control/api/latest.php");

                    Authenticator.setDefault (new Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication (username, password.toCharArray());
                        }
                    });

                    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    conn.setReadTimeout(10000);
                    conn.setConnectTimeout(15000);
                    conn.setRequestMethod("POST");
                    conn.setDoInput(true);
                    conn.setDoOutput(true);

                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));

                    writer.write("data=" + payloads[i]);
                    writer.close();
                    os.close();

                    publishProgress("Result: " + conn.getResponseCode() + ", " + conn.getResponseMessage());
                } catch(Exception e)
                {
                    return "Error: " + e.getMessage();
                }
            }

            return "Success";
        }

        protected void onProgressUpdate(String... progress) {
            for(int i = 0; i < progress.length; ++i)
                Log.i("httprequest", progress[i]);
        }

        protected void onPostExecute(String result) {
            Log.i("httprequest", result);
        }
    }

    private Map<String, ScanEntry> rssi_map;
    private Timer timer;
    private final BluetoothAdapter mBluetoothAdapter;
    private final String macAddress;

    public LocusScanner(BluetoothAdapter bluetoothAdapter, String macAddress) {
        mBluetoothAdapter = bluetoothAdapter;
        this.macAddress = macAddress;
        rssi_map = new java.util.HashMap<String, ScanEntry>();
    }

    public void Clear()
    {
        rssi_map.clear();
    }

    public void PushMeasurements(String macAddress, final String username, final String password)
    {
        String measurement = "{ \"pushmeasurement\": { \"measurement\": { \"type\":\"wifi\", \"source\":\"" + macAddress + "\", \"measurements\":[";

        boolean first = true;

        for(Map.Entry<String, ScanEntry> entry:rssi_map.entrySet())
        {
            if(!first)
                measurement += ", ";
            first = false;

            measurement += "{ \"destination\": \"" +  entry.getKey().replace(":", "") + "\", \"count\": " + entry.getValue().getCount() + ", \"value\": " + entry.getValue().getAvgRssi() + "}";
        }

        measurement += "] } } }";

        rssi_map.clear();

        PostDataTask post_data_task = new PostDataTask(username, password);
        try
        {
            post_data_task.execute(URLEncoder.encode(measurement, "UTF-8"));
        } catch(Exception e)
        {
            Log.v("LocusScanner", "Error: " + e.getMessage());
        }
    }

    void AddResult(String address, int rssi)
    {
        if(rssi_map.get(address) == null)
            rssi_map.put(address, new ScanEntry());
        rssi_map.get(address).addRssi(rssi);
    }

    public void StartScan(String username, String password) {
        if(!mBluetoothAdapter.isEnabled())
            return;

        Clear();

        ScanSettings.Builder scanSettings_build = new ScanSettings.Builder();
        scanSettings_build.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        scanSettings_build.setReportDelay(100);
        scanSettings_build.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);

        //scan specified devices only with ScanFilter
        List<ScanFilter> scanFilters = new ArrayList<ScanFilter>();

        mBluetoothAdapter.getBluetoothLeScanner().startScan(scanFilters, scanSettings_build.build(), leScanCallback);

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                PushMeasurements(macAddress, username, password);
                mBluetoothAdapter.getBluetoothLeScanner().stopScan(leScanCallback);
            }
        }, 5000);
    }

    private String byteToHex(final byte[] hash)
    {
        Formatter formatter = new Formatter();
        for (byte b : hash)
        {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        void HandleScanResult(ScanResult sr) {
            byte[] scan_record = sr.getScanRecord().getManufacturerSpecificData(0x004C);

            if(scan_record != null)
                Log.i("ScanRecord", "Address: " + sr.getDevice().toString() + ", length = " + scan_record.length + ", record = " + byteToHex(scan_record));

            if(scan_record != null && scan_record.length == 23)
            {
                int major = (scan_record[18] & 0xFF) * 256 + (scan_record[19] & 0xFF);
                int minor = (scan_record[20] & 0xFF) * 256 + (scan_record[21] & 0xFF);

                Log.i("ScanRecord", "Address: " + sr.getDevice().toString() + ", major = " + major + ", minor: " + minor);
                Log.i("ScanRecord", "Address: " + sr.getDevice().toString() + ", major = " + major + ", minor: " + minor);
                AddResult("" + minor, sr.getRssi());
            }

            //AddResult(sr.getDevice().toString(), sr.getRssi());
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            HandleScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult sr : results) {
                HandleScanResult(sr);
            }
        }
    };

}

