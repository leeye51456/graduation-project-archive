package org.onosproject.orch.core.explicit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.*;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.orch.Orchestration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class FlowRuleJsonConstructor {

    private final int PRIORITY_BY_IN_PORT_AND_ETH_SRC = 41000;
    private final int PRIORITY_BY_ETH_DST = 50000;

    private final int TIMEOUT_BY_IN_PORT_AND_ETH_SRC = 5;
    private final int TIMEOUT_BY_ETH_DST = 30;


    private final Logger log = LoggerFactory.getLogger(getClass());


    private ApplicationId appId;
    private TopologyService topologyService;


    public FlowRuleJsonConstructor(Orchestration orch) {
        appId = orch.getApplicationId();
        topologyService = orch.getTopologyService();
    }


    public Set<JSONObject> buildFlowRuleJson(ConnectPoint src, ConnectPoint dst, MacAddress srcMac, MacAddress dstMac) {

        Topology topology = topologyService.currentTopology();
        Set<Path> paths = topologyService.getPaths(topology, src.deviceId(), dst.deviceId());

        FlowRuleJsonTable table = new FlowRuleJsonTable(srcMac, dstMac);

        // between two hosts connected to the same device
        // same device -> provisioning request to the controller which has that device
        if (paths.size() <= 0) {
            PortNumber inPort = src.port();
            ConnectPoint output = dst;
            table.addRules(inPort, output);
            return table.getRules();
        }

        // traversing links on the path,
        // build provisioning request whenever owner controller of src and dst becomes different
        List<Link> links = paths.iterator().next().links();


        // first device: IN_PORT = port toward the source host
        //               OUTPUT = port toward the next device
        {
            PortNumber inPort = src.port();
            ConnectPoint output = links.get(0).src();
            table.addRules(inPort, output);
        }

        // intermediate devices: IN_PORT = port toward the previous device
        //                       OUTPUT = port toward the next device
        for (int i = 1; i < links.size(); i++) {
            PortNumber inPort = links.get(i - 1).dst().port();
            ConnectPoint output = links.get(i).src();
            table.addRules(inPort, output);
        }

        // last device: IN_PORT = port toward the previous device
        //              OUTPUT = port toward the destination host
        {
            PortNumber inPort = links.get(links.size() - 1).dst().port();
            ConnectPoint output = dst;
            table.addRules(inPort, output);
        }

        return table.getRules();
    }


    private class FlowRuleJsonTable {

        private Set<JSONObject> rules;
        private MacAddress src;
        private MacAddress dst;

        protected FlowRuleJsonTable(MacAddress src, MacAddress dst) {
            this.src = src;
            this.dst = dst;

            rules = new HashSet<>();
        }

        public Set<JSONObject> getRules() {
            return rules;
        }

        public void addRules(PortNumber inPort, ConnectPoint outPoint) {
            DeviceId deviceId = outPoint.deviceId();

            JSONObject flowRuleJsonByDst, flowRuleJsonByInPortAndSrc;
            try {
                flowRuleJsonByInPortAndSrc = buildNewFlowRuleByInPortAndSrc(deviceId, inPort, outPoint.port());
                flowRuleJsonByDst = buildNewFlowRuleByDst(deviceId, outPoint.port());
            } catch (JSONException e) {
                log.warn("exception: {}", e.toString());
                return;
            }

            rules.add(flowRuleJsonByDst);
            rules.add(flowRuleJsonByInPortAndSrc);
        }

        private JSONObject buildNewFlowRuleByInPortAndSrc(DeviceId deviceId,
                                                          PortNumber inPort, PortNumber output) throws JSONException {
            JSONObject root
                    = buildBaseFlowRule(PRIORITY_BY_IN_PORT_AND_ETH_SRC, TIMEOUT_BY_IN_PORT_AND_ETH_SRC, deviceId);
            root.put("treatment", buildTreatmentNode(output));
            root.put("selector", buildSelectorNodeWithInPortAndSrc(inPort));
            return root;
        }

        private JSONObject buildNewFlowRuleByDst(DeviceId deviceId, PortNumber output) throws JSONException {
            JSONObject root = buildBaseFlowRule(PRIORITY_BY_ETH_DST, TIMEOUT_BY_ETH_DST, deviceId);
            root.put("treatment", buildTreatmentNode(output));
            root.put("selector", buildSelectorNodeWithDst());
            return root;
        }

        private JSONObject buildBaseFlowRule(int priority, int timeout, DeviceId deviceId) throws JSONException {
            JSONObject root = new JSONObject();

            root.put("priority", priority);
            if (timeout <= 0) {
                root.put("timeout", 0);
                root.put("isPermanent", true);
            } else {
                root.put("timeout", timeout);
                root.put("isPermanent", false);
            }
            root.put("deviceId", deviceId.toString());
            root.put("appId", appId.name());

            return root;
        }

        private JSONObject buildTreatmentNode(PortNumber output) throws JSONException {
            JSONObject treatment = new JSONObject();
            JSONArray instructions = new JSONArray();

            JSONObject outputInstruction = new JSONObject();
            outputInstruction.put("type", "OUTPUT");
            outputInstruction.put("port", output.toString());
            instructions.put(outputInstruction);

            treatment.put("instructions", instructions);
            return treatment;
        }

        private JSONObject buildSelectorNodeWithInPortAndSrc(PortNumber inPort) throws JSONException {
            JSONObject selector = new JSONObject();
            JSONArray criteria = new JSONArray();

            JSONObject inPortCriteria = new JSONObject();
            inPortCriteria.put("type", "IN_PORT");
            inPortCriteria.put("port", inPort.toString());
            criteria.put(inPortCriteria);

            JSONObject ethSrcCriteria = new JSONObject();
            ethSrcCriteria.put("type", "ETH_SRC");
            ethSrcCriteria.put("mac", src.toString());
            criteria.put(ethSrcCriteria);

            selector.put("criteria", criteria);
            return selector;
        }

        private JSONObject buildSelectorNodeWithDst() throws JSONException {
            JSONObject selector = new JSONObject();
            JSONArray criteria = new JSONArray();

            JSONObject ethSrcCriteria = new JSONObject();
            ethSrcCriteria.put("type", "ETH_DST");
            ethSrcCriteria.put("mac", dst.toString());
            criteria.put(ethSrcCriteria);

            selector.put("criteria", criteria);
            return selector;
        }

    }

}
