package com.kuaidi100.fast.pi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

public class PiFormat {
    public static void main(String[] args) throws IOException {
        Map<String, BitSet> map = new HashMap<>();
//        File pi = new File("config/pi-test.txt");
        File pi = new File("config/pi-200m.txt");
        InputStream in = new FileInputStream(pi);
        in.read(new byte[2]);
        byte[] buff = new byte[1024 * 16];
        int last = -1;
        int index = 0;
        while (true) {
            int c = in.read(buff);
            if (c > 0) {
                for (int i = 0; i < c; i++) {
                    int current = buff[i] - 48;
                    if (last != -1) {
                        String k = last + "" + current;
                        BitSet set = map.get(k);
                        if (set == null) {
                            set = new BitSet(250000000);
                            map.put(k, set);
                        }
                        int idx = index - 1;
                        set.set(idx, true);
                    }
                    index++;
                    last = current;
                }
                System.out.println("pick index:" + index);
            } else {
                break;
            }
//            if (index >= 2000000) {
//                break;
//            }
        }
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("config/pi-format"));
        oos.writeObject(map);
        oos.close();
    }
}
