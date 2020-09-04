package org.onosproject.orch.adt.elem;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.HostId;

import java.util.Objects;


public class SimpleHost {
    private HostId id;
    private ConnectPoint location;

    public SimpleHost(HostId hostId, ConnectPoint location) {
        this.id = hostId;
        this.location = location;
    }

    public HostId getId() {
        return id;
    }
    public ConnectPoint getLocation() {
        return location;
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
        SimpleHost that = (SimpleHost)obj;
        // need to compare location, because it is used to find explicit/implicit hosts from host table
        return this.getId().equals(that.getId())
                && this.getLocation().equals(that.getLocation());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getLocation());
    }
}
