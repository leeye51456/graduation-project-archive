package org.onosproject.orch.adt.elem;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;

import java.util.Objects;


public class DirectedLink extends AbstractSimpleLink {

    public DirectedLink(ConnectPoint src, ConnectPoint dst) {
        super(src, dst);
    }
    public DirectedLink(DeviceId srcId, long srcPort, DeviceId dstId, long dstPort) {
        super(srcId, srcPort, dstId, dstPort);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        DirectedLink that = (DirectedLink)obj;
        return (this.getSrc().deviceId().equals(that.getSrc().deviceId())
                && this.getDst().deviceId().equals(that.getDst().deviceId()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getSrc().deviceId(), this.getDst().deviceId());
    }
}
