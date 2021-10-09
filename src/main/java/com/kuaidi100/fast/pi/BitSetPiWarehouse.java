package com.kuaidi100.fast.pi;

import com.kuaidi100.fast.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BitSetPiWarehouse implements PiWarehouse {

    private static final Logger log = LoggerFactory.getLogger(BitSetPiWarehouse.class);

    @Autowired
    private PiConfigProperties properties;
    private ConcurrentHashMap<String, BitSet> doubleMap;
    private ConcurrentHashMap<Integer, BitSet> singleMap;

    @PostConstruct
    public void init() throws IOException, InterruptedException {
        doubleMap = new ConcurrentHashMap<>();
        singleMap = new ConcurrentHashMap<>();
        loadPi();
    }

    private void loadPi() throws IOException {
        File file = new File(properties.getPiFilePath());
        InputStream in = new FileInputStream(file);
        in.read(new byte[2]);
        byte[] buff = new byte[1024 * 16];
        int index = 0;
        int last = -1;
        int c = 0;
        int crt = -1;
        String key = null;
        while (true) {
            c = in.read(buff);
            if (c > 0) {
                for (int i = 0; i < c; i++) {
                    crt = buff[i] - 48;
                    if (last != -1) {
                        key = last + "" + crt;
                        BitSet set = doubleMap.get(key);
                        if (set == null) {
                            set = new BitSet(properties.getPiLength());
                            doubleMap.put(key, set);
                        }
                        set.set(index - 1, true);
                    }
                    BitSet set = singleMap.get(crt);
                    if (set == null) {
                        set = new BitSet(properties.getPiLength());
                        singleMap.put(crt, set);
                    }
                    set.set(index, true);
                    last = crt;
                    index++;
                }
                log.debug("load pi index:{}", index);
            } else {
                break;
            }
        }
    }

    @Override
    public void addNumber(int num, int position) {

    }

    @Override
    public int indexOf(String query) {
        String prefix = query.substring(0, 2);
        int tail = query.charAt(query.length() - 1) - 48;
        BitSet prefixSet = doubleMap.get(prefix);
        BitSet tailSet = singleMap.get(tail);
        int idx = 0;
        int tIdx = -1;
        int tailOffset = properties.getQueryLength() + properties.getMaxAllowMissCount();
        while ((idx = prefixSet.nextSetBit(idx)) >= 0) {
            int loopIdx = idx;
            idx++;
            if (tIdx > 0 && loopIdx < (tIdx - tailOffset)) {
                continue;
            }
            tIdx = tailSet.nextSetBit(loopIdx + properties.getQueryLength() - 1);
            if (tIdx > loopIdx + tailOffset) {
                continue;
            }
            int miss = 0;
            boolean flag = true;
            int cIdx = loopIdx + 2;
            for (int i = 2; i < query.length(); i++) {
                char c = query.charAt(i);
                int ic = c - 48;
                BitSet set = singleMap.get(ic);
                int ccIdx = set.nextSetBit(cIdx);
                if (ccIdx == cIdx) {
                    cIdx = ccIdx + 1;
                    continue;
                } else if (ccIdx == (cIdx + 1)) {
                    if (miss > 9) {
                        flag = false;
                        break;
                    }
                    cIdx = ccIdx + 1;
                    miss++;
                    continue;
                } else {
                    flag = false;
                    break;
                }
            }
            if (flag) {
                return loopIdx;
            }
        }

        return -1;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        BitSetPiWarehouse warehouse = new BitSetPiWarehouse();
        warehouse.properties = new PiConfigProperties();
        warehouse.init();
        String path = "C:\\Users\\kuaidi100\\Desktop\\test_data_orderid_500.txt";
        BufferedReader reader = new BufferedReader(new FileReader(path));
        reader.readLine();
        String line = null;
        int maxIdx = 0;
        String info = null;
        while ((line = reader.readLine()) != null) {
            String[] arr = line.split(",");
            long time = System.currentTimeMillis();
            int idx = warehouse.indexOf(arr[3]);
            time = System.currentTimeMillis() - time;
            StringBuilder builder = new StringBuilder();
            builder.append("index:")
                    .append(idx)
                    .append(" test index:")
                    .append(arr[0])
                    .append(" time:")
                    .append(time)
                    .append(" query:")
                    .append(arr[3]);
            System.out.println(builder.toString());
            if (idx > maxIdx) {
                maxIdx = idx;
                info = builder.toString();
            }
        }
        System.out.println("maxIndex:" + maxIdx + " " + info);
    }
}
