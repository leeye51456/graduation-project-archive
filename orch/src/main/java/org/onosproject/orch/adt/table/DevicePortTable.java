package org.onosproject.orch.adt.table;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;

import java.util.concurrent.ConcurrentHashMap;


public class DevicePortTable {
    private final ConcurrentHashMap<DeviceId, ConcurrentHashMap<Long, ConnectPoint>> table;
    private final ConcurrentHashMap<ConnectPoint, ConnectPoint> checkTable;
    private final ConcurrentHashMap<DeviceId, Long> nextPortNumbers;

    public DevicePortTable() {
        table = new ConcurrentHashMap<>();
        checkTable = new ConcurrentHashMap<>();
        nextPortNumbers = new ConcurrentHashMap<>();
    }

    // initialize port table for deviceId and return true if not initialized.
    // stay and return false if already initialized
    public synchronized void initializeForDeviceIfNot(DeviceId implicitDeviceId) {
        // already initialized
        if (isInitializedForDevice(implicitDeviceId)) {
            return;
        }

        table.put(implicitDeviceId, new ConcurrentHashMap<>());
        nextPortNumbers.put(implicitDeviceId, 1L);
    }

    // allocate and return implicit port for deviceId
    public synchronized long putAndAllocatePort(DeviceId implicitDeviceId, ConnectPoint explicitConnectPoint) {
        if (!isInitializedForDevice(implicitDeviceId)) {
            return 0; // not initialized
        }

        // duplicate check
        if (isMapped(explicitConnectPoint)) {
            return checkTable.get(explicitConnectPoint).port().toLong();
        }

        long allocatedPort = getAndIncreaseNextPortNumber(implicitDeviceId);
        table.get(implicitDeviceId).put(allocatedPort, explicitConnectPoint);
        checkTable.put(explicitConnectPoint, new ConnectPoint(implicitDeviceId, PortNumber.portNumber(allocatedPort)));
        return allocatedPort;
    }

    public ConnectPoint getExplicitConnectPoint(DeviceId implicitDeviceId, long port) {
        if (!isAllocated(implicitDeviceId, port)) {
            return null;
        }

        return table.get(implicitDeviceId).get(port);
    }

    private boolean isMapped(ConnectPoint explicitConnectPoint) {
        return checkTable.containsKey(explicitConnectPoint);
    }

    private boolean isInitializedForDevice(DeviceId implicitDeviceId) {
        return table.containsKey(implicitDeviceId);
    }

    private boolean isAllocated(DeviceId implicitDeviceId, long port) {
        if (!isInitializedForDevice(implicitDeviceId)) {
            return false;
        }

        return table.get(implicitDeviceId).containsKey(port);
    }

    private synchronized long getAndIncreaseNextPortNumber(DeviceId implicitDeviceId) {
        long nextPortNumber = nextPortNumbers.get(implicitDeviceId);
        nextPortNumbers.put(implicitDeviceId, nextPortNumber + 1);
        return nextPortNumber;
    }
}
