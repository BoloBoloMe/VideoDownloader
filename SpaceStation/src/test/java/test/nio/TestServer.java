package test.nio;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class TestServer {
    public static void main(String[] args) {
        EventLoopGroup bothGroup = new NioEventLoopGroup(1);
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bothGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new TestServerInitializer());
       ChannelFuture future = bootstrap.bind(9000);
        try {
            future.sync();
            System.out.println("xxxxxx");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
