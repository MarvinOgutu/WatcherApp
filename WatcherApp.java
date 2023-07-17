import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WatcherApp {

    private static final String broker = "tcp://broker.emqx.io:1883";
    private static final String connectionStatusTopic = "gateway/connection_status";
    private static final String networkStatsTopic = "gateway/network_stats";

    private static boolean connected = false;
    private static long connectionStartTime = 0;
    private static long connectionEndTime = 0;
    private static int retryCount = 0;

    public static void main(String[] args) {
        String clientId = "WatcherAppClient";
        MemoryPersistence persistence = new MemoryPersistence();

        try {
            MqttClient mqttClient = new MqttClient(broker, clientId, persistence);

            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.out.println("Connection lost.");
                    connected = false;
                    connectionEndTime = System.currentTimeMillis();
                    retryCount++;

                    // Send disconnection status to the MQTT broker
                    publishToMQTT(mqttClient, connectionStatusTopic, "disconnected");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    if (topic.equals(connectionStatusTopic)) {
                        String payload = new String(message.getPayload());
                        if (payload.equals("connected")) {
                            recordConnection();
                        } else if (payload.equals("disconnected")) {
                            recordDisconnection();
                        }
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used in this example
                }
            });

            mqttClient.connect(connOpts);
            mqttClient.subscribe(connectionStatusTopic);

            connected = true;
            publishToMQTT(mqttClient, connectionStatusTopic, "connected");
            connectionStartTime = System.currentTimeMillis();
            System.out.println("Connected to MQTT broker.");

            // Main program logic
            while (true) {
                if (connected) {
                    // Perform your desired operations here
                    System.out.println("Network connection is active");
                    // Example: Send sensor data to the MQTT broker
                    // publishToMQTT(mqttClient, "sensor_data", "Sensor value");
                } else {
                    System.out.println("Network connection is lost");
                    // Take appropriate actions when the network connection is lost
                }

                try {
                    Thread.sleep(10000); // Sleep for 10 seconds
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                // Check if connection has been lost for more than 5 seconds
                if (connected && System.currentTimeMillis() - connectionEndTime > 5000) {
                    connected = false;
                    publishToMQTT(mqttClient, connectionStatusTopic, "disconnected");
                }

                // Check if the connection should be retried
                if (!connected && System.currentTimeMillis() - connectionEndTime > 5000) {
                    retryCount++;
                    publishToMQTT(mqttClient, connectionStatusTopic, "connected");
                    connectionStartTime = System.currentTimeMillis();
                    System.out.println("Retrying connection at " + getTimeString(connectionStartTime));
                }

                // Send network statistics
                if (connectionStartTime != 0) {
                    String stats = "Connection time: " + getTimeString(connectionStartTime) + " - " + getTimeString(connectionEndTime)
                            + " | Retries: " + retryCount;
                    publishToMQTT(mqttClient, networkStatsTopic, stats);
                }
            }

        } catch (MqttException me) {
            me.printStackTrace();
        }
    }

    private static void recordConnection() {
        connectionStartTime = System.currentTimeMillis();
        System.out.println("Connection established at " + getTimeString(connectionStartTime));
        // Send connection status to the MQTT broker
        // publishToMQTT(mqttClient, networkStatsTopic, "Connection established at " + getTimeString(connectionStartTime));
    }

    private static void recordDisconnection() {
        connectionEndTime = System.currentTimeMillis();
        System.out.println("Connection lost at " + getTimeString(connectionEndTime));
        // Send disconnection status to the MQTT broker
        // publishToMQTT(mqttClient, networkStatsTopic, "Connection lost at " + getTimeString(connectionEndTime));
    }

    private static void publishToMQTT(MqttClient mqttClient, String topic, String message) {
        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttClient.publish(topic, mqttMessage);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private static String getTimeString(long timeMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(timeMillis));
    }
}
