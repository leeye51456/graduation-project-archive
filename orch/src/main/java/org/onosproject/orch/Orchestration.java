package org.onosproject.orch;

import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.codec.CodecService;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.HostLocation;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.orch.monitor.ResourceTables;
import org.onosproject.orch.monitor.TopologyInformation;

import java.util.ArrayList;


public interface Orchestration {

    ApplicationId getApplicationId();

    TopologyService getTopologyService();
    DeviceService getDeviceService();
    LinkService getLinkService();
    HostService getHostService();
    CodecService getCodecService();

    ResourceTables getResourceTables();
    TopologyInformation getTopologyInformation();

    boolean isImplicitOrchestration();
    String getSchemeForExplicit();
    String getSchemeForImplicit();

    boolean changeOrchestrationToExplicit();
    boolean changeOrchestrationToImplicit();

    void executeTopologyDiscoveryAndAddChildren(ArrayList<String> children);

    void addDevice(DeviceId deviceId);
    void addLink(ConnectPoint src, ConnectPoint dst);
    void deleteLink(ConnectPoint src, ConnectPoint dst);
    void addHost(MacAddress macAddress, VlanId vlanId, HostLocation location);

    void divideProvisioning(ConnectPoint src, ConnectPoint dst);
    void divideProvisioning(ConnectPoint src, ConnectPoint dst, MacAddress srcMac, MacAddress dstMac);
    void applyFlowRulesToMyself(String jsonString);

}
