package org.onosproject.orch.adt.elem;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;


public class UndirectedLink extends AbstractSimpleLink {

    public UndirectedLink(ConnectPoint src, ConnectPoint dst) {
        super(src, dst);
    }
    public UndirectedLink(DeviceId srcId, long srcPort, DeviceId dstId, long dstPort) {
        super(srcId, srcPort, dstId, dstPort);
    }

    public static UndirectedLink getInverted(UndirectedLink undirectedLink) {
        return new UndirectedLink(undirectedLink.getDst(), undirectedLink.getSrc());
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
        UndirectedLink that = (UndirectedLink)obj;
        boolean equal1 = (this.getSrc().deviceId().equals(that.getSrc().deviceId())
                && this.getDst().deviceId().equals(that.getDst().deviceId()));
        boolean equal2 = (this.getSrc().deviceId().equals(that.getDst().deviceId())
                && this.getDst().deviceId().equals(that.getSrc().deviceId()));
        return equal1 || equal2;
    }

    @Override
    public int hashCode() {
        return this.getSrc().deviceId().hashCode() + this.getDst().deviceId().hashCode();
    }
}
