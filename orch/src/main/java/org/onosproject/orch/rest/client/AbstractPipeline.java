package org.onosproject.orch.rest.client;

import javax.net.ssl.SSLException;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;


public abstract class AbstractPipeline extends ChannelInitializer<SocketChannel> {

    private boolean ssl = false;
    private EventLoopGroup group;


    public AbstractPipeline(EventLoopGroup group) {
        this.group = group;
    }


    protected abstract void addHandlerToPipeline(ChannelPipeline p, EventLoopGroup group, SocketChannel sc);

    @Override
    protected void initChannel(SocketChannel sc) throws Exception {

        ChannelPipeline p = sc.pipeline();
        if(ssl) {
            SslContext sslCtx = null;
            try {
                sslCtx = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                p.addLast(sslCtx.newHandler(sc.alloc()));
            } catch (SSLException e1) {
                e1.printStackTrace();
            }
        }

        p.addLast("chunked", new HttpObjectAggregator(1048576));
        p.addLast("codec", new HttpClientCodec());
//        p.addLast(new ResponseHandlerForExplicitElements(group, sc, orch));
        addHandlerToPipeline(p, group, sc);
    }
}
