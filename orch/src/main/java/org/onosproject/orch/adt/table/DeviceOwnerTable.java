package org.onosproject.orch.adt.table;

import org.onosproject.net.DeviceId;

import java.util.concurrent.ConcurrentHashMap;


public class DeviceOwnerTable {
    private ConcurrentHashMap<DeviceId, String> table; // DeviceId:IP

    public DeviceOwnerTable() {
        table = new ConcurrentHashMap<>();
    }

    public void put(DeviceId deviceId, String ip) {
        table.put(deviceId, ip);
    }

    public String getOwnerIp(DeviceId deviceId) {
        return table.get(deviceId);
    }
}
