package org.onosproject.orch.rest.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Set;


public abstract class AbstractResponseHandler extends SimpleChannelInboundHandler<HttpObject> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private EventLoopGroup group;
    private SocketChannel sc;

    private String serverIp;

    private StringBuffer contentBuffer = new StringBuffer();


    public AbstractResponseHandler(EventLoopGroup group, SocketChannel sc) {
        this.group = group;
        this.sc = sc;
        serverIp = "";
    }


    protected abstract void handleContent(String content);

    protected String getServerIp() {
        return serverIp;
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);

        serverIp = ((InetSocketAddress)(ctx.channel().remoteAddress())).getAddress().getHostAddress();

        log.info("[{}] channel connected", serverIp);
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {

        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse)msg;

            log.info("[{}] response status: {}", serverIp, response.status());
            log.info("[{}] response protocol version: {}", serverIp, response.protocolVersion());

            if (!response.headers().isEmpty()) {
                Set<String> str = response.headers().names();
                for (String name : str) {
                    log.info("[{}] header - {}: {}", serverIp, name, response.headers().getAll(name));
                }
            }
        }

        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent) msg;
            contentBuffer.append(content.content().toString(CharsetUtil.UTF_8));

            if (content instanceof LastHttpContent) {
                String finalContent = this.contentBuffer.toString();
                log.info("[{}] content: {}", serverIp, finalContent);

                if (!finalContent.isEmpty()) {
                    handleContent(finalContent);
                }

                close(ctx);
            }
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        close(ctx);
    }


    public void close(ChannelHandlerContext ctx) {
        ctx.close();
        sc.close();
        group.shutdownGracefully();
    }

}
