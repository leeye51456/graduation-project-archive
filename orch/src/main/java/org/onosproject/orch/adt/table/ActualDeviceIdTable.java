package org.onosproject.orch.adt.table;

import org.onosproject.net.DeviceId;

import java.util.concurrent.ConcurrentHashMap;


public class ActualDeviceIdTable {

    private final ConcurrentHashMap<DeviceId, DeviceId> ex2acTable; // exvs:(actual)
    private final ConcurrentHashMap<DeviceId, DeviceId> ac2exTable; // (actual):exvs

    public ActualDeviceIdTable() {
        ex2acTable = new ConcurrentHashMap<>();
        ac2exTable = new ConcurrentHashMap<>();
    }

    public void put(DeviceId explicitDevice, DeviceId actualDevice) {
        ex2acTable.put(explicitDevice, actualDevice);
        ac2exTable.put(actualDevice, explicitDevice);
    }

    public DeviceId getExplicitDeviceId(DeviceId actualDevice) {
        return ac2exTable.get(actualDevice);
    }
    public DeviceId getActualDeviceId(DeviceId explicitDevice) {
        return ex2acTable.get(explicitDevice);
    }

}
