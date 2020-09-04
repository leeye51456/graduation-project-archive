package org.onosproject.orch.adt.table;

import org.onosproject.orch.adt.elem.SimpleHost;

import java.util.concurrent.ConcurrentHashMap;


public class HostMappingTable {
    private final ConcurrentHashMap<SimpleHost, SimpleHost> im2exTable; // implicit:explicit
    private final ConcurrentHashMap<SimpleHost, SimpleHost> ex2imTable; // explicit:implicit

    public HostMappingTable() {
        im2exTable = new ConcurrentHashMap<>();
        ex2imTable = new ConcurrentHashMap<>();
    }

    public void put(SimpleHost explicitHost, SimpleHost implicitHost) {
        im2exTable.put(implicitHost, explicitHost);
        ex2imTable.put(explicitHost, implicitHost);
    }

    public boolean containsExplicitHost(SimpleHost explicitHost) {
        return ex2imTable.containsKey(explicitHost);
    }
    public boolean containsImplicitHost(SimpleHost implicitHost) {
        return im2exTable.containsKey(implicitHost);
    }
}
