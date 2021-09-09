package com.kuaidi100.fast.server;

import io.netty.channel.ChannelHandlerContext;

public class AddNewRespData extends RespData {
    private int orderId;

    public AddNewRespData() {
    }

    public AddNewRespData(ChannelHandlerContext ctx, int orderId, long delayTime) {
        super.ctx = ctx;
        this.orderId = orderId;
        setDelayTime(delayTime);
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    /*@Override
    public int compareTo(Delayed o) {
        AddNewRespData data = (AddNewRespData) o;
        return (delayTime < data.delayTime) ? -1 : ((delayTime == data.delayTime) ? 0 : 1);
    }*/
}
