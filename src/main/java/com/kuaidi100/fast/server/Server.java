package com.kuaidi100.fast.server;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.strands.SuspendableRunnable;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
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
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {

    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private String basePath;
    private int bossN;
    private int workerN;
    private static final int BASE_NUM = 10000000;
    // 512MB
    private static final int FILE_SIZE = 1 << 29;
    private static final String FILE_PREFIX = "ORDER_DATA_%s_%d";

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
                    .setRealFileNum(new AtomicInteger(0))
                    .setIdxWriteIndex(0)
                    .setIdxFileNum(new AtomicInteger(0));
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

    private void setIdxWriteIndex(EventExecutorAttach attach, int writeIndex) {
        attach.setIdxWriteIndex(writeIndex);
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
        return String.format(FILE_PREFIX, getSuffix(num), fileNum.get());
    }

    private String getRealFileName(EventExecutorAttach attach) {
        Byte num = attach.getNum();
        AtomicInteger fileNum = attach.getRealFileNum();
        return String.format(FILE_PREFIX, getSuffix(num), fileNum.get());
    }

    private String getIdxFileName(EventExecutorAttach attach) {
        Byte num = attach.getNum();
        AtomicInteger fileNum = attach.getIdxFileNum();
        String suffix = getSuffix(fileNum.get());
        String exeNum = getSuffix(num);
        return "mobile_" + exeNum + "_" + suffix;
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

    private void setIdxFileNum(EventExecutor executor, int incrSize) {
        EventExecutorAttach attach = getAttach(executor);
        attach.getIdxFileNum().addAndGet(incrSize);
        // 产生一个新文件时必须重置写索引
        attach.setIdxWriteIndex(0);
    }

    private Map<String, Queue<Index>> mobileIdxMap;

    private Queue<Index> getIdxQueue(String mobile) {
        Queue<Index> idxQueue = mobileIdxMap.get(mobile);
        if (idxQueue == null) {
            idxQueue = new ConcurrentLinkedQueue<>();
            mobileIdxMap.put(mobile, idxQueue);
        }
        return idxQueue;
    }

    private void saveMobileIdx(String mobile, Index idx) {
        getIdxQueue(mobile).offer(idx);
    }

    private Map<Integer, TimeoutBlockingQueue<String>> orderMap;

    private TimeoutBlockingQueue<String> getOrderQueue(EventExecutor executor) {
        TimeoutBlockingQueue<String> orders = orderMap.get(executor.hashCode());
        if (orders == null) {
            String[] items = new String[2000];
            orders = new TimeoutBlockingQueue<>(items, 1, 1000);
            orderMap.put(executor.hashCode(), orders);
        }
        return orders;
    }

    private void putOrderData(EventExecutor executor, String order) throws InterruptedException {
        TimeoutBlockingQueue<String> orders = getOrderQueue(executor);
        orders.put(order);
    }

    private Map<Integer, TimeoutBlockingQueue<Index>> saveIndexMap;

    private TimeoutBlockingQueue<Index> getSaveIndexQueue(EventExecutor executor) {
        TimeoutBlockingQueue<Index> indexes = saveIndexMap.get(executor.hashCode());
        if (indexes == null) {

            Index[] items = new Index[2000];
            indexes = new TimeoutBlockingQueue<>(items, 1, 1000);
            saveIndexMap.put(executor.hashCode(), indexes);
        }
        return indexes;
    }

    private void putIndexData(EventExecutor executor, Index index) throws InterruptedException {
        TimeoutBlockingQueue<Index> indexes = getSaveIndexQueue(executor);
        indexes.put(index);
    }

    public Server(String basePath, int bossN, int workerN) {
        this.basePath = basePath;
        this.bossN = bossN;
        this.workerN = workerN;
        boss = new NioEventLoopGroup(bossN);
        worker = new NioEventLoopGroup(workerN);
        executorAttachMap = new ConcurrentHashMap<>((int) (workerN / .75) + 1);
        orderMap = new ConcurrentHashMap<>((int) (workerN / .75) + 1);
        saveIndexMap = new ConcurrentHashMap<>((int) (workerN / .75) + 1);
        mobileIdxMap = new ConcurrentHashMap<>(65535);

        readIdxFile();
    }

    private void readIdxFile() {
        String path = this.basePath + "idx" + File.separator;
        File idxPath = new File(path);
        File[] files = idxPath.listFiles();
        int position = 0;
        int length = 95;
        for (int i = 0, limit = files.length; i < limit; i++) {
            File file = files[i];
            if (!file.getName().startsWith("mobile")) {
                continue;
            }
            while (true) {
                byte[] bytes = fileRead(file, position, length);
                if (bytes == null || bytes.length <= 0) {
                    break;
                }
                if (bytes[0] != 123) {
                    break;
                }
                Index index = JSONObject.parseObject(new String(bytes), Index.class);
                saveMobileIdx(index.value, index);
                position += length;
            }
            System.out.println(position);
            position = 0;
        }
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
        private static final String FIND = "FINDBYMOBILE";
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
                this.find(request, ctx, paramMap);
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
            long start = System.currentTimeMillis();

            EventExecutor executor = ctx.executor();
            EventExecutorAttach attach = server.getAttach(executor);

            String sMobile = paramMap.get("SENDER_MOBILE");
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
            attach.setWriteIndex(writeIndex + length);
            Index index = new Index(fileName, getInt(writeIndex), getInt(length), sMobile);
            server.saveMobileIdx(sMobile, index);
            log.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}", paramMap.get("ORDER_ID"),
                    paramMap.get("USER_ID"),
                    paramMap.get("COM"),
                    paramMap.get("NUM"),
                    paramMap.get("SENDER_NAME"),
                    sMobile,
                    paramMap.get("SENDER_ADDR"),
                    paramMap.get("RECEIVER_NAME"),
                    paramMap.get("RECEIVER_MOBILE"),
                    paramMap.get("RECEIVER_ADDR"));

            executor.execute(() -> {
                batchWrite(executor, orderInfo);
                batchWriteIndex(executor, index);
            });

            new Fiber<>((SuspendableRunnable) () -> {
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
                long take = System.currentTimeMillis() - start;
                ThreadPoolUtils.getInstance().updateTotalTime(take);
                try {
                    long sleep = 2000 - take;
                    log.info("sleep: {}", sleep);
                    if (sleep > 0) {
                        ThreadPoolUtils.getInstance().updateWaitingTime(sleep);
                        TimeUnit.MILLISECONDS.sleep(sleep);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                log.info("take: {}", System.currentTimeMillis() - start);
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            }).start();
        }

        private void batchWriteIndex(EventExecutor executor, Index idxData) {
            try {
                server.putIndexData(executor, idxData);

                TimeoutBlockingQueue<Index> queue = server.getSaveIndexQueue(executor);
                List<Index> indexList = queue.poll();
                if (indexList != null) {
                    EventExecutorAttach attach = server.getAttach(executor);
                    int writeIndex = attach.getIdxWriteIndex();
                    String fileName = server.getIdxFileName(attach);

                    StringBuilder sb = new StringBuilder();
                    for (Index idx : indexList) {
                        String idxJson = JSONObject.toJSONString(idx);
                        log.info("idx len: {}", idxJson.length());
                        // 如果拼上下一个订单信息会超出文件大小，则将前面的内容先写入文件
                        if (writeIndex + sb.length() + idxJson.length() > FILE_SIZE) {
                            if (sb.length() > 0) {
                                int nextWriteIndex = fileWrite(server.basePath + "idx" + File.separator + fileName, sb.toString(), writeIndex);
                                server.setIdxWriteIndex(attach, nextWriteIndex);
                            }

                            log.error("超出索引文件最大写索引，重新生成一个文件");
                            server.setIdxFileNum(executor, 1);
                            writeIndex = attach.getIdxWriteIndex();
                            fileName = server.getIdxFileName(attach);

                            // 让缓冲区从0开始
                            sb.setLength(0);
                        }
                        sb.append(idxJson);
                    }
                    int nextWriteIndex = fileWrite(server.basePath + "idx" + File.separator + fileName, sb.toString(), writeIndex);
                    server.setIdxWriteIndex(attach, nextWriteIndex);
                    indexList = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void batchWrite(EventExecutor executor, String orderInfo) {
            try {
                // 订单信息写入队列
                server.putOrderData(executor, orderInfo);

                TimeoutBlockingQueue<String> queue = server.getOrderQueue(executor);
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
                    orders = null;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void find(FullHttpRequest request, ChannelHandlerContext ctx, Map<String, String> paramMap) {
            String sMobile = paramMap.get("SENDER_MOBILE");
            Queue<Index> idxQueue = server.getIdxQueue(sMobile);
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            if (HttpUtil.isKeepAlive(request)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            List<JSONObject> list;
            int size;
            if (idxQueue == null || (size = idxQueue.size()) == 0) {
                list = new ArrayList<>(0);
            } else {
                int limit = size > 10 ? 10 : size;
                int i = 0;
                list = new ArrayList<>(limit);
                for (Index index : idxQueue) {
                    if (i >= limit) {
                        break;
                    }
                    byte[] bytes = fileRead(server.basePath + index.getFileName(), Integer.parseInt(index.getOffset()), Integer.parseInt(index.getLength()));
                    list.add(JSONObject.parseObject(new String(bytes)));
                    i++;
                }
            }
            byte[] bytes = JSONObject.toJSONString(list).getBytes();
            ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.directBuffer(bytes.length);
            byteBuf.writeBytes(bytes);
            response.content().writeBytes(byteBuf);
            list = null;
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
            int i = uri.indexOf('?');
            String[] params = StringUtils.split(uri.substring(i + 1), '&');
            for (String param : params) {
                String[] kv = StringUtils.split(param, '=');
                ret.put(kv[0], kv[1]);
            }
            return ret;
        }

        static final Pattern pattern = Pattern.compile("prov([0-9]+)city([0-9]+)county_([0-9]+)");

        public static byte[] toByte(Map<String, String> map) {
            OrderData orderData = new OrderData();

            String com = map.get("COM");
            String num = map.get("NUM");
            String sName = map.get("SENDER_NAME");
            String sMobile = map.get("SENDER_MOBILE");
            String sAddr = map.get("SENDER_ADDR");
            String rName = map.get("RECEIVER_NAME");
            String rMobile = map.get("RECEIVER_MOBILE");
            String rAddr = map.get("RECEIVER_ADDR");
            String oid = map.get("ORDER_ID");
            String uid = map.get("USER_ID");

            ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(58);
            buffer.writeByte(Short.parseShort(com.substring(3)));
            buffer.writeByte(Integer.parseInt(num.substring(2)));
            buffer.writeByte(Integer.parseInt(sName.substring(5)));
            buffer.writeBytes(sMobile.getBytes());
            Matcher matcher = pattern.matcher(sAddr);
            matcher.find();
            buffer.writeByte(Byte.parseByte(matcher.group(1)));
            buffer.writeByte(Short.parseShort(matcher.group(2)));
            buffer.writeByte(Integer.parseInt(matcher.group(3)));
            buffer.writeByte(Integer.parseInt(rName.substring(5)));
            buffer.writeBytes(rMobile.getBytes());
            matcher = pattern.matcher(rAddr);
            matcher.find();
            buffer.writeByte(Byte.parseByte(matcher.group(1)));
            buffer.writeByte(Short.parseShort(matcher.group(2)));
            buffer.writeByte(Integer.parseInt(matcher.group(3)));
            buffer.writeByte(Integer.parseInt(oid));
            buffer.writeByte(Integer.parseInt(uid));
            return buffer.nioBuffer().array();
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

    public static byte[] fileRead(File file, int position, int length) {
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

    static class EventExecutorAttach {
        Byte num;
        EventExecutor executor;
        AtomicInteger orderId;
        Integer writeIndex;
        AtomicInteger fileNum;
        Integer realWriteIndex;
        AtomicInteger realFileNum;
        Integer idxWriteIndex;
        AtomicInteger idxFileNum;

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

        public Integer getIdxWriteIndex() {
            return idxWriteIndex;
        }

        public EventExecutorAttach setIdxWriteIndex(Integer idxWriteIndex) {
            this.idxWriteIndex = idxWriteIndex;
            return this;
        }

        public AtomicInteger getIdxFileNum() {
            return idxFileNum;
        }

        public EventExecutorAttach setIdxFileNum(AtomicInteger idxFileNum) {
            this.idxFileNum = idxFileNum;
            return this;
        }
    }

    static class Index {
        String fileName;
        String offset;
        String length;
        String value;

        public Index() {
        }

        public Index(String fileName, String offset, String length, String value) {
            this.fileName = fileName;
            this.offset = offset;
            this.length = length;
            this.value = value;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getOffset() {
            return offset;
        }

        public void setOffset(String offset) {
            this.offset = offset;
        }

        public String getLength() {
            return length;
        }

        public void setLength(String length) {
            this.length = length;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    static class OrderData implements Serializable {
        /*{
            "COM": "COM863",
            "SENDER_NAME": "name_617671",
            "SENDER_MOBILE": "13926085842",
            "ACTION": "ADDNEW",
            "SENDER_ADDR": "prov4city104county_38356",
            "ORDER_ID": "140999254",
            "NUM": "NO238",
            "RECEIVER_NAME": "name_138080",
            "USER_ID": "759432",
            "RECEIVER_MOBILE": "13921414925",
            "RECEIVER_ADDR": "prov14city78county_8268",
            "HASH": "123456"
        }*/
        @JSONField(name = "COM")
        short com;
        @JSONField(name = "NUM")
        int num;
        @JSONField(name = "SENDER_NAME")
        int sName;
        @JSONField(name = "SENDER_MOBILE")
        String sMobile;
        byte sProv;
        short sCity;
        int sCountry;
        @JSONField(name = "RECEIVER_NAME")
        int rName;
        @JSONField(name = "RECEIVER_MOBILE")
        String rMobile;
        byte rProv;
        short rCity;
        int rCountry;
        @JSONField(name = "ORDER_ID")
        int oid;
        @JSONField(name = "USER_ID")
        int uid;

        public static void main(String[] args) {
            String s = "{\n" +
                    "            \"COM\": \"COM863\",\n" +
                    "            \"SENDER_NAME\": \"name_617671\",\n" +
                    "            \"SENDER_MOBILE\": \"13926085842\",\n" +
                    "            \"ACTION\": \"ADDNEW\",\n" +
                    "            \"SENDER_ADDR\": \"prov4city104county_38356\",\n" +
                    "            \"ORDER_ID\": \"140999254\",\n" +
                    "            \"NUM\": \"NO238\",\n" +
                    "            \"RECEIVER_NAME\": \"name_138080\",\n" +
                    "            \"USER_ID\": \"759432\",\n" +
                    "            \"RECEIVER_MOBILE\": \"13921414925\",\n" +
                    "            \"RECEIVER_ADDR\": \"prov14city78county_8268\",\n" +
                    "            \"HASH\": \"123456\"\n" +
                    "        }";
            Pattern pattern = Pattern.compile("prov([0-9]+)city([0-9]+)county_([0-9]+)");

            OrderData orderData = new OrderData();
            Map<String, String> map = JSONObject.parseObject(s, Map.class);

            String com = map.get("COM");
            orderData.setCom(Short.parseShort(com.substring(3)));

            String num = map.get("NUM");
            orderData.setNum(Integer.parseInt(num.substring(2)));

            String sName = map.get("SENDER_NAME");
            orderData.setsName(Integer.parseInt(sName.substring(5)));

            String sMobile = map.get("SENDER_MOBILE");
            orderData.setsMobile(sMobile);

            String sAddr = map.get("SENDER_ADDR");
            Matcher matcher = pattern.matcher(sAddr);
            matcher.find();
            orderData.setsProv(Byte.parseByte(matcher.group(1)));
            orderData.setsCity(Short.parseShort(matcher.group(2)));
            orderData.setsCountry(Integer.parseInt(matcher.group(3)));

            String rName = map.get("RECEIVER_NAME");
            orderData.setrName(Integer.parseInt(rName.substring(5)));

            String rMobile = map.get("RECEIVER_MOBILE");
            orderData.setrMobile(rMobile);

            String rAddr = map.get("RECEIVER_ADDR");
            matcher = pattern.matcher(rAddr);
            matcher.find();
            orderData.setrProv(Byte.parseByte(matcher.group(1)));
            orderData.setrCity(Short.parseShort(matcher.group(2)));
            orderData.setrCountry(Integer.parseInt(matcher.group(3)));

            String oid = map.get("ORDER_ID");
            orderData.setOid(Integer.parseInt(oid));

            String uid = map.get("USER_ID");
            orderData.setUid(Integer.parseInt(uid));

            ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(58);
            buffer.writeByte(orderData.getCom());
            buffer.writeByte(orderData.getNum());
            buffer.writeByte(orderData.getsName());
            buffer.writeBytes(orderData.getsMobile().getBytes());
            buffer.writeByte(orderData.getsProv());
            buffer.writeByte(orderData.getsCity());
            buffer.writeByte(orderData.getsCountry());
            buffer.writeByte(orderData.getrName());
            buffer.writeBytes(orderData.getrMobile().getBytes());
            buffer.writeByte(orderData.getrProv());
            buffer.writeByte(orderData.getrCity());
            buffer.writeByte(orderData.getrCountry());
            buffer.writeByte(orderData.getOid());
            buffer.writeByte(orderData.getUid());
            System.out.println(buffer);
        }

        public short getCom() {
            return com;
        }

        public void setCom(short com) {
            this.com = com;
        }

        public int getNum() {
            return num;
        }

        public void setNum(int num) {
            this.num = num;
        }

        public int getsName() {
            return sName;
        }

        public void setsName(int sName) {
            this.sName = sName;
        }

        public String getsMobile() {
            return sMobile;
        }

        public void setsMobile(String sMobile) {
            this.sMobile = sMobile;
        }

        public byte getsProv() {
            return sProv;
        }

        public void setsProv(byte sProv) {
            this.sProv = sProv;
        }

        public short getsCity() {
            return sCity;
        }

        public void setsCity(short sCity) {
            this.sCity = sCity;
        }

        public int getsCountry() {
            return sCountry;
        }

        public void setsCountry(int sCountry) {
            this.sCountry = sCountry;
        }

        public int getrName() {
            return rName;
        }

        public void setrName(int rName) {
            this.rName = rName;
        }

        public String getrMobile() {
            return rMobile;
        }

        public void setrMobile(String rMobile) {
            this.rMobile = rMobile;
        }

        public byte getrProv() {
            return rProv;
        }

        public void setrProv(byte rProv) {
            this.rProv = rProv;
        }

        public short getrCity() {
            return rCity;
        }

        public void setrCity(short rCity) {
            this.rCity = rCity;
        }

        public int getrCountry() {
            return rCountry;
        }

        public void setrCountry(int rCountry) {
            this.rCountry = rCountry;
        }

        public int getOid() {
            return oid;
        }

        public void setOid(int oid) {
            this.oid = oid;
        }

        public int getUid() {
            return uid;
        }

        public void setUid(int uid) {
            this.uid = uid;
        }
    }

    static final String S = "000";
    public static String getSuffix(int num) {
        String numStr = String.valueOf(num);
        int len = numStr.length();
        if (len > S.length()) {
            throw new RuntimeException("num is error");
        }
        return S.substring(0, S.length() - numStr.length()) + num;
    }

    static final String INT = "000000000";
    public static String getInt(int num) {
        String numStr = String.valueOf(num);
        int len = numStr.length();
        if (len > INT.length()) {
            throw new RuntimeException("num is error");
        }
        return INT.substring(0, INT.length() - numStr.length()) + num;
    }
}
