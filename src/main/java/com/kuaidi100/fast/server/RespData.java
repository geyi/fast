package com.kuaidi100.fast.server;

import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class RespData implements Delayed {
    ChannelHandlerContext ctx;
    long delayTime;

    public ChannelHandlerContext getCtx() {
        return ctx;
    }

    public void setCtx(ChannelHandlerContext ctx) {
        this.ctx = ctx;
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
        RespData data = (RespData) o;
        return (this.delayTime < data.delayTime) ? -1 : ((this.delayTime == data.delayTime) ? 0 : 1);
    }
}
