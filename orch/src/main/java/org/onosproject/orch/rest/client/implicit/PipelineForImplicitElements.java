package org.onosproject.orch.rest.client.implicit;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import org.onosproject.orch.Orchestration;
import org.onosproject.orch.rest.client.AbstractPipeline;


public class PipelineForImplicitElements extends AbstractPipeline {

    private Orchestration orch;


    public PipelineForImplicitElements(EventLoopGroup group, Orchestration orch) {
        super(group);

        this.orch = orch;
    }


    @Override
    protected void addHandlerToPipeline(ChannelPipeline p, EventLoopGroup group, SocketChannel sc) {
        p.addLast(new ResponseHandlerForImplicitElements(group, sc, orch));
    }

}
