package com.kuaidi100.fast.server;

import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.EventExecutor;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Server {

    private static String basePath;

    private static final NioEventLoopGroup BOSS = new NioEventLoopGroup(2);
    private static final NioEventLoopGroup WORKER = new NioEventLoopGroup(8);
    private static Channel channel;

    private static final AtomicInteger EE_NUM = new AtomicInteger(0);
    private static Map<Integer, EventExecutorAttach> eeAttachMap = new HashMap<>((int) (8 / .75) + 1);
    private static long getOrderId(EventExecutor ee) {
        EventExecutorAttach eventExecutorAttach = eeAttachMap.get(ee.hashCode());
        if (eventExecutorAttach == null) {
            int num = EE_NUM.getAndIncrement();
            eventExecutorAttach = new EventExecutorAttach((byte) num, ee, new AtomicLong(num * 10000000), 0);
            eeAttachMap.put(ee.hashCode(), eventExecutorAttach);
        }
        return eventExecutorAttach.getOrderId().incrementAndGet();
    }
    private static int getWriteIndex(EventExecutor ee) {
        EventExecutorAttach eventExecutorAttach = eeAttachMap.get(ee.hashCode());
        if (eventExecutorAttach == null) {
            int num = EE_NUM.getAndIncrement();
            eventExecutorAttach = new EventExecutorAttach(ee, new AtomicLong(num * 10000000), 0);
            eeAttachMap.put(ee.hashCode(), eventExecutorAttach);
        }
        return eventExecutorAttach.getWriteIndex();
    }
    private static void setWriteIndex(EventExecutor ee, int writeIndex) {
        eeAttachMap.get(ee.hashCode()).setWriteIndex(writeIndex);
    }

    private static Map<Byte, Map<Long, OrderData>> orderData = new ConcurrentHashMap<>((int) (8 / .75) + 1);
    private static void saveOrderData(EventExecutor ee, long orderId, OrderData od) {
        Byte num = eeAttachMap.get(ee.hashCode()).getNum();
        Map<Long, OrderData> odMap = orderData.get(num);
        if (odMap == null) {
            odMap = new HashMap<>(10000000);
            orderData.put(num, odMap);
        }
        odMap.put(orderId, od);
    }
    private static OrderData getOrderData(long orderId) {
        return orderData.get((byte) (orderId / 10000000)).get(orderId);
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public static NioEventLoopGroup getBoss() {
        return BOSS;
    }

    public static NioEventLoopGroup getWorker() {
        return WORKER;
    }

    public static Channel getChannel() {
        return channel;
    }

    public static void main(String[] args) throws Exception {
        new Server().startServer(9001);
    }

    public ChannelFuture startServer(int port) throws Exception {
        ServerBootstrap serverBootstrap = new ServerBootstrap().group(BOSS, WORKER)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(1 * 1024));
                        pipeline.addLast(new RequestHandler());
                    }
                });
        ChannelFuture bindFuture = serverBootstrap.bind(new InetSocketAddress(port));
        channel = bindFuture.sync().channel();
        System.out.println("server startup!!! bind port: " + port + ", basePath: " + basePath);
        return bindFuture;
    }

    public void destroy() {
        if (channel != null) {
            channel.close().syncUninterruptibly();
        }
        BOSS.shutdownGracefully();
        WORKER.shutdownGracefully();
        System.out.println("server destroy!!!");
    }

    static class RequestHandler extends ChannelInboundHandlerAdapter {
        private static final String SUCC_RESP = "{\"STATUS\":\"SUCCESS\",\"ORDER_ID\":%d}";
        private static final String ADD_NEW = "ADDNEW";
        private static final String FIND = "FINDBYID";
        private static final String ACTION = "ACTION";
        private static final String ORDER_ID = "ORDER_ID";

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            FullHttpRequest request = (FullHttpRequest) msg;
            if (!request.decoderResult().isSuccess()) {
                System.out.println("request decode is not success");
                this.sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }

            String uri = request.uri();
            Map<String, String> paramMap = this.parseQueryString(uri);
            String action = paramMap.get(ACTION);
            if (ADD_NEW.equals(action)) {
                this.addNew(request, ctx, paramMap);
            } else if (FIND.equals(action)) {
                this.findById(request, ctx, paramMap);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            if (ctx.channel().isActive()) {
                this.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        }

        private void addNew(FullHttpRequest request, ChannelHandlerContext ctx, Map<String, String> paramMap) {
            EventExecutor executor = ctx.executor();
            long orderId = getOrderId(executor);
            paramMap.put(ORDER_ID, String.valueOf(orderId));
            String orderInfo = JSONObject.toJSONString(paramMap);
            int length = orderInfo.getBytes().length;
            int writeIndex = getWriteIndex(executor);
            fileWrite(basePath + executor.hashCode(), orderInfo, writeIndex);
            setWriteIndex(executor, length);
            saveOrderData(executor, orderId, new OrderData(String.valueOf(executor.hashCode()), writeIndex, length));

            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            if (HttpUtil.isKeepAlive(request)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }

            byte[] resp = String.format(SUCC_RESP, orderId).getBytes();
            ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.directBuffer(resp.length);
            byteBuf.writeBytes(resp);
            response.content().writeBytes(byteBuf);
            byteBuf.release();
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        private void findById(FullHttpRequest request, ChannelHandlerContext ctx, Map<String, String> paramMap) {
            Long orderId = Long.valueOf(paramMap.get(ORDER_ID));
            OrderData order = getOrderData(orderId);
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            if (HttpUtil.isKeepAlive(request)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }

            byte[] bytes = fileRead(basePath + order.getFilename(), order.getOffset(), order.getLength());
            ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.directBuffer(bytes.length);
            byteBuf.writeBytes(bytes);
            response.content().writeBytes(byteBuf);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }

        private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
            HttpResponse errorResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                    Unpooled.copiedBuffer(
                            "Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));
            errorResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
            // 发送完成后关闭channel
            ctx.writeAndFlush(errorResponse).addListener(ChannelFutureListener.CLOSE);
        }

        private Map<String, String> parseQueryString(String uri) {
            Map<String, String> ret = new HashMap<>();
            /*try {
                uri = URLDecoder.decode(uri, "utf-8");
            } catch (Exception e) {
                try {
                    uri = URLDecoder.decode(uri, "iso-8859-1");
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }*/
            int i = uri.indexOf('?');
            String[] params = StringUtils.split(uri.substring(i + 1), '&');
            for (String param : params) {
                String[] kv = StringUtils.split(param, '=');
                ret.put(kv[0], kv[1]);
            }
            return ret;
        }

        public static long fileWrite(String filePath, String content, int index) {
            File file = new File(filePath);
            RandomAccessFile randomAccessTargetFile;
            MappedByteBuffer map;
            try {
                randomAccessTargetFile = new RandomAccessFile(file, "rw");
                FileChannel targetFileChannel = randomAccessTargetFile.getChannel();
                map = targetFileChannel.map(FileChannel.MapMode.READ_WRITE, 0, (long) 1024 * 1024 * 1024);
                map.position(index);
                map.put(content.getBytes());
                return map.position();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
            }
            return 0L;
        }

        public static String fileRead(String filePath, long index) {
            File file = new File(filePath);
            RandomAccessFile randomAccessTargetFile;
            //  操作系统提供的一个内存映射的机制的类
            MappedByteBuffer map;
            try {
                randomAccessTargetFile = new RandomAccessFile(file, "r");
                FileChannel targetFileChannel = randomAccessTargetFile.getChannel();
                map = targetFileChannel.map(FileChannel.MapMode.READ_ONLY, 0, index);
                byte[] byteArr = new byte[10 * 1024];
                map.get(byteArr, 0, (int) index);
                return new String(byteArr);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
            }
            return "";
        }

        public static byte[] fileRead(String filePath, int position, int length) {
            File file = new File(filePath);
            RandomAccessFile randomAccessTargetFile;
            MappedByteBuffer map;
            try {
                randomAccessTargetFile = new RandomAccessFile(file, "r");
                FileChannel targetFileChannel = randomAccessTargetFile.getChannel();
                map = targetFileChannel.map(FileChannel.MapMode.READ_ONLY, position, position + length);
                byte[] byteArr = new byte[length];
                map.get(byteArr);
                return byteArr;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
            }
            return null;
        }

        public static void main(String[] args) {
            System.out.println(fileRead("E:\\1390301622.txt", 2 * 1024));
        }
    }

    static class EventExecutorAttach {
        Byte num;
        EventExecutor ee;
        AtomicLong orderId;
        Integer writeIndex;

        public EventExecutorAttach(EventExecutor ee, AtomicLong orderId, Integer writeIndex) {
            this.ee = ee;
            this.orderId = orderId;
            this.writeIndex = writeIndex;
        }

        public EventExecutorAttach(Byte num, EventExecutor ee, AtomicLong orderId, Integer writeIndex) {
            this.num = num;
            this.ee = ee;
            this.orderId = orderId;
            this.writeIndex = writeIndex;
        }

        public Byte getNum() {
            return num;
        }

        public void setNum(Byte num) {
            this.num = num;
        }

        public EventExecutor getEe() {
            return ee;
        }

        public void setEe(EventExecutor ee) {
            this.ee = ee;
        }

        public AtomicLong getOrderId() {
            return orderId;
        }

        public void setOrderId(AtomicLong orderId) {
            this.orderId = orderId;
        }

        public Integer getWriteIndex() {
            return writeIndex;
        }

        public void setWriteIndex(Integer writeIndex) {
            this.writeIndex = writeIndex;
        }
    }

    static class OrderData {
        String filename;
        Integer offset;
        Integer length;

        public OrderData(String filename, Integer offset, Integer length) {
            this.filename = filename;
            this.offset = offset;
            this.length = length;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public Integer getOffset() {
            return offset;
        }

        public void setOffset(Integer offset) {
            this.offset = offset;
        }

        public Integer getLength() {
            return length;
        }

        public void setLength(Integer length) {
            this.length = length;
        }
    }
}
