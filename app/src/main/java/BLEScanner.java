import com.example.wkl04.BLEScan.MainActivity;

import java.util.Map;

/**
 * Created by wkl04 on 27-11-2016.
 */

public class BLEScanner {
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

    private Map<String, ScanEntry> rssi_map;

    public BLEScanner() {
        rssi_map = new java.util.HashMap<String, ScanEntry>();
    }
}
