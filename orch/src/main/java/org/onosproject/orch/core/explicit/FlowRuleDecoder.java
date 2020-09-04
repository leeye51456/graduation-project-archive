package org.onosproject.orch.core.explicit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onlab.rest.BaseResource;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.CodecService;
import org.onosproject.codec.JsonCodec;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.orch.Orchestration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;


public class FlowRuleDecoder {

    private final Logger log = LoggerFactory.getLogger(getClass());

    // codecs for onos core consume jackson json
    private final ObjectMapper mapper = new ObjectMapper();


    private final CodecService codecService;
    private final InternalCodecContext codecContext;


    public FlowRuleDecoder(Orchestration orch) {
        codecService = orch.getCodecService();
        codecContext = new InternalCodecContext();
    }


    public FlowRule decodeFlowRule(String jsonString) throws IOException, IllegalArgumentException {
        ObjectNode jsonObject = (ObjectNode)mapper.readTree(jsonString);
        return decodeFlowRule(jsonObject);
    }
    public FlowRule decodeFlowRule(ObjectNode jsonObject) throws IllegalArgumentException {
        return codecContext.codec(FlowRule.class).decode(jsonObject, codecContext);
    }

    public FlowRule[] decodeFlowRules(String jsonString) throws IOException {
        log.info("trying to decode json of flow rules: {}", jsonString);

        ObjectNode root = (ObjectNode)mapper.readTree(jsonString);
        ArrayNode flowsNode = (ArrayNode)root.get("flows");

        ArrayList<FlowRule> flowRules = new ArrayList<>();
        for (JsonNode node : flowsNode) {
            FlowRule flowRule;
            try {
                flowRule = decodeFlowRule((ObjectNode) node);
            } catch (IllegalArgumentException e) {
                log.warn("exception: {}", e.toString());
                continue;
            }
            if (flowRule == null) {
                continue;
            }
            flowRules.add(flowRule);
        }
        return flowRules.toArray(new FlowRule[0]);
    }


    private class InternalCodecContext extends BaseResource implements CodecContext {
        @Override
        public ObjectMapper mapper() {
            return mapper;
        }

        @Override
        public <T> JsonCodec<T> codec(Class<T> entityClass) {
            return codecService.getCodec(entityClass);
        }

        @Override
        public <T> T getService(Class<T> serviceClass) {
            return get(serviceClass);
        }
    }

}
