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
import io.netty.channel.ChannelOption;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {

    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private String basePath;
    private int bossN;
    private int workerN;
    private static final int BASE_NUM = 10000000;
    // 512MB
    private static final int FILE_SIZE = 1 << 29;
    private static final String FILE_PREFIX = "ORDER_DATA_%d_%d";
    private AtomicInteger orderAutoId = new AtomicInteger(0);

    private NioEventLoopGroup boss;
    private NioEventLoopGroup worker;
    private Channel channel;

    private AtomicInteger executorNum = new AtomicInteger(0);
    private Map<Integer, EventExecutorAttach> executorAttachMap;

    private EventExecutorAttach getAttach(EventExecutor executor) {
        EventExecutorAttach eventExecutorAttach = executorAttachMap.get(executor.hashCode());
        if (eventExecutorAttach == null) {
            int num = executorNum.getAndIncrement();
            eventExecutorAttach = new EventExecutorAttach()
                    .setNum((byte) num)
                    .setExecutor(executor)
                    .setOrderId(new AtomicInteger(num * BASE_NUM))
                    .setWriteIndex(0)
                    .setFileNum(new AtomicInteger(0))
                    .setRealWriteIndex(0)
                    .setRealFileNum(new AtomicInteger(0));
            executorAttachMap.put(executor.hashCode(), eventExecutorAttach);
        }
        return eventExecutorAttach;
    }

    private int getOrderId(EventExecutor executor) {
        return getAttach(executor).getOrderId().incrementAndGet();
    }

    private int getOrderId(EventExecutorAttach attach) {
        return attach.getOrderId().incrementAndGet();
    }

    private int getWriteIndex(EventExecutor executor) {
        return getAttach(executor).getWriteIndex();
    }

    private int getWriteIndex(EventExecutorAttach attach) {
        return attach.getWriteIndex();
    }

    private void setWriteIndex(EventExecutor executor, int writeIndex) {
        getAttach(executor).setWriteIndex(writeIndex);
    }

    private void setRealWriteIndex(EventExecutorAttach attach, int writeIndex) {
        attach.setRealWriteIndex(writeIndex);
    }

    private String getFileName(EventExecutor executor) {
        EventExecutorAttach attach = getAttach(executor);
        Byte num = attach.getNum();
        AtomicInteger fileNum = attach.getFileNum();
        return String.format(FILE_PREFIX, num, fileNum.get());
    }

    private String getFileName(EventExecutorAttach attach) {
        Byte num = attach.getNum();
        AtomicInteger fileNum = attach.getFileNum();
        return String.format(FILE_PREFIX, num, fileNum.get());
    }

    private String getRealFileName(EventExecutorAttach attach) {
        Byte num = attach.getNum();
        AtomicInteger fileNum = attach.getRealFileNum();
        return String.format(FILE_PREFIX, num, fileNum.get());
    }

    private void setFileNum(EventExecutor executor, int incrSize) {
        EventExecutorAttach attach = getAttach(executor);
        attach.getFileNum().addAndGet(incrSize);
        // 产生一个新文件时必须重置写索引
        attach.setWriteIndex(0);
    }

    private void setRealFileNum(EventExecutor executor, int incrSize) {
        EventExecutorAttach attach = getAttach(executor);
        attach.getRealFileNum().addAndGet(incrSize);
        // 产生一个新文件时必须重置写索引
        attach.setRealWriteIndex(0);
    }

    private Map<Byte, Map<Integer, OrderIndexData>> indexMap;

    private Map<Integer, OrderIndexData> getIndexMap(EventExecutor executor) {
        Byte num = getAttach(executor).getNum();
        Map<Integer, OrderIndexData> idxMap = indexMap.get(num);
        if (idxMap == null) {
            idxMap = new HashMap<>((int) (BASE_NUM / .75) + 1);
            indexMap.put(num, idxMap);
        }
        return idxMap;
    }

    private void saveOrderIndexData(EventExecutor executor, int orderId, OrderIndexData idxData) {
        getIndexMap(executor).put(orderId, idxData);
    }

    private OrderIndexData getOrderIndex(int orderId) {
        return indexMap.get((byte) (orderId / BASE_NUM)).get(orderId);
    }

    private Map<Integer, TimeoutBlockingQueue<String>> orderMap;

    private TimeoutBlockingQueue<String> getOrderMap(EventExecutor executor) {
        TimeoutBlockingQueue<String> orders = orderMap.get(executor.hashCode());
        if (orders == null) {
            String[] items = new String[2000];
            orders = new TimeoutBlockingQueue<>(items, 1000, 1000);
            orderMap.put(executor.hashCode(), orders);
        }
        return orders;
    }

    private void saveOrderData(EventExecutor executor, String order) throws InterruptedException {
        TimeoutBlockingQueue<String> orders = getOrderMap(executor);
        orders.put(order);
    }

    public Server(String basePath, int bossN, int workerN) {
        this.basePath = basePath;
        this.bossN = bossN;
        this.workerN = workerN;
        boss = new NioEventLoopGroup(bossN);
        worker = new NioEventLoopGroup(workerN);
        executorAttachMap = new ConcurrentHashMap<>((int) (workerN / .75) + 1);
        indexMap = new ConcurrentHashMap<>((int) (workerN / .75) + 1);
        orderMap = new ConcurrentHashMap<>((int) (workerN / .75) + 1);
    }

    public Channel startServer(int port) throws Exception {
        final Server server = this;
        ServerBootstrap serverBootstrap = new ServerBootstrap().group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 4096)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(1 * 1024));
                        pipeline.addLast(new RequestHandler(server));
                    }
                });
        ChannelFuture bindFuture = serverBootstrap.bind(new InetSocketAddress(port));
        channel = bindFuture.sync().channel();
        log.info("server startup!!! bind port: {}, basePath: {}, bossN: {}, workN: {}",
                port, basePath, bossN, workerN);
        return channel;
    }

    public void destroy() {
        if (channel != null) {
            channel.close().syncUninterruptibly();
        }
        boss.shutdownGracefully();
        worker.shutdownGracefully();
        log.info("server destroy!!!");
    }

    static class RequestHandler extends ChannelInboundHandlerAdapter {
        private static final String SUCC_RESP = "{\"STATUS\":\"SUCCESS\",\"ORDER_ID\":%d}";
        private static final String ADD_NEW = "ADDNEW";
        private static final String FIND = "FINDBYID";
        private static final String ACTION = "ACTION";
        private static final String ORDER_ID = "ORDER_ID";

        private Server server;

        public RequestHandler(Server server) {
            this.server = server;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            FullHttpRequest request = (FullHttpRequest) msg;
            /*if (!request.decoderResult().isSuccess()) {
                log.error("request decode is not success");
                this.sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }*/

            String uri = request.uri();
            Map<String, String> paramMap = this.parseQueryString(uri);
            String action = paramMap.get(ACTION);
            if (ADD_NEW.equals(action)) {
                this.addNew(request, ctx, paramMap);
            } else if (FIND.equals(action)) {
                this.findById(request, ctx, paramMap);
            } else {
                this.sendError(ctx, HttpResponseStatus.NOT_FOUND);
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
            EventExecutorAttach attach = server.getAttach(executor);

            int orderId = server.getOrderId(attach);
            int writeIndex = attach.getWriteIndex();
            String fileName = server.getFileName(attach);

            paramMap.put(ORDER_ID, String.valueOf(orderId));
            String orderInfo = JSONObject.toJSONString(paramMap);
            int length = orderInfo.getBytes().length;

            if (writeIndex + length > FILE_SIZE) {
                log.error("超出文件最大写索引");
                server.setFileNum(executor, 1);
                writeIndex = attach.getWriteIndex();
            }
            /*int nextWriteIndex = fileWrite(server.basePath + fileName, orderInfo, writeIndex);*/
            server.setWriteIndex(executor, writeIndex + length);
            server.saveOrderIndexData(executor, orderId, new OrderIndexData(fileName, writeIndex, length));
            log.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", paramMap.get("ORDER_ID"),
                    paramMap.get("USER_ID"),
                    paramMap.get("COM"),
                    paramMap.get("NUM"),
                    paramMap.get("SENDER_NAME"),
                    paramMap.get("SENDER_MOBILE"),
                    paramMap.get("SENDER_ADDR"),
                    paramMap.get("RECEIVER_NAME"),
                    paramMap.get("RECEIVER_MOBILE"),
                    paramMap.get("RECEIVER_ADDR"));

            executor.execute(() -> batchWrite(executor, orderInfo));

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

        private void batchWrite(EventExecutor executor, String orderInfo) {
            try {
                // 订单信息写入队列
                server.saveOrderData(executor, orderInfo);

                TimeoutBlockingQueue<String> queue = server.getOrderMap(executor);
                List<String> orders = queue.poll();
                if (orders != null) {
                    EventExecutorAttach attach = server.getAttach(executor);
                    int writeIndex = attach.getRealWriteIndex();
                    String fileName = server.getRealFileName(attach);

                    StringBuilder sb = new StringBuilder();
                    for (String order : orders) {
                        // 如果拼上下一个订单信息会超出文件大小，则将前面的内容先写入文件
                        if (writeIndex + sb.length() + order.length() > FILE_SIZE) {
                            if (sb.length() > 0) {
                                int nextWriteIndex = fileWrite(server.basePath + fileName, sb.toString(), writeIndex);
                                server.setRealWriteIndex(attach, nextWriteIndex);
                            }

                            log.error("超出文件最大写索引，重新生成一个文件");
                            server.setRealFileNum(executor, 1);
                            writeIndex = attach.getRealWriteIndex();
                            fileName = server.getRealFileName(attach);

                            // 让缓冲区从0开始
                            sb.setLength(0);
                        }
                        sb.append(order);
                    }
                    int nextWriteIndex = fileWrite(server.basePath + fileName, sb.toString(), writeIndex);
                    server.setRealWriteIndex(attach, nextWriteIndex);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void findById(FullHttpRequest request, ChannelHandlerContext ctx, Map<String, String> paramMap) {
            Integer orderId = Integer.valueOf(paramMap.get(ORDER_ID));
            OrderIndexData order = server.getOrderIndex(orderId);
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            if (HttpUtil.isKeepAlive(request)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }

            byte[] bytes = fileRead(server.basePath + order.getFileName(), order.getOffset(), order.getLength());
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

        public static int fileWrite(String filePath, String content, int index) {
            File file = new File(filePath);
            RandomAccessFile randomAccessTargetFile = null;
            MappedByteBuffer map;
            try {
                randomAccessTargetFile = new RandomAccessFile(file, "rw");
                FileChannel targetFileChannel = randomAccessTargetFile.getChannel();
                map = targetFileChannel.map(FileChannel.MapMode.READ_WRITE, 0, FILE_SIZE);
                map.position(index);
                map.put(content.getBytes());
                return map.position();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (randomAccessTargetFile != null) {
                        randomAccessTargetFile.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return 0;
        }

        public static byte[] fileRead(String filePath, int position, int length) {
            File file = new File(filePath);
            RandomAccessFile randomAccessTargetFile = null;
            MappedByteBuffer map;
            try {
                randomAccessTargetFile = new RandomAccessFile(file, "r");
                FileChannel targetFileChannel = randomAccessTargetFile.getChannel();
                map = targetFileChannel.map(FileChannel.MapMode.READ_ONLY, position, position + length);
                byte[] bytes = new byte[length];
                map.get(bytes);
                return bytes;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (randomAccessTargetFile != null) {
                        randomAccessTargetFile.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }

    static class EventExecutorAttach {
        Byte num;
        EventExecutor executor;
        AtomicInteger orderId;
        Integer writeIndex;
        AtomicInteger fileNum;
        Integer realWriteIndex;
        AtomicInteger realFileNum;

        public Byte getNum() {
            return num;
        }

        public EventExecutorAttach setNum(Byte num) {
            this.num = num;
            return this;
        }

        public EventExecutor getExecutor() {
            return executor;
        }

        public EventExecutorAttach setExecutor(EventExecutor executor) {
            this.executor = executor;
            return this;
        }

        public AtomicInteger getOrderId() {
            return orderId;
        }

        public EventExecutorAttach setOrderId(AtomicInteger orderId) {
            this.orderId = orderId;
            return this;
        }

        public Integer getWriteIndex() {
            return writeIndex;
        }

        public EventExecutorAttach setWriteIndex(Integer writeIndex) {
            this.writeIndex = writeIndex;
            return this;
        }

        public AtomicInteger getFileNum() {
            return fileNum;
        }

        public EventExecutorAttach setFileNum(AtomicInteger fileNum) {
            this.fileNum = fileNum;
            return this;
        }

        public Integer getRealWriteIndex() {
            return realWriteIndex;
        }

        public EventExecutorAttach setRealWriteIndex(Integer realWriteIndex) {
            this.realWriteIndex = realWriteIndex;
            return this;
        }

        public AtomicInteger getRealFileNum() {
            return realFileNum;
        }

        public EventExecutorAttach setRealFileNum(AtomicInteger realFileNum) {
            this.realFileNum = realFileNum;
            return this;
        }
    }

    static class OrderIndexData {
        String fileName;
        Integer offset;
        Integer length;

        public OrderIndexData(String fileName, Integer offset, Integer length) {
            this.fileName = fileName;
            this.offset = offset;
            this.length = length;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
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
