package com.kuaidi100.fast.pi;

import com.kuaidi100.fast.server.Constant;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.BitSet;
import java.util.Map;

public class PiLoader {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        File file = new File(Constant.BASE_PATH + "pi-format");
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
        Map<String, BitSet> map = (Map<String, BitSet>) ois.readObject();
//        for (String key:map.keySet()) {
//            System.out.println(key + ":[" + map.get(key) + "]");
//        }
        BitSet set = map.get("14");
        System.out.println(set.nextSetBit(0));
    }
}
