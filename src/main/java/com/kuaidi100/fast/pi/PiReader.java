//package com.kuaidi100.fast.pi;
//
//import org.springframework.boot.context.properties.EnableConfigurationProperties;
//import org.springframework.stereotype.Component;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.RandomAccessFile;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.LinkedBlockingQueue;
//
//@Component
//@EnableConfigurationProperties({PiConfigProperties.class})
//public class PiReader {
//    private InputStream input;
//    private int readIndex;
//    private PiConfigProperties properties;
//    private BlockingQueue<List<int[]>> buffer;
//    private Thread[] threads;
//    private boolean started = false;
//    private final static List<int[]> END_FLAG = new ArrayList<>(0);
//
//    public PiReader(PiConfigProperties properties) {
//        this.properties = properties;
//    }
//
//
//    public List<int[]> readPi() throws InterruptedException, IOException {
//        if (input == null) {
//            input = new FileInputStream(properties.getPiFilePath());
//            input.read(new byte[2]);
//            readIndex = 0;
//        }
//        int len = 1024 * 16;
//        byte[] buff = new byte[len];
//        int c = input.read(buff);
//        List<int[]> list = null;
//        if (c > 0) {
//            list = new ArrayList<>(c);
//            for (int i = 0; i < c; i++) {
//                list.add(new int[]{readIndex++, buff[i] - 48});
//            }
//        } else {
//            input.close();
//            input = null;
//        }
//        return list;
//    }
//
//    private void split() throws IOException {
//        File file = new File(properties.getPiFilePath());
//        File dir = new File(properties.getPiFileDir());
//        if (!dir.exists()) {
//            dir.mkdirs();
//        }
//        InputStream input = new FileInputStream(file);
//        input.read(new byte[2]);
//        long len = file.length();
//        int readIndex = 0;
//        int fileIndex = 0;
//        byte[] buff = new byte[8192];
//        long duration = len / properties.getReaderCount();
//        FileOutputStream fos = new FileOutputStream(new File(dir, fileIndex + ""));
//        while (true) {
//            int c = input.read(buff);
//            if (c > 0) {
//                fos.write(buff, 0, c);
//                readIndex += c;
//                if (readIndex >= duration * (fileIndex + 1)) {
//                    fos.close();
//                    fos = new FileOutputStream(new File(dir, readIndex + ""));
//                }
//            }
//        }
//    }
//
//    private void start() throws InterruptedException {
//        if (!started) {
//            buffer = new LinkedBlockingQueue<>();
//            threads = new Thread[properties.getReaderCount()];
//            long fileLength = new File(properties.getPiFilePath()).length();
//            long duration = fileLength / properties.getReaderCount();
//            CountDownLatch startLatch = new CountDownLatch(properties.getReaderCount() + 1);
//            CountDownLatch finishLatch = new CountDownLatch(properties.getReaderCount());
//            for (int i = 0; i < properties.getReaderCount(); i++) {
//                long begin = duration * i;
//                if (i == 0) {
//                    begin = 2;
//                }
//                long end = duration * (i + 1);
//                if (i == properties.getReaderCount() - 1) {
//                    end = fileLength + 1;
//                }
//                System.out.println("duration:" + i + " begin:" + begin + " end:" + end);
//                threads[i] = new Thread(new ReaderThread(begin, end, startLatch, finishLatch));
//                threads[i].start();
//            }
//            new Thread(() -> {
//                try {
//                    startLatch.countDown();
//                    System.out.println("read pi finshed");
//                    finishLatch.await();
//                    buffer.put(END_FLAG);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }).start();
//            startLatch.await();
//            started = true;
//        }
//    }
//
//    private class ReaderThread implements Runnable {
//        private long begin;
//        private long end;
//        private long index;
//        private CountDownLatch startLatch;
//        private CountDownLatch finishLatch;
//        private ReaderThread(long begin, long end, CountDownLatch startLatch,CountDownLatch finishLatch) {
//            this.begin = begin;
//            this.index = begin;
//            this.end = end;
//            this.startLatch = startLatch;
//            this.finishLatch = finishLatch;
//        }
//        @Override
//        public void run() {
//            try {
//                startLatch.countDown();
//                RandomAccessFile input = new RandomAccessFile(properties.getPiFilePath(), "r");
//                input.seek(begin);
//                while (true) {
//                    if (index >= end) {
//                        System.out.println("reader thread finished.index:" + index);
//                        break;
//                    }
//                    long len = end - index;
//                    len = len > 8192 ? 8192 : len;
//                    byte[] buff = new byte[(int) len];
//                    int c = input.read(buff);
//                    List<int[]> list = new ArrayList<>(c);
//                    for (byte b : buff) {
////                        buffer.offer(new int[]{(int) index++, b - 48});
//                        list.add(new int[]{(int) index++, b - 48});
//                    }
//                    buffer.put(list);
//                }
//                finishLatch.countDown();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//}
