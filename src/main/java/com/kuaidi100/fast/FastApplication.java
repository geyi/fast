package com.kuaidi100.fast;

import com.kuaidi100.fast.server.Server;
import io.netty.channel.ChannelFuture;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FastApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(FastApplication.class, args);
        Server server = new Server();
        server.setBasePath(args[0]);
        ChannelFuture bindFuture = server.startServer(9001);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.destroy()));
        bindFuture.channel().closeFuture().syncUninterruptibly();
    }

}
