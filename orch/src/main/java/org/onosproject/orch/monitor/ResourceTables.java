package org.onosproject.orch.monitor;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.orch.adt.elem.DirectedLink;
import org.onosproject.orch.adt.elem.SimpleHost;
import org.onosproject.orch.adt.elem.UndirectedLink;
import org.onosproject.orch.adt.table.*;

import java.util.concurrent.ConcurrentHashMap;


public class ResourceTables {

    private ConcurrentHashMap<DeviceId, String> deviceOwnerTable; // DeviceId:IP
    private ActualDeviceIdTable actualDeviceIdTable; // exvs:(actual)

    private DevicePortTable implicitDevicePortTable;

    private DeviceMappingTable deviceMappingTable;
    private LinkMappingTable linkMappingTable;
    private HostMappingTable hostMappingTable;


    public ResourceTables() {
        deviceOwnerTable = new ConcurrentHashMap<>();
        actualDeviceIdTable = new ActualDeviceIdTable();

        implicitDevicePortTable = new DevicePortTable();

        deviceMappingTable = new DeviceMappingTable();
        linkMappingTable = new LinkMappingTable();
        hostMappingTable = new HostMappingTable();
    }


    public void putIntoDeviceOwnerTable(DeviceId deviceId, String ip) {
        deviceOwnerTable.put(deviceId, ip);
    }

    public String getFromDeviceOwnerTable(DeviceId deviceId) {
        return deviceOwnerTable.get(deviceId);
    }


    public void putIntoActualDeviceIdTable(DeviceId explicitDevice, DeviceId actualDevice) {
        actualDeviceIdTable.put(explicitDevice, actualDevice);
    }

    public DeviceId getActualDeviceId(DeviceId explicitDevice) {
        return actualDeviceIdTable.getActualDeviceId(explicitDevice);
    }
    public DeviceId getExplicitDeviceIdFromActual(DeviceId actualDevice) {
        return actualDeviceIdTable.getExplicitDeviceId(actualDevice);
    }


    public long putIntoPortTableAndAllocatePort(ConnectPoint explicitConnectPoint) {
        DeviceId implicitDeviceId = deviceMappingTable.getImplicit(explicitConnectPoint.deviceId());
        return implicitDevicePortTable.putAndAllocatePort(implicitDeviceId, explicitConnectPoint);
    }

    public ConnectPoint getExplicitConnectPoint(DeviceId deviceId, long port) {
        return implicitDevicePortTable.getExplicitConnectPoint(deviceId, port);
    }


    public void putIntoDeviceMappingTable(DeviceId explicitDevice, DeviceId implicitDevice) {
        implicitDevicePortTable.initializeForDeviceIfNot(implicitDevice);
        deviceMappingTable.put(explicitDevice, implicitDevice);
    }

    public DeviceId getImplicitFromDeviceMappingTable(DeviceId explicitDevice) {
        return deviceMappingTable.getImplicit(explicitDevice);
    }


    public UndirectedLink buildImplicitLinkAndPutIntoLinkMappingTable(DirectedLink explicitLink) {
        ConnectPoint explicitLinkSrc = explicitLink.getSrc();
        ConnectPoint explicitLinkDst = explicitLink.getDst();

        DeviceId implicitLinkSrcId = getImplicitFromDeviceMappingTable(explicitLinkSrc.deviceId());
        DeviceId implicitLinkDstId = getImplicitFromDeviceMappingTable(explicitLinkDst.deviceId());

        UndirectedLink implicitLink = linkMappingTable.getImplicitLinkBetween(implicitLinkSrcId, implicitLinkDstId);
        if (implicitLink == null) {
            long implicitLinkSrcPort = implicitDevicePortTable.putAndAllocatePort(implicitLinkSrcId, explicitLinkSrc);
            long implicitLinkDstPort = implicitDevicePortTable.putAndAllocatePort(implicitLinkDstId, explicitLinkDst);
            implicitLink = new UndirectedLink(implicitLinkSrcId, implicitLinkSrcPort,
                    implicitLinkDstId, implicitLinkDstPort);
        }

        linkMappingTable.put(explicitLink, implicitLink);

        return implicitLink;
    }

    public UndirectedLink getImplicitFromLinkMappingTable(DirectedLink explicitLink) {
        return linkMappingTable.getImplicit(explicitLink);
    }

    public void disconnectAndRemoveFromLinkMappingTable(DirectedLink explicitLink) {
        linkMappingTable.disconnectAndRemove(explicitLink);
    }

    public boolean isConnectedInLinkMappingTable(UndirectedLink implicitLink) {
        return linkMappingTable.isConnected(implicitLink);
    }

    public boolean containsInLinkMappingTable(DirectedLink explicitLink) {
        return linkMappingTable.contains(explicitLink);
    }
    public boolean containsInLinkMappingTable(UndirectedLink implicitLink) {
        return linkMappingTable.contains(implicitLink);
    }


    public void putIntoHostMappingTable(SimpleHost explicitHost, SimpleHost implicitHost) {
        hostMappingTable.put(explicitHost, implicitHost);
    }

    public boolean containsExplicitHost(SimpleHost explicitHost) {
        return hostMappingTable.containsExplicitHost(explicitHost);
    }
    public boolean containsImplicitHost(SimpleHost implicitHost) {
        return hostMappingTable.containsImplicitHost(implicitHost);
    }

}
