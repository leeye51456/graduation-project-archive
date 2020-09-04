package org.onosproject.orch.adt.table;

import org.onosproject.net.DeviceId;
import org.onosproject.orch.adt.elem.DirectedLink;
import org.onosproject.orch.adt.elem.UndirectedLink;

import java.util.concurrent.ConcurrentHashMap;


public class LinkMappingTable {
    private final ConcurrentHashMap<UndirectedLink, ConcurrentHashMap<DirectedLink, Boolean>> im2exTable;
    private final ConcurrentHashMap<DirectedLink, UndirectedLink> ex2imTable;

    private final ConcurrentHashMap<DeviceId, ConcurrentHashMap<DeviceId, UndirectedLink>> connectionTable;

    public LinkMappingTable() {
        im2exTable = new ConcurrentHashMap<>();
        ex2imTable = new ConcurrentHashMap<>();
        connectionTable = new ConcurrentHashMap<>();
    }

    public void put(DirectedLink explicitLink, UndirectedLink implicitLink) {
        synchronized (im2exTable) {
            if (!contains(implicitLink)) {
                im2exTable.put(implicitLink, new ConcurrentHashMap<>());
            }
        }

        im2exTable.get(implicitLink).put(explicitLink, true);
        ex2imTable.put(explicitLink, implicitLink);

        DeviceId srcId = implicitLink.getSrc().deviceId();
        DeviceId dstId = implicitLink.getDst().deviceId();
        synchronized (connectionTable) {
            if (!connectionTable.containsKey(srcId)) {
                connectionTable.put(srcId, new ConcurrentHashMap<>());
            }
        }
        synchronized (connectionTable) {
            if (!connectionTable.containsKey(dstId)) {
                connectionTable.put(dstId, new ConcurrentHashMap<>());
            }
        }
        connectionTable.get(srcId).put(dstId, implicitLink);
        connectionTable.get(dstId).put(srcId, UndirectedLink.getInverted(implicitLink));
    }

    public UndirectedLink getImplicit(DirectedLink explicitLink) {
        return ex2imTable.get(explicitLink);
    }

    public UndirectedLink getImplicitLinkBetween(DeviceId deviceId1, DeviceId deviceId2) {
        if (!connectionTable.containsKey(deviceId1) || !connectionTable.containsKey(deviceId2)) {
            return null;
        }
        return connectionTable.get(deviceId1).get(deviceId2);
    }

    public void disconnectAndRemove(DirectedLink explicitLink) {
        if (!contains(explicitLink)) {
            return;
        }

        UndirectedLink implicitLink = ex2imTable.remove(explicitLink);
        im2exTable.get(implicitLink).put(explicitLink, false);
    }

    public boolean isConnected(UndirectedLink implicitLink) {
        if (!contains(implicitLink)) {
            return false;
        }

        return im2exTable.get(implicitLink).containsValue(true);
    }

    public boolean contains(DirectedLink explicitLink) {
        return ex2imTable.containsKey(explicitLink);
    }
    public boolean contains(UndirectedLink implicitLink) {
        return im2exTable.containsKey(implicitLink);
    }

    public boolean hasConnectionBetween(DeviceId deviceId1, DeviceId deviceId2) {
        if (!connectionTable.containsKey(deviceId1) || !connectionTable.containsKey(deviceId2)) {
            return false;
        }
        return connectionTable.get(deviceId1).containsKey(deviceId2)
                && connectionTable.get(deviceId2).containsKey(deviceId1);
    }

}
