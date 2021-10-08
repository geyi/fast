package com.kuaidi100.fast.server;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.strands.SuspendableRunnable;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.util.ParameterizedTypeImpl;

import java.lang.reflect.Type;
import java.util.List;

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

        long WORD_MASK = 0xffffffffffffffffL;
        System.out.println(WORD_MASK << 65);
        System.out.println(Long.toBinaryString(WORD_MASK << 65));
        System.out.println(Long.numberOfTrailingZeros(WORD_MASK << 65));
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
