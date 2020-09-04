package org.onosproject.orch.rest.client.explicit;

import java.net.InetSocketAddress;
import java.util.Set;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.onosproject.orch.Orchestration;
import org.onosproject.orch.core.explicit.ExplicitTopologyConstructor;
import org.onosproject.orch.rest.client.AbstractResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ResponseHandlerForExplicitElements extends AbstractResponseHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private Orchestration orch;


    public ResponseHandlerForExplicitElements(EventLoopGroup group, SocketChannel sc, Orchestration orch) {
        super(group, sc);
        this.orch = orch;
    }


    @Override
    protected void handleContent(String content) {
        JSONObject json;
        try {
            json = new JSONObject(content);
        } catch (JSONException e) {
            log.warn("[{}] exception: {}", getServerIp(), e.toString());
            return;
        }
        ExplicitTopologyConstructor.create(json, getServerIp(), orch).applyJson();
    }

}
