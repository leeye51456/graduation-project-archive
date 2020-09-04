package org.onosproject.orch.rest.client;

import java.nio.charset.StandardCharsets;

import javax.xml.bind.DatatypeConverter;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class AbstractRestClient implements RestClient {

    private final Logger log = LoggerFactory.getLogger(getClass());


    private ChannelFuture cf;
    private EventLoopGroup group;

    private String host;
    private int port;
    private HttpMethod method;
    private String uri;
    private String body;


    public AbstractRestClient(String ip, int port, HttpMethod method, String uri, String body) {
        this.host = ip;
        this.port = port;
        this.method = method;
        this.uri = uri;
        this.body = body;
    }


    abstract protected void setHandlerForBootstrap(Bootstrap b, EventLoopGroup group);

    @Override
    public void connect() {
        log.info("trying to connect to {}:{}{} ...", host, port, uri);

        group = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true);
//                    .handler(/*new PipelineForExplicitElements(group, myOrchService)*/);
            setHandlerForBootstrap(b, group);
            cf = b.connect(host, port).sync();
            createRequest(method);
        } catch (Exception e) {
            log.warn("exception: {}", e.toString());
        }
    }


    void setAdditionalHeaders(DefaultFullHttpRequest request) {
        // nothing to do here
    }

    private void createRequest(HttpMethod method) {
        log.info("trying to request to {}:{}{} ...", host, port, uri);

        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri);

        String encoding = DatatypeConverter.printBase64Binary("karaf:karaf".getBytes(StandardCharsets.UTF_8));

        request.headers().add(HttpHeaderNames.AUTHORIZATION,"Basic " + encoding);
        request.headers().set(HttpHeaderNames.HOST, host+":"+port);
//        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

        if (method == HttpMethod.POST) {
            request.headers().add(HttpHeaderNames.CONTENT_TYPE,"application/json");

            ByteBuf bbuf = Unpooled.copiedBuffer(body, CharsetUtil.UTF_8);
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, bbuf.readableBytes());
            request.content().clear().writeBytes(bbuf);
        }

        setAdditionalHeaders(request);

        cf.channel().writeAndFlush(request);

        log.info("sent a request to {}:{}{}", host, port, uri);
    }


    public void close() {
        cf.channel().close();
        group.shutdownGracefully();
    }

}
