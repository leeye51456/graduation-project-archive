package org.onosproject.orch.rest.client.implicit;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import org.json.JSONException;
import org.json.JSONObject;
import org.onosproject.orch.Orchestration;
import org.onosproject.orch.core.implicit.ImplicitTopologyConstructor;
import org.onosproject.orch.rest.client.AbstractResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ResponseHandlerForImplicitElements extends AbstractResponseHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private Orchestration orch;


    public ResponseHandlerForImplicitElements(EventLoopGroup group, SocketChannel sc, Orchestration orch) {
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
        ImplicitTopologyConstructor.create(json, getServerIp(), orch).applyJson();
    }

}
