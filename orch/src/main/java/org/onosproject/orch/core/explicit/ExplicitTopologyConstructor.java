package org.onosproject.orch.core.explicit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.HostLocation;
import org.onosproject.net.PortNumber;
import org.onosproject.orch.Orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ExplicitTopologyConstructor {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final String EDGE_UPDATES = "edgeUpdates";

    private final JSONObject json;
    private final String serverIp;
    private final Orchestration orch;

    private boolean hasBeenParsed;


    private ExplicitTopologyConstructor(JSONObject json, String serverIp, Orchestration orch) {
        this.json = json;
        this.serverIp = serverIp;
        this.orch = orch;
        hasBeenParsed = false;
    }
    public static ExplicitTopologyConstructor create(JSONObject json, String serverIp, Orchestration orch) {
        return new ExplicitTopologyConstructor(json, serverIp, orch);
    }


    public ExplicitTopologyConstructor applyJson() {
        if (hasBeenParsed) {
            log.info("json already applied: {}", json);
            return this;
        }

        hasBeenParsed = true;

        if (json.has(EDGE_UPDATES)) {
            return applyEdgeUpdatesJson();
        }

        for (ElementType elementType : ElementType.values()) {
            if (json.has(elementType.property)) {
                return applyJsonWithRootProperty(elementType);
            }
        }

        log.warn("cannot resolve the json message for explicit orchestration: {}", json);
        return this;
    }


    private ExplicitTopologyConstructor applyEdgeUpdatesJson() {
        JSONArray addedNode, deletedNode;
        try {
            JSONObject edgeUpdatesNode = json.getJSONObject(EDGE_UPDATES);
            addedNode = edgeUpdatesNode.getJSONArray("added");
            deletedNode = edgeUpdatesNode.getJSONArray("deleted");
        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            return this;
        }

        int length;

        length = addedNode.length();
        for (int i = 0; i < length; i++) {
            JSONObject linkNode;
            try {
                linkNode = addedNode.getJSONObject(i);
            } catch (JSONException e) {
                log.warn("exception: {}", e.toString());
                continue;
            }
            ElementType.LINKS.applySingleElementJson(linkNode, serverIp, orch, false);
        }

        length = deletedNode.length();
        for (int i = 0; i < length; i++) {
            JSONObject linkNode;
            try {
                linkNode = deletedNode.getJSONObject(i);
            } catch (JSONException e) {
                log.warn("exception: {}", e.toString());
                continue;
            }
            ElementType.LINKS.applySingleElementJson(linkNode, serverIp, orch, true);
        }
        return this;
    }


    private ExplicitTopologyConstructor applyJsonWithRootProperty(ElementType elementType) {
        JSONArray elementsArrayNode;
        try {
            elementsArrayNode = json.getJSONArray(elementType.property);
        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            return this;
        }

        int length = elementsArrayNode.length();
        for (int i = 0; i < length; i++) {
            JSONObject singleElementNode;
            try {
                singleElementNode = elementsArrayNode.getJSONObject(i);
            } catch (JSONException e) {
                log.warn("exception: {}", e.toString());
                continue;
            }
            elementType.applySingleElementJson(singleElementNode, serverIp, orch, false);
        }

        return this;
    }


    private enum ElementType {

        DEVICES("devices") {
            void applySingleElementJson(JSONObject deviceNode, String serverIp, Orchestration orch, boolean removal) {
                if (removal) {
                    return;
                }

                try {
                    String deviceIdNode = deviceNode.getString("id");

                    String[] deviceIdNodeSplit = deviceIdNode.split(":"); // { scheme, id }
                    if (deviceIdNodeSplit.length != 2) {
                        log.warn("id of device is invalid: {}", deviceIdNode);
                    }

                    DeviceId originalDeviceId = DeviceId.deviceId(deviceIdNode);
                    DeviceId deviceId = DeviceId.deviceId(orch.getSchemeForExplicit() + ":" + deviceIdNodeSplit[1]);
                    orch.getResourceTables().putIntoActualDeviceIdTable(deviceId, originalDeviceId);
                    orch.getResourceTables().putIntoDeviceOwnerTable(deviceId, serverIp);

                    orch.addDevice(deviceId);

                } catch (JSONException e) {
                    log.warn("exception: {}", e.toString());
                }
            }
        },

        LINKS("links") {
            void applySingleElementJson(JSONObject linkNode, String serverIp, Orchestration orch, boolean removal) {
                try {
                    JSONObject srcNode = linkNode.getJSONObject("src");
                    JSONObject dstNode = linkNode.getJSONObject("dst");
                    String srcDeviceNode = srcNode.getString("device");
                    String dstDeviceNode = dstNode.getString("device");

                    PortNumber srcPort = PortNumber.portNumber(srcNode.getLong("port"));
                    PortNumber dstPort = PortNumber.portNumber(dstNode.getLong("port"));

                    String[] srcIdNodeSplit = srcDeviceNode.split(":");
                    String[] dstIdNodeSplit = dstDeviceNode.split(":");
                    if (srcIdNodeSplit.length != 2 || dstIdNodeSplit.length != 2) {
                        log.warn("id of src ({}) or dst ({}) of link is is invalid", srcDeviceNode, dstDeviceNode);
                    }

                    DeviceId srcId = DeviceId.deviceId(orch.getSchemeForExplicit() + ":" + srcIdNodeSplit[1]);
                    DeviceId dstId = DeviceId.deviceId(orch.getSchemeForExplicit() + ":" + dstIdNodeSplit[1]);

                    ConnectPoint src = new ConnectPoint(srcId, srcPort);
                    ConnectPoint dst = new ConnectPoint(dstId, dstPort);

                    if (removal) {
                        orch.deleteLink(src, dst);
                    } else {
                        orch.addLink(src, dst);
                    }

                } catch (JSONException e) {
                    log.warn("exception: {}", e.toString());
                }
            }
        },

        HOSTS("hosts") {
            void applySingleElementJson(JSONObject hostNode, String serverIp, Orchestration orch, boolean removal) {
                if (removal) {
                    return;
                }

                try {
                    String macNode = hostNode.getString("mac");
                    String vlanNode = hostNode.getString("vlan");

                    JSONObject locationNode = hostNode.getJSONObject("location");
                    String locationElementIdNode = locationNode.getString("elementId");
                    long locationPortNode = locationNode.getLong("port");

                    String[] locationElementIdSplit = locationElementIdNode.split(":");

                    if (locationElementIdSplit.length == 2) {
                        MacAddress macAddress = MacAddress.valueOf(macNode);
                        VlanId vlanId = VlanId.vlanId(vlanNode);

                        DeviceId locationId = DeviceId.deviceId(
                                orch.getSchemeForExplicit() + ":" + locationElementIdSplit[1]);
                        PortNumber locationPort = PortNumber.portNumber(locationPortNode);
                        HostLocation location = new HostLocation(locationId, locationPort, 0);

                        orch.addHost(macAddress, vlanId, location);
                    }

                } catch (JSONException e) {
                    log.warn("exception: {}", e.toString());
                }
            }
        };

        private static final Logger log = LoggerFactory.getLogger(ElementType.class);
        public final String property;

        ElementType(String property) {
            this.property = property;
        }

        abstract void applySingleElementJson(
                JSONObject singleElementNode, String serverIp, Orchestration orch, boolean removal);
    }

}
