package com.kuaidi100.fast.pi;

public class Counter {
    private static boolean TIMING = false;
    private String name;
    private long start;
    private long cumulative;
    private long times;
    private boolean forceTiming;

    public Counter(String name) {
        this(name, false);
    }

    public Counter(String name, boolean forceTiming) {
        this.name = name;
        start = 0;
        cumulative = 0;
        times = 0;
        this.forceTiming = forceTiming || TIMING;
    }

    public void start() {
        if (forceTiming) {
//            if (start != 0) {
//                stop();
//            }
            start = System.nanoTime();
        }
    }

    public void stop() {
        if (forceTiming) {
            cumulative += System.nanoTime() - start;
            start = 0;
            times++;
        }
    }

    public long cumulative() {
        return cumulative;
    }

    public long average() {
        return times == 0 ? 0 : cumulative / times;
    }

    public long times() {
        return times;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("(")
                .append(name)
                .append(":")
                .append(times)
                .append(":")
                .append(average())
                .append(":")
                .append(cumulative)
                .append(")");
        return builder.toString();
    }

    public void setForceTiming(boolean forceTiming) {
        this.forceTiming = forceTiming;
    }

    public static void setTiming(boolean timing) {
        TIMING = timing;
    }
}
