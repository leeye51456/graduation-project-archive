package org.onosproject.orch.core.implicit;

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

import org.onosproject.orch.adt.elem.DirectedLink;
import org.onosproject.orch.adt.elem.UndirectedLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ImplicitTopologyConstructor {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final String EDGE_UPDATES = "edgeUpdates";

    private final JSONObject json;
    private final String serverIp;
    private final Orchestration orch;

    private boolean hasBeenParsed;


    private ImplicitTopologyConstructor(JSONObject json, String serverIp, Orchestration orch) {
        this.json = json;
        this.serverIp = serverIp;
        this.orch = orch;
        hasBeenParsed = false;
    }
    public static ImplicitTopologyConstructor create(JSONObject json, String serverIp, Orchestration orch) {
        return new ImplicitTopologyConstructor(json, serverIp, orch);
    }


    public ImplicitTopologyConstructor applyJson() {
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

        log.warn("cannot resolve the json message for implicit orchestration: {}", json);
        return this;
    }


    private ImplicitTopologyConstructor applyEdgeUpdatesJson() {
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


    private ImplicitTopologyConstructor applyJsonWithRootProperty(ElementType elementType) {
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
                    DeviceId explicitDeviceId = DeviceId.deviceId(deviceIdNode);
                    DeviceId implicitDeviceId = buildImplicitDeviceId(serverIp, orch);
                    orch.getResourceTables().putIntoDeviceMappingTable(explicitDeviceId, implicitDeviceId);
                    orch.getResourceTables().putIntoDeviceOwnerTable(implicitDeviceId, serverIp);
                    orch.addDevice(implicitDeviceId);

                } catch (JSONException e) {
                    log.warn("exception: {}", e.toString());
                }
            }
        },

        LINKS("links") {
            void applySingleElementJson(JSONObject linkNode, String serverIp, Orchestration orch, boolean removal) {
                try {
                    JSONObject srcNode = linkNode.getJSONObject("src");
                    DeviceId srcId = DeviceId.deviceId(srcNode.getString("device"));
                    PortNumber srcPort = PortNumber.portNumber(srcNode.getLong("port"));
                    ConnectPoint src = new ConnectPoint(srcId, srcPort);

                    JSONObject dstNode = linkNode.getJSONObject("dst");
                    DeviceId dstId = DeviceId.deviceId(dstNode.getString("device"));
                    PortNumber dstPort = PortNumber.portNumber(dstNode.getLong("port"));
                    ConnectPoint dst = new ConnectPoint(dstId, dstPort);

                    DirectedLink explicitLink = new DirectedLink(src, dst);

                    if (removal) {
                        UndirectedLink implicitLink = orch.getResourceTables()
                                .getImplicitFromLinkMappingTable(explicitLink);
                        orch.getResourceTables().disconnectAndRemoveFromLinkMappingTable(explicitLink);
                        log.info("disconnect explicit-implicit link pair from link table; " +
                                "explicit link is {} and implicit link is {}", explicitLink, implicitLink);

                        if (!orch.getResourceTables().isConnectedInLinkMappingTable(implicitLink)) {
                            orch.deleteLink(implicitLink.getSrc(), implicitLink.getDst());
                            orch.deleteLink(implicitLink.getDst(), implicitLink.getSrc());
                        }

                    } else {
                        UndirectedLink implicitLink = orch.getResourceTables()
                                .buildImplicitLinkAndPutIntoLinkMappingTable(explicitLink);
                        log.info("put explicit-implicit link pair into link table and add implicit link into core; " +
                                "explicit link is {} and implicit link is {}", explicitLink, implicitLink);

                        orch.addLink(implicitLink.getSrc(), implicitLink.getDst());
                        orch.addLink(implicitLink.getDst(), implicitLink.getSrc());
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
                    MacAddress macAddress = MacAddress.valueOf(hostNode.getString("mac"));
                    VlanId vlanId = VlanId.vlanId(hostNode.getString("vlan"));

                    JSONObject locationNode = hostNode.getJSONObject("location");
                    DeviceId locationId = DeviceId.deviceId(locationNode.getString("elementId"));
                    PortNumber locationPort = PortNumber.portNumber(locationNode.getLong("port"));
                    ConnectPoint explicitLocation = new ConnectPoint(locationId, locationPort);

                    DeviceId implicitDeviceId = ElementType.buildImplicitDeviceId(serverIp, orch);
                    long implicitPort = orch.getResourceTables().putIntoPortTableAndAllocatePort(explicitLocation);
                    PortNumber implicitPortNumber = PortNumber.portNumber(implicitPort);
                    HostLocation implicitLocation = new HostLocation(implicitDeviceId, implicitPortNumber, 0);

                    orch.addHost(macAddress, vlanId, implicitLocation);

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

        abstract void applySingleElementJson(JSONObject singleElementNode, String serverIp,
                                             Orchestration orch, boolean removal);

        private static DeviceId buildImplicitDeviceId(String serverIp, Orchestration orch) {
            // W.X.Y.Z -> imvs:f00Wf00Xf00Yf00Z
            StringBuilder sb = new StringBuilder(orch.getSchemeForImplicit());
            sb.append(':');
            String[] serverIpSegments = serverIp.split("\\.");
            for (String segment : serverIpSegments) {
                sb.append(String.format("f%3s", segment).replace(' ', '0'));
            }
            return DeviceId.deviceId(sb.toString());
        }
    }

}
