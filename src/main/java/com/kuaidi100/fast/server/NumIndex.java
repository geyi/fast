package com.kuaidi100.fast.server;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class NumIndex {
    private static final int LONG_ADDRESSABLE_BITS = 6;
    private Map<Byte, long[]> positionMap = new HashMap<>((int) (10 / .75) + 1);

    public NumIndex() {
        for (int i = 0; i < 10; i++) {
            positionMap.put(Byte.valueOf(String.valueOf(i)), new long[3276800]);
        }
        init();
    }

    private void set(Byte num, int bitIndex) {
        long[] bitmap = positionMap.get(num);
        int longIndex = bitIndex >>> LONG_ADDRESSABLE_BITS;
        long oldValue = bitmap[longIndex];
        long newValue = oldValue | (1L << bitIndex);
        bitmap[longIndex] = newValue;
    }

    private boolean get(Byte num, int bitIndex) {
        long[] bitmap = positionMap.get(num);
        return (bitmap[(bitIndex >>> LONG_ADDRESSABLE_BITS)] & (1L << bitIndex)) != 0;
    }

    private void print(Byte num) {
        System.out.println(num);
        long[] value = positionMap.get(num);
        for (int i = 10; i >= 0; i--) {
            printBinary(value[i]);
            System.out.println("|");
        }
    }

    private void printBinary(long l) {
        for (long i = 63; i >= 0; i--) {
            System.out.print((l & (1L << i)) == 0 ? "0" : "1");
        }
    }

    private void init() {
        int capacity = 209713664;
        int pos = 2;
        int size = 40960;
        int rangeSize = 10000000;
        int limit = capacity / rangeSize + 1;
        CountDownLatch countDownLatch = new CountDownLatch(limit);
        for (int i = 0; i < limit; i++) {
            int rangeLimit = Math.min(rangeSize * (i + 1), capacity);
            int finalPos = pos;
            ThreadPoolUtils.getInstance().execute(() -> task(finalPos, size, rangeLimit, countDownLatch));
            System.out.println(pos + "/" + size + "/" + rangeLimit);
            pos = rangeLimit;
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void task(int pos, int size, int rangeSize, CountDownLatch countDownLatch) {
        while (true) {
            if (pos >= rangeSize) {
                break;
            }
            byte[] nums = read(pos, size);
            if (nums == null) {
                break;
            }

            for (int i = 0, len = nums.length; i < len; i++) {
                char num = (char) nums[i];
                int bitIndex = pos - 2 + i;
                /*System.out.println(num);
                System.out.println(bitIndex);*/
                set(Byte.valueOf(String.valueOf(num)), bitIndex);
            }
            int nextPos = Math.min(rangeSize, pos + size);
            int nextSize;
            if ((nextSize = rangeSize - nextPos) < size) {
                size = nextSize;
            }
            pos = nextPos;

        }
        System.out.println(pos + "/" + size);
        countDownLatch.countDown();
    }

    private byte[] read(int position, int size) {
        File file = new File("C:\\Users\\kuaidi100\\Desktop\\pi-200m.txt");
        RandomAccessFile randomAccessTargetFile = null;
        MappedByteBuffer map;
        try {
            randomAccessTargetFile = new RandomAccessFile(file, "r");
            FileChannel targetFileChannel = randomAccessTargetFile.getChannel();
            map = targetFileChannel.map(FileChannel.MapMode.READ_ONLY, position, size);
            byte[] bytes = new byte[size];
            map.get(bytes);
            return bytes;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error " + position + "/" + size);
        } finally {
            try {
                if (randomAccessTargetFile != null) {
                    randomAccessTargetFile.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private byte[] read2(int position, int size) {
        File file = new File("C:\\Users\\kuaidi100\\Desktop\\pi-200m.txt");
        RandomAccessFile randomAccessTargetFile = null;
        try {
            randomAccessTargetFile = new RandomAccessFile(file, "r");
            randomAccessTargetFile.seek(position);
            byte[] bytes = new byte[size];
            randomAccessTargetFile.read(bytes, 0, size);
            return bytes;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error " + position + "/" + size);
        } finally {
            try {
                if (randomAccessTargetFile != null) {
                    randomAccessTargetFile.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        NumIndex numIndex = new NumIndex();
        System.out.println("初始化时间：" + (System.currentTimeMillis() - start) + "ms");
        /*numIndex.printBinary(65535L);*/

        /*for (int i = 0; i < 99; i++) {
            numIndex.set(Byte.valueOf("1"), i);
        }
        numIndex.print(Byte.valueOf("1"));*/

//        numIndex.task(2, 2048, 0);
//        numIndex.print(Byte.valueOf("1"));
        long start2 = System.currentTimeMillis();
        String s =
                "7626296943170328152372416063616219376575405163726180481501340425225452284449550662725516608984270090";
        System.out.println(numIndex.getOffset(s));
        System.out.println("搜索时间：" + (System.currentTimeMillis() - start2) + "ms");

    }

    public int getOffset(String s) {
        int index = 0;
        Byte num = Byte.valueOf(s.substring(index, 1));
        for (int i = 0; i < 209713664; i++) {
            if (!get(num, i)) {
                continue;
            }
            int offset = i;
            if (check(s, index, offset)) {
                return offset;
            }
        }
        return -1;
    }

    private boolean check(String s, int index, int offset) {
        if (index > s.length() - 80) {
            return true;
        }
        Byte num = Byte.valueOf(s.substring(index + 1, index + 2));
        int tmpOffset;
        if (!get(num, (tmpOffset = offset + 1))) {
            if (!get(num, (tmpOffset = offset + 2))) {
                return false;
            }
        }
        return check(s, index + 1, tmpOffset);
    }

}
