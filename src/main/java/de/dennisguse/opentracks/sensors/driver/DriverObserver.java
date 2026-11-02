package de.dennisguse.opentracks.sensors.driver;


public interface DriverObserver<T> {
    /**
     * The Driver could connect to the sensor.
     */
    void onConnected(String sensorAddress, String sensorName);

    /**
     * Sensor got temporarily disconnected, Driver should try to re-connect.
     */
    void onConnectionLost();

    /**
     * The Driver provided data from the sensor.
     */
    void onDataReceived(T value);

    /**
     * The Driver was shutdown and no further data will be provided.
     */
    //TODO check if really need this, because the driver was told to disconnect and then just signalizes that it finished this task.
    void onDisconnected();

    /**
     * The Driver detected that the sensor got deactivated.
     * For example, the user deactivated the internal GPS.
     */
    default void onSensorDeactivated() {}
}
