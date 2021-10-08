package com.kuaidi100.fast.pi;

public interface PiWarehouse {
    //    private final static ConcurrentHashMap<Integer, BitSet> warehouse;
//    static {
//        warehouse = new ConcurrentHashMap<>(10);
//    }
    void addNumber(int num, int position);

    int indexOf(String query);
}
