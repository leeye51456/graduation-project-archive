package org.onosproject.orch.rest.client.explicit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http.HttpMethod;
import org.onosproject.orch.Orchestration;
import org.onosproject.orch.rest.client.AbstractRestClient;


public class RestClientForExplicitElements extends AbstractRestClient {

    private Orchestration orch;


    public RestClientForExplicitElements(String ip, int port, String uri, Orchestration orch) {
        super(ip, port, HttpMethod.GET, uri, "");

        this.orch = orch;
    }

    public RestClientForExplicitElements(
            String ip, int port, HttpMethod method, String uri, String body, Orchestration orch) {
        super(ip, port, method, uri, body);

        this.orch = orch;
    }


    @Override
    protected void setHandlerForBootstrap(Bootstrap b, EventLoopGroup group) {
        b.handler(new PipelineForExplicitElements(group, orch));
    }

}
