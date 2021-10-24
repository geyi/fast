package com.kuaidi100.fast.pi;

import com.kuaidi100.fast.server.Constant;
import com.kuaidi100.fast.server.PiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Component
@EnableConfigurationProperties({PiConfigProperties.class})
public class PiTreeWarehouse {
    @Autowired
    private PiConfigProperties config;
    private boolean init = false;
    private PiTree tree;
    private byte[] bytes;
    private ThreadPoolExecutor pool;

    @PostConstruct
    public void init() throws IOException {
        if (!init) {
            bytes = PiUtils.readPiBytes(config.getPiFilePath());
            tree = new PiTree(10);
            init = false;
            pool = new ThreadPoolExecutor(config.getQueryThreadCount(),
                    config.getQueryThreadCount(),
                    3000,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>());
            pool.prestartAllCoreThreads();
        }
    }

    public PiTree.Pattern addPattern(String pattern) {
        return tree.addPattern(pattern);
    }

    public void addPattern(PiTree.Pattern pattern) {
        tree.addPattern(pattern);
    }

    public Map<PiTree.Pattern, Integer> indexOf() {
        int duration = (bytes.length + bytes.length % config.getQueryThreadCount()) / config.getQueryThreadCount();
        CountDownLatch latch = new CountDownLatch(config.getQueryThreadCount());
//        Map<PiTree.Pattern, Map<Integer, Integer>> map = new ConcurrentHashMap<>();
        BlockingQueue<Map<PiTree.Pattern, Map<Integer, Integer>>> queue = new LinkedBlockingQueue<>();
//        Map<PiTree.Pattern, Integer> result = new ConcurrentHashMap<>();
        for (int i = 0; i < config.getQueryThreadCount(); i++) {
            int idx = i;
            pool.execute(() -> {
                try {
                    queue.put(tree.matches(bytes, duration * idx, duration));
                } catch (InterruptedException e) {
                    Thread.interrupted();
                }
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.interrupted();
        }
        Map<PiTree.Pattern, Map<Integer, Integer>> tmp = new HashMap<>();
        for (Map<PiTree.Pattern, Map<Integer, Integer>> map : queue) {
            for (PiTree.Pattern p : map.keySet()) {
                if (p.getPattern().equals("9778072368065967688546664343242642487637511353186970458447698439665086862025882990894263995285000655")) {
                    Map<Integer, Integer> integerIntegerMap = map.get(p);
                    System.out.println(integerIntegerMap);
                }
                Map<Integer, Integer> m = map.get(p);
                Map<Integer, Integer> mt = tmp.get(p);
                if (mt == null) {
                    mt = new HashMap<>();
                    tmp.put(p, mt);
                }
                for (Integer i : m.keySet()) {
                    if (mt.containsKey(i)) {
                        Integer cnt = mt.get(i);
                        cnt += m.get(i);
                        mt.put(i, cnt);
                    } else {
                        mt.put(i, m.get(i));
                    }
                }
            }
        }
        return tree.getPatternIndex(tmp, bytes);
//        return result;
    }

    public Map<PiTree.Pattern, Integer> indexOf(int begin, int length) {
        return tree.getPatternIndex(tree.matches(bytes, begin, length), bytes);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        PiTreeWarehouse warehouse = new PiTreeWarehouse();
        PiConfigProperties properties = new PiConfigProperties();
        properties.setQueryThreadCount(16);
        warehouse.config = properties;
        warehouse.init();
        Map<PiTree.Pattern, Integer> fileMap = new HashMap<>();
        Map<String, Integer> sortMap = new HashMap<>();
        BufferedReader reader = new BufferedReader(new FileReader(Constant.BASE_PATH + "test_data_200000.txt"));
//        BufferedReader reader = new BufferedReader(new FileReader(Constant.BASE_PATH + "test_data_200000.txt"));
        String line = null;
        Counter buildTree = new Counter("buildTree", true);
        while ((line = reader.readLine()) != null) {
            String[] arr = line.split(",");
            buildTree.start();
            PiTree.Pattern pattern = warehouse.addPattern(arr[4]);
            buildTree.stop();
            fileMap.put(pattern, Integer.parseInt(arr[1]));
            sortMap.put(arr[4], Integer.valueOf(arr[5]));
        }
//        PiTree.Pattern pattern = warehouse.addPattern("8242595016063230024667017845566328988856305349583825187905573445335085703316283036345815957894841883");
//        fileMap.put(pattern, 16334543);
        long start = System.currentTimeMillis();
        Map<PiTree.Pattern, Integer> result = warehouse.indexOf();
//        Map<PiTree.Pattern, Integer> result = warehouse.indexOf(0, warehouse.bytes.length);
        start = System.currentTimeMillis() - start;
        System.out.println("匹配耗时：" + start);
        StringBuilder builder = new StringBuilder();
        StringBuilder errorResult = new StringBuilder();
        int error = 0;
        /*List<Map.Entry<PiTree.Pattern, Integer>> list = new ArrayList<>();
        for (Map.Entry<PiTree.Pattern, Integer> es : result.entrySet()) {
            list.add(es);
        }
        list.sort((o1, o2) -> {
            return o1.getValue().compareTo(o2.getValue());
        });
        int count = 0;
        for (int i = 0, limit = list.size(); i < limit; i++) {
            Map.Entry<PiTree.Pattern, Integer> es = list.get(i);
            String key = es.getKey().getPattern();
            Integer sort = sortMap.get(key);
            if (i + 1 != sort) {
//                System.out.println(key);
                count++;
            }
        }
        System.out.println(count);
        System.out.println("end!");*/
        for (PiTree.Pattern key : fileMap.keySet()) {
//            System.out.println(fileMap.get(key) + "," + result.get(key) + "," + key.getPattern());

            /*builder.append(fileMap.get(key))
                    .append(",")
                    .append(result.get(key))
                    .append(",")
                    .append(key.getPattern())
                    .append("\n");*/
            Integer r = result.get(key);
            Integer s = fileMap.get(key);
            if (r == null || r == -1 || !r.equals(s)) {
                error++;
                errorResult.append(fileMap.get(key))
                        .append(",")
                        .append(result.get(key))
                        .append(",")
                        .append(key.getPattern())
                        .append("\n");
            }
        }
        System.out.println(errorResult);
//        PrintWriter writer = new PrintWriter(new FileWriter(Constant.BASE_PATH + "result.txt"));
//        writer.write(builder.toString());
//        writer.close();
//        PrintWriter errorWriter = new PrintWriter(new FileWriter(Constant.BASE_PATH + "error.txt"));
//        errorWriter.write(errorResult.toString());
//        errorWriter.close();
//        LogUtils.debug("total|{}|{}|{}|{}", buildTree, start, error, fileMap.size());
        new CountDownLatch(1).await();
    }

}
