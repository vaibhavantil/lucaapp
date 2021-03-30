package de.culture4life.luca.location;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

public class GeofenceEvent {

    private GeofencingEvent wrappedEvent;

    public GeofenceEvent(GeofencingEvent wrappedEvent) {
        this.wrappedEvent = wrappedEvent;
    }

    public boolean didEnter() {
        return wrappedEvent.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER;
    }

    public boolean didExit() {
        return wrappedEvent.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_EXIT;
    }

    public GeofencingEvent getWrappedEvent() {
        return wrappedEvent;
    }

    @Override
    public String toString() {
        return "GeofenceEvent{" +
                "transition=" + wrappedEvent.getGeofenceTransition() +
                ", triggeringLocation=" + wrappedEvent.getTriggeringLocation() +
                '}';
    }

}
