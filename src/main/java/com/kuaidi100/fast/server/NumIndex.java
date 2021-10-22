package com.kuaidi100.fast.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class NumIndex {
    private static final int LONG_ADDRESSABLE_BITS = 6;
    private static final int BITS_PER_WORD = 1 << LONG_ADDRESSABLE_BITS;
    private static final long WORD_MASK = 0xffffffffffffffffL;
    private static final int CAPACITY = 209713664;
    private static final int LONG_CAPACITY = (CAPACITY >> LONG_ADDRESSABLE_BITS) + 1;

    private String filePath;
    private Map<Byte, long[]> positionMap = new HashMap<>((int) (10 / .75) + 1);

    public NumIndex(String filePath) {
        this.filePath = filePath;
        for (int i = 0; i < 10; i++) {
            positionMap.put(Byte.valueOf(String.valueOf(i)), new long[LONG_CAPACITY]);
        }
        init();
        init2();
    }

    public Map<Byte, long[]> getPositionMap() {
        return positionMap;
    }

    private void set(Byte num, int bitIndex) {
        long[] bitmap = positionMap.get(num);
        int longIndex = bitIndex >>> LONG_ADDRESSABLE_BITS;
        bitmap[longIndex] = bitmap[longIndex] | (1L << bitIndex);
    }

    public boolean get(Byte num, int bitIndex) {
        return (positionMap.get(num)[bitIndex >>> LONG_ADDRESSABLE_BITS] & (1L << bitIndex)) != 0;
    }

    public int nextBitIndex(Byte num, int bitIndex) {
        long[] words = positionMap.get(num);
        int u = bitIndex >>> LONG_ADDRESSABLE_BITS;
        if (u >= LONG_CAPACITY)
            return -1;

        long word = words[u] & (WORD_MASK << bitIndex);

        while (true) {
            if (word != 0)
                return (u * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
            if (++u == LONG_CAPACITY)
                return -1;
            word = words[u];
        }
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

    private byte[] pi = new byte[CAPACITY];
    private void init2() {
        int pos = 2;
        int size = 40960;
        while (true) {
            if (pos >= CAPACITY) {
                break;
            }
            byte[] nums = read(pos, size);
            if (nums == null) {
                break;
            }

            System.arraycopy(nums, 0, pi, pos - 2, nums.length);

            int nextPos = Math.min(CAPACITY, pos + size);
            int nextSize;
            if ((nextSize = CAPACITY - nextPos) < size) {
                size = nextSize;
            }
            pos = nextPos;
        }
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

    public static void main(String[] args) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();
        NumIndex numIndex = new NumIndex("C:\\Users\\kuaidi100\\Desktop\\pi-200m.txt");
        System.out.println("初始化时间：" + (System.currentTimeMillis() - start) + "ms");

        /*for (int i = 0; i < 99; i++) {
            numIndex.set(Byte.valueOf("1"), i);
        }*/
        /*numIndex.print(Byte.valueOf("1"));
        int bitIndex = 0;
        int i = 100;
        while (i-- > 0) {
            System.out.println(bitIndex = numIndex.nextBitIndex(Byte.valueOf("1"), bitIndex));
            bitIndex++;
        }*/

        /*NumTrie numTrie = new NumTrie();
        String path = "C:\\Users\\kuaidi100\\Desktop\\test_data_10000.txt";
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line;
        List<Integer> indexList = new ArrayList<>(10000);
        while ((line = reader.readLine()) != null) {
            String[] arr = line.split(",");
            numTrie.add(arr[4]);
            indexList.add(Integer.valueOf(arr[1]));
        }

        int limit = CAPACITY - 110;
        int count = 0;
        int time = 0;
        for (int i = 0; i < limit; i++) {
            byte[] bytes = Arrays.copyOfRange(numIndex.pi, i, i + 110);
            String s = new String(bytes);
            start = System.currentTimeMillis();
            if (i == 48948194) {
                System.out.println();
            }
            if (numTrie.search(s)) {
//                System.out.println(i);
                *//*if (!indexList.contains(i) && !indexList.contains(i - 1)) {
                    System.out.println(i + "," + s);
                }*//*
                *//*int t;
                if (indexList.contains(t = i) || indexList.contains(t = i - 1) || indexList.contains(t = i + 1)) {
                    indexList.remove(Integer.valueOf(t));
                }*//*
                count++;
            }
            time += System.currentTimeMillis() - start;
        }
        System.out.println("耗时：" + time);
        System.out.println("找到：" + count);
        for (Integer integer : indexList) {
            System.out.println(integer);
        }*/


        /*start = System.currentTimeMillis();
        int range = 20000000;
        limit = (CAPACITY / range) + 1;
        CountDownLatch countDownLatch = new CountDownLatch(limit);
        for (int j = 0; j < limit; j++) {
            int finalJ = j;
            ThreadPoolUtils.getInstance().execute(() -> {
                int m = finalJ * range;
                int n = Math.min(CAPACITY, m + range);
                if (n == CAPACITY) {
                    n = CAPACITY - 110;
                }
                for (int i = m; i < n; i++) {
                    byte[] bytes = Arrays.copyOfRange(numIndex.pi, i, i + 110);
                    String s = new String(bytes);
                    if (numTrie.search(s)) {
                        System.out.println(i);
                    }
                }
                countDownLatch.countDown();
            });
        }
        countDownLatch.await();
        System.out.println("耗时：" + (System.currentTimeMillis() - start));*/

        /*for (Map.Entry<Byte, long[]> es : numIndex.positionMap.entrySet()) {
            Byte num = es.getKey();
            NumTrie.Node node = new NumTrie.Node(num);
            int idx = numTrie.getRoot().getNodes().indexOf(node);
            if (idx != -1) {
                continue;
            }
            long[] bitmap = es.getValue();
            int offset = -1;
            NumTrie.Node parent = numTrie.getRoot();
            while ((offset = numIndex.nextBitIndex(num, ++offset)) != -1) {
                System.out.println(offset); // 31
                // 判断32位是否有
                NumTrie.Node numNode = parent.getNodes().get(idx);
                for (NumTrie.Node nn : numNode.getNodes()) {
                    byte value = nn.getValue();
                    if (numIndex.get(value, offset + 1)) {

                    }
                }
            }
        }*/




        String path = "C:\\Users\\kuaidi100\\Desktop\\test_data_500.txt";
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line;
        int totalTime = 0;
        while ((line = reader.readLine()) != null) {
            String[] arr = line.split(",");
            long time = System.currentTimeMillis();
            int idx = numIndex.getOffset(arr[4], 1000000);
//            int idx = numIndex.getOffset(arr[3]);
            time = System.currentTimeMillis() - time;
            totalTime += time;
            if (((idx + 1) != Integer.parseInt(arr[0]))) {
                StringBuilder builder = new StringBuilder();
                builder.append("ret:" + ((idx) == Integer.parseInt(arr[1])))
                        .append(" index:")
                        .append(idx)
                        .append(" test index:")
                        .append(arr[1])
                        .append(" time:")
                        .append(time)
                        .append(" query:")
                        .append(arr[4]);
                System.out.println(builder.toString());
            }
        }
        System.out.println("平均耗时：" + totalTime / 500);

        /*long start2 = System.currentTimeMillis();
        String s =
                "0597097089755998121155833587363209162968769330389105362671915650620999330716376827434943697812700528";
        System.out.println(numIndex.getOffset(s));
        System.out.println("搜索时间：" + (System.currentTimeMillis() - start2) + "ms");*/

        /*System.out.println(numIndex.get(Byte.valueOf("1"), 5975514));*/

    }

    public int getOffset(String s) {
        int index = 0;
        Byte num = Byte.valueOf(s.substring(index, 1));
        for (int i = 0; i != -1 && i < CAPACITY; i = nextBitIndex(num, ++i)) {
            if (i == 98620896) {
                System.out.println(new String(Arrays.copyOfRange(pi, i, i + 110)));

            }
            int offset = i;
            if (check(s, index, offset, 0)) {
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

                for (int i = m; i != -1 && i < n && !success.get(); i = nextBitIndex(num, ++i)) {
                    if (check(s, index, i)) {
                        result.set(i);
                        success.set(true);
                        countDownLatch.countDown();
                    }
                }
            });
        }
        try {
            countDownLatch.await(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return result.get();
    }

    private boolean check(String s, int index, int offset) {
        if (index > s.length() - 80) {
            return true;
        }
        Byte num = Byte.valueOf(s.substring(index + 1, index + 2));
        int tmpOffset;
        if (!get(num, (tmpOffset = offset + 1))) {
            if (index == 0) {
                return false;
            }
            if (!get(num, (tmpOffset = offset + 2))) {
                return false;
            }
        }
        return check(s, index + 1, tmpOffset);
    }

    private boolean check(String s, int index, int offset, int jumpCount) {
        if (index > s.length() - 2) {
            return true;
        }
        Byte num = Byte.valueOf(s.substring(index + 1, index + 2));
        int tmpOffset;
        if (!get(num, (tmpOffset = offset + 1))) {
            if (index == 0 || jumpCount >= 9) {
                return false;
            } else {
                jumpCount++;
            }
            if (!get(num, (tmpOffset = offset + 2))) {
                return false;
            }
        }
        return check(s, index + 1, tmpOffset, jumpCount);
    }

}
