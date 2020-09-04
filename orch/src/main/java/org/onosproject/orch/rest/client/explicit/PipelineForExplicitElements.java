package org.onosproject.orch.rest.client.explicit;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import org.onosproject.orch.Orchestration;
import org.onosproject.orch.rest.client.AbstractPipeline;


public class PipelineForExplicitElements extends AbstractPipeline {

    private Orchestration orch;


    public PipelineForExplicitElements(EventLoopGroup group, Orchestration orch) {
        super(group);

        this.orch = orch;
    }


    @Override
    protected void addHandlerToPipeline(ChannelPipeline p, EventLoopGroup group, SocketChannel sc) {
        p.addLast(new ResponseHandlerForExplicitElements(group, sc, orch));
    }

}
