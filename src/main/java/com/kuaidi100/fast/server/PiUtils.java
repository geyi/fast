package com.kuaidi100.fast.server;

import java.io.*;
import java.util.*;

public class PiUtils {

    public static byte[] readPiBytes(String piFilePath) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buff = new byte[1024 * 16];
        FileInputStream fis = new FileInputStream(piFilePath);
        fis.read(new byte[2]);
        while (true) {
            int c = fis.read(buff);
            if (c <= 0) {
                break;
            }
            bos.write(buff, 0, c);
        }
        return bos.toByteArray();
    }
    public static String readPi(String piFilePath) throws IOException {
        StringBuilder builder = new StringBuilder();
        FileInputStream fis = new FileInputStream(piFilePath);
        fis.read(new byte[2]);
        byte[] buff = new byte[1024 * 16];
        while (true) {
            int c = fis.read(buff);
            if (c <= 0) {
                break;
            }
            builder.append(new String(buff, 0, c));
        }
        return builder.toString();
    }

    public static String readPi(int pos, int length, String piFilePath) throws IOException {
        return readPi(piFilePath).substring(pos, pos + length);
    }

    private static void buildPiFile(int count) throws IOException {
        String pi = readPi(Constant.BASE_PATH + "pi-200m.txt");
        Random random = new Random();
        int srcLen = 109;
        int patterLen = 100;
        List<PiData> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            PiData data = new PiData();
            data.id = i + 1;
            data.index = random.nextInt(pi.length() - 110);
            data.pattern = pi.substring(data.index, data.index + srcLen);
            data.source = data.pattern;
            data.prefix = data.pattern.substring(0, 2);
            data.miss = random.nextInt(9);
            StringBuilder builder = new StringBuilder();
            if (data.miss == 0) {
                builder.append(data.pattern);
            } else {
                int off = 0;
                for (int j = 0; j < data.miss; j++) {
                    int pos = random.nextInt(11);
                    pos = 12 * j + pos + 2;
                    if (off + 1 == pos) {
                        pos += 1;
                        System.out.println(off + "/" + pos);
                    }
                    String substring = data.pattern.substring(off, pos);
                    if (substring.length() == 1) {
                        System.out.println(substring);
                    }
                    builder.append(substring);
                    data.missPos.add(pos);
                    off = pos + 1;
                }
                if (off < data.pattern.length()) {
                    builder.append(data.pattern.substring(off));
                }
            }
            builder.setLength(patterLen);
            data.pattern = builder.toString();
            list.add(data);
        }
        list.sort(Comparator.comparing(o -> o.index));
        for (int i = 0; i < list.size(); i++) {
            list.get(i).orderId = i + 1;
        }
        list.sort(Comparator.comparing(o -> o.id));
        String fileName = Constant.BASE_PATH + "test_data_" + count + ".txt";
        BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
        for (PiData data : list) {
            String content = String.format("%d,%d,%s,%d,%s,%d\n",
                    data.id,
                    data.index,
                    data.prefix,
                    data.miss,
                    data.pattern,
                    data.orderId);
            writer.write(content);
//            System.out.println(String.format("%d,%d,%s,%d,%s,%s",
//                    data.id,
//                    data.index,
//                    data.prefix,
//                    data.miss,
//                    data.source,
//                    data.missPos.toString()));
        }
        writer.close();
    }

    private static class PiData {
        private int id;
        private int index;
        private String prefix;
        private int miss;
        private String pattern;
        private int orderId;
        private String source;
        private List<Integer> missPos = new ArrayList<>();
    }

    public static void main(String[] args) throws IOException {
        buildPiFile(10000);
        System.out.println("success");
//        String pi = readPi("config/pi-200m.txt");
//        int pos = 53358517;
//        System.out.println(pi.substring(pos, pos + 109));
//        pos = 53358534;
//        System.out.println(pi.substring(pos, pos + 109));
    }
}
