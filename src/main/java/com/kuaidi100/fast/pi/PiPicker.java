package com.kuaidi100.fast.pi;

import com.kuaidi100.fast.server.Constant;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class PiPicker {
    public static void main(String[] args) throws IOException {
        String piFile = Constant.BASE_PATH + "pi-200m.txt";
        String dirPath = Constant.BASE_PATH + "pi";
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        InputStream in = new FileInputStream(piFile);
        byte[] buff = new byte[1024 * 16];
        in.read(new byte[2]);
        Map<Integer, DataOutputStream> outputs = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            FileOutputStream fos = new FileOutputStream(new File(dir, String.valueOf(i)));
            outputs.put(i, new DataOutputStream(fos));
        }
        int index = 0;
        while (true) {
            int c = in.read(buff);
            if (c > 0) {
                for (int i = 0; i < c; i++) {
                    DataOutputStream dos = outputs.get(buff[i] - 48);
                    dos.writeInt(index++);
                }
            } else {
                break;
            }
            System.out.println("pick index:" + index);
        }
        for (DataOutputStream dos : outputs.values()) {
            dos.close();
        }
    }
}
