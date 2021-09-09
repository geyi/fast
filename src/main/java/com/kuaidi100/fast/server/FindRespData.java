package com.kuaidi100.fast.server;

import io.netty.channel.ChannelHandlerContext;

public class FindRespData extends RespData {
    private byte[] data;

    public FindRespData(ChannelHandlerContext ctx, byte[] data, long delayTime) {
        super.ctx = ctx;
        this.data = data;
        setDelayTime(delayTime);
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    /*@Override
    public int compareTo(Delayed o) {
        FindRespData data = (FindRespData) o;
        return (delayTime < data.delayTime) ? -1 : ((delayTime == data.delayTime) ? 0 : 1);
    }*/
}
