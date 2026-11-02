package de.dennisguse.opentracks.sensors.driver;

import android.content.Context;
import android.os.Handler;

public interface Driver {

    //TODO Address is optional for internal drivers. Move address to driver instantiation.
    void connect(Context context, Handler handler, String address);

    //TODO Remove from interface?
    boolean isConnected();

    default void updateDriverParameters(Context context, Handler handler) {
        throw new UnsupportedOperationException();
    }

    void disconnect();
}
