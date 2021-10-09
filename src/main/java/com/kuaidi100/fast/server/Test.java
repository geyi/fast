package com.kuaidi100.fast.server;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.SuspendableRunnable;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.util.ParameterizedTypeImpl;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Test {
    public static void main(String[] args) throws Exception {
        /*long start = System.currentTimeMillis();
        int size = 10000;
        Fiber<Void>[] fibers = new Fiber[size];
        for (int i = 0; i < fibers.length; i++) {
            fibers[i] = new Fiber<>((SuspendableRunnable) () -> calc());
        }
        for (int i = 0; i < fibers.length; i++) {
            fibers[i].start();
        }
        for (int i = 0; i < fibers.length; i++) {
            fibers[i].join();
        }
        long end = System.currentTimeMillis();
        System.out.println(end - start);

        Fiber<Object> objectFiber = new Fiber<>();*/


        /*String s = "{\n" +
                "\t\"data\":{\n" +
                "\t\t\"name\":\"username\",\n" +
                "\t\t\"value\":\"root\"\n" +
                "\t},\n" +
                "\t\"msg\":\"Success\",\n" +
                "\t\"ret\":0\n" +
                "}";
        Result<List<Item>> listResult = parseListResult(s, Item.class);
        System.out.println(listResult);*/

        /*long WORD_MASK = 0xffffffffffffffffL;
        System.out.println(WORD_MASK << 65);
        System.out.println(Long.toBinaryString(WORD_MASK << 65));
        System.out.println(Long.numberOfTrailingZeros(WORD_MASK << 65));*/

        FileReader fileReader = new FileReader("C:\\Users\\kuaidi100\\Desktop\\nohup.log");
        BufferedReader bufferedReader = new BufferedReader(fileReader);

        String s;
        Map<Integer, String> map = new TreeMap<>();
        while ((s = bufferedReader.readLine()) != null) {
            if (!s.contains("resp")) {
                continue;
            }
            String[] split = s.split("\\|");
            map.put(Integer.valueOf(split[6]), split[7]);
        }
        System.out.println(map);
        System.out.println(map.size());

        Map<Integer, String> sourceMap = new TreeMap<>();
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i <= 3; i++) {
            fileReader = new FileReader("C:\\Users\\kuaidi100\\Desktop\\test_data_orderid_" + i + ".txt");
            bufferedReader = new BufferedReader(fileReader);
            while ((s = bufferedReader.readLine()) != null) {
                String[] split = s.split(",");
                sourceMap.put(Integer.valueOf(split[0]), split[5]);
                list.add(Integer.valueOf(split[1]));
            }
        }
        System.out.println(sourceMap);
        list.sort((o1, o2) -> {
            return o1.compareTo(o2);
        });
        int num = 0;
        for (Integer integer : list) {
            System.out.println(++num + "|" + integer);
        }

        for (int i = 1; i <= 3000; i++) {
            if (!map.get(i).equals(sourceMap.get(i))) {
                System.out.println(i + "|" + map.get(i) + "|" + sourceMap.get(i));
            }
        }
    }

    private static <T> Result<List<T>> parseListResult(String json, Class<T> clazz) {
        return JSONObject.parseObject(json, buildType(Result.class, List.class, clazz));
    }

    private static Type buildType(Type... types) {
        ParameterizedTypeImpl beforeType = null;
        if (types != null && types.length > 0) {
            for (int i = types.length - 1; i > 0; i--) {
                beforeType = new ParameterizedTypeImpl(new Type[]{beforeType == null ? types[i] : beforeType}, null, types[i - 1]);
            }
        }
        return beforeType;
    }

    public static class Result<T> {
        private int ret;
        private String msg;
        private T data;

        public int getRet() {
            return ret;
        }

        public void setRet(int ret) {
            this.ret = ret;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }
    }

    public static class Item {
        private String name;
        private String value;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    static void calc() {
        int result = 0;
        for (int m = 0; m < 10000; m++) {
            for (int i = 0; i < 200; i++) result += i;
        }
    }
}
