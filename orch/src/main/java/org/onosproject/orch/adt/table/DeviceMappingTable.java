package org.onosproject.orch.adt.table;

import org.onosproject.net.DeviceId;

import java.util.concurrent.ConcurrentHashMap;


public class DeviceMappingTable {
    private ConcurrentHashMap<DeviceId, DeviceId> table; // explicit:implicit

    public DeviceMappingTable() {
        table = new ConcurrentHashMap<>();
    }

    public void put(DeviceId explicitDevice, DeviceId implicitDevice) {
        table.put(explicitDevice, implicitDevice);
    }

    public DeviceId getImplicit(DeviceId explicitDevice) {
        return table.get(explicitDevice);
    }
}
