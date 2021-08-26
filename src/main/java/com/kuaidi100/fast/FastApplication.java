package com.kuaidi100.fast;

import com.kuaidi100.fast.server.Server;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FastApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(FastApplication.class, args);
        String basePath = "E:\\";
        int bossN = 1;
        int workN = 1;
        int length = args.length;
        if (length == 3) {
            basePath = args[0];
            bossN = Integer.parseInt(args[1]);
            workN = Integer.parseInt(args[2]);
        }
        Server server = new Server(basePath, bossN, workN);
        Channel channel = server.startServer(9001);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.destroy()));
        channel.closeFuture().syncUninterruptibly();
    }

}
