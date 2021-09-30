package com.kuaidi100.fast.server;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class NumIndex {
    private static final int LONG_ADDRESSABLE_BITS = 6;
    private static final int CAPACITY = 209713664;

    private String filePath;
    private Map<Byte, long[]> positionMap = new HashMap<>((int) (10 / .75) + 1);

    public NumIndex(String filePath) {
        this.filePath = filePath;
        for (int i = 0; i < 10; i++) {
            positionMap.put(Byte.valueOf(String.valueOf(i)), new long[3276800]);
        }
        init();
    }

    private void set(Byte num, int bitIndex) {
        long[] bitmap = positionMap.get(num);
        int longIndex = bitIndex >>> LONG_ADDRESSABLE_BITS;
        bitmap[longIndex] = bitmap[longIndex] | (1L << bitIndex);
    }

    private boolean get(Byte num, int bitIndex) {
        return (positionMap.get(num)[bitIndex >>> LONG_ADDRESSABLE_BITS] & (1L << bitIndex)) != 0;
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
        int pos = 2;
        int size = 40960;
        int rangeSize = 20000000;
        int limit = CAPACITY / rangeSize + 1;
        CountDownLatch countDownLatch = new CountDownLatch(limit);
        for (int i = 0; i < limit; i++) {
            int rangeLimit = Math.min(rangeSize * (i + 1), CAPACITY);
            int finalPos = pos;
            ThreadPoolUtils.getInstance().execute(() -> task(finalPos, size, rangeLimit, countDownLatch));
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
                set(Byte.valueOf(String.valueOf(num)), bitIndex);
            }
            int nextPos = Math.min(rangeSize, pos + size);
            int nextSize;
            if ((nextSize = rangeSize - nextPos) < size) {
                size = nextSize;
            }
            pos = nextPos;

        }
        countDownLatch.countDown();
    }

    private byte[] read(int position, int size) {
        File file = new File(filePath);
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
        File file = new File(filePath);
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
        NumIndex numIndex = new NumIndex("C:\\Users\\GEYI\\Desktop\\TMP\\pi-200m.txt");
        System.out.println("初始化时间：" + (System.currentTimeMillis() - start) + "ms");

        long start2 = System.currentTimeMillis();
        String s =
                "0694055217886917382314463050191608201773165461843981290739460517266863978766407209146507654911588610";
        System.out.println(numIndex.getOffset(s, 1000000));
        System.out.println("搜索时间：" + (System.currentTimeMillis() - start2) + "ms");

    }

    public int getOffset(String s) {
        int index = 0;
        Byte num = Byte.valueOf(s.substring(index, 1));
        for (int i = 0; i < CAPACITY; i++) {
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

    public int getOffset(String s, int range) {
        AtomicInteger result = new AtomicInteger(-1);
        AtomicBoolean success = new AtomicBoolean(false);
        CountDownLatch countDownLatch = new CountDownLatch(1);

        int index = 0;
        Byte num = Byte.valueOf(s.substring(index, 1));
        int limit = (CAPACITY / range) + 1;
        for (int j = 0; j < limit; j++) {
            int finalJ = j;
            ThreadPoolUtils.getInstance().execute(() -> {
                int m = finalJ * range;
                int n = Math.min(CAPACITY, m + range);
                for (int i = m; i < n && !success.get(); i++) {
                    if (!get(num, i)) {
                        continue;
                    }
                    if (check(s, index, i)) {
                        result.set(i);
                        success.set(true);
                        countDownLatch.countDown();
                    }
                }
            });
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return result.get();
    }

    private boolean check(String s, int index, int offset) {
        if (index > s.length() - 2) {
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
