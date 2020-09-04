package org.onosproject.orch.adt.elem;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;


public abstract class AbstractSimpleLink {
    private ConnectPoint src;
    private ConnectPoint dst;

    public AbstractSimpleLink(ConnectPoint src, ConnectPoint dst) {
        this.src = src;
        this.dst = dst;
    }
    public AbstractSimpleLink(DeviceId srcId, long srcPort, DeviceId dstId, long dstPort) {
        this.src = new ConnectPoint(srcId, PortNumber.portNumber(srcPort));
        this.dst = new ConnectPoint(dstId, PortNumber.portNumber(dstPort));
    }

    public ConnectPoint getSrc() {
        return this.src;
    }
    public ConnectPoint getDst() {
        return this.dst;
    }

    @Override
    public String toString() {
        return this.src.toString() + ":" + this.dst.toString();
    }
}
