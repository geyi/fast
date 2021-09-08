package com.kuaidi100.fast.server;

import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class AddNewRespData implements Delayed {
    private ChannelHandlerContext ctx;
    private int orderId;
    private long delayTime;

    public AddNewRespData() {
    }

    public AddNewRespData(ChannelHandlerContext ctx, int orderId, long delayTime) {
        this.ctx = ctx;
        this.orderId = orderId;
        setDelayTime(delayTime);
    }

    public ChannelHandlerContext getCtx() {
        return ctx;
    }

    public void setCtx(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public long getDelayTime() {
        return delayTime;
    }

    public void setDelayTime(long delayTime) {
        this.delayTime = System.currentTimeMillis() + (delayTime > 0 ? delayTime : 0);
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return delayTime - System.currentTimeMillis();
    }

    @Override
    public int compareTo(Delayed o) {
        AddNewRespData data = (AddNewRespData) o;
        return (this.delayTime < data.delayTime) ? -1 : ((this.delayTime == data.delayTime) ? 0 : 1);
    }
}
