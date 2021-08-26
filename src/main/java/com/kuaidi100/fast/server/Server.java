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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class Server {

    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private String basePath;
    private int bossN;
    private int workerN;
    private static final int BASE_NUM = 10000000;
    private static final int FILE_SIZE = 1 << 30;
    private static final String FILE_PREFIX = "ORDER_DATA_%d_%d";

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
                    .setFileNum(new AtomicInteger(0));
            executorAttachMap.put(executor.hashCode(), eventExecutorAttach);
        }
        return eventExecutorAttach;
    }
    private int getOrderId(EventExecutor executor) {
        return getAttach(executor).getOrderId().incrementAndGet();
    }
    private int getWriteIndex(EventExecutor executor) {
        return getAttach(executor).getWriteIndex();
    }
    private void setWriteIndex(EventExecutor executor, int writeIndex) {
        getAttach(executor).setWriteIndex(writeIndex);
    }
    private String getFileName(EventExecutor executor) {
        EventExecutorAttach attach = getAttach(executor);
        Byte num = attach.getNum();
        AtomicInteger fileNum = attach.getFileNum();
        return String.format(FILE_PREFIX, num, fileNum.get());
    }
    private void setFileNum(EventExecutor executor, int incrSize) {
        EventExecutorAttach attach = getAttach(executor);
        attach.getFileNum().addAndGet(incrSize);
        // 产生一个新文件时必须重置写索引
        attach.setWriteIndex(0);
    }

    private Map<Byte, Map<Integer, OrderIndexData>> indexMap;
    private Map<Integer, OrderIndexData> getIndexMap(EventExecutor executor) {
        Byte num = getAttach(executor).getNum();
        Map<Integer, OrderIndexData> idxMap = indexMap.get(num);
        if (idxMap == null) {
            idxMap = new HashMap<>(BASE_NUM);
            indexMap.put(num, idxMap);
        }
        return idxMap;
    }
    private void saveOrderIndexData(EventExecutor executor, int orderId, OrderIndexData idxData) {
        getIndexMap(executor).put(orderId, idxData);
    }
    private OrderIndexData getOrderData(int orderId) {
        return indexMap.get((byte) (orderId / BASE_NUM)).get(orderId);
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public int getBossN() {
        return bossN;
    }

    public void setBossN(int bossN) {
        this.bossN = bossN;
    }

    public int getWorkerN() {
        return workerN;
    }

    public void setWorkerN(int workerN) {
        this.workerN = workerN;
    }

    public NioEventLoopGroup getBoss() {
        return boss;
    }

    public NioEventLoopGroup getWorker() {
        return worker;
    }

    public Channel getChannel() {
        return channel;
    }

    public Server(String basePath, int bossN, int workerN) {
        this.basePath = basePath;
        this.bossN = bossN;
        this.workerN = workerN;
        boss  = new NioEventLoopGroup(bossN);
        worker = new NioEventLoopGroup(workerN);
        executorAttachMap = new ConcurrentHashMap<>((int) (workerN / .75) + 1);
        indexMap = new ConcurrentHashMap<>((int) (workerN / .75) + 1);
    }

    public Channel startServer(int port) throws Exception {
        final Server server = this;
        ServerBootstrap serverBootstrap = new ServerBootstrap().group(boss, worker)
                .channel(NioServerSocketChannel.class)
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
        log.info("server startup!!! bind port: {}, basePath: {}", port, basePath);
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
            int orderId = server.getOrderId(executor);
            paramMap.put(ORDER_ID, String.valueOf(orderId));
            String fileName = server.getFileName(executor);
            String orderInfo = JSONObject.toJSONString(paramMap);
            int length = orderInfo.getBytes().length;
            int writeIndex = server.getWriteIndex(executor);
            if (writeIndex + length > FILE_SIZE) {
                log.error("超出文件最大写索引");
                server.setFileNum(executor, 1);
                fileName = server.getFileName(executor);
                writeIndex = server.getWriteIndex(executor);
            }
            int nextWriteIndex = fileWrite(server.basePath + fileName, orderInfo, writeIndex);
            server.setWriteIndex(executor, nextWriteIndex);
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
            Integer orderId = Integer.valueOf(paramMap.get(ORDER_ID));
            OrderIndexData order = server.getOrderData(orderId);
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
            RandomAccessFile randomAccessTargetFile;
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
            }
            return 0;
        }

        public static byte[] fileRead(String filePath, int position, int length) {
            File file = new File(filePath);
            RandomAccessFile randomAccessTargetFile;
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
