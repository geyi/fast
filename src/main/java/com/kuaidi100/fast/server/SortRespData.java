package com.kuaidi100.fast.server;

import io.netty.channel.ChannelHandlerContext;

import java.util.Objects;

public class SortRespData extends RespData {
    private int id;
    private int indexId;
    private int orderId;

    public SortRespData() {
    }

    public SortRespData(ChannelHandlerContext ctx, int id, long delayTime) {
        super.ctx = ctx;
        this.id = id;
        setDelayTime(delayTime);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIndexId() {
        return indexId;
    }

    public void setIndexId(int indexId) {
        this.indexId = indexId;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SortRespData that = (SortRespData) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
