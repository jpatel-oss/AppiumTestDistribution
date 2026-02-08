package com.appium.device;

import com.appium.capabilities.Capabilities;
import com.appium.manager.AppiumServerManager;
import com.appium.utils.Api;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.appium.utils.OverriddenVariable.getOverriddenStringValue;

public class Devices {
    private static List<Device> instance;
    private static final Logger LOGGER = Logger.getLogger(Devices.class.getName());

    private Devices() {

    }

    @SneakyThrows
    public static List<Device> getConnectedDevices() {
        if (instance == null) {
            String deviceToExecute = getOverriddenStringValue("DEVICE_TO_EXECUTE", "local");

            if ("devicefarm".equalsIgnoreCase(deviceToExecute)) {
                // Get hub URL from capabilities
                JSONObject serverConfig = Capabilities.getInstance()
                        .getCapabilityObjectFromKey("serverConfig");
                String hubUrl = serverConfig
                        .getJSONObject("server")
                        .getJSONObject("plugin")
                        .getJSONObject("device-farm")
                        .getString("hub");

                // Make API call to hub
                String response = new Api().getResponse(hubUrl + "/device-farm/api/device");
                instance = Arrays.asList(new ObjectMapper().readValue(response, Device[].class));
            } else {
                // Existing local logic
                AppiumServerManager appiumServerManager = new AppiumServerManager();
                String remoteWDHubIP = appiumServerManager.getRemoteWDHubIP();
                URL url = new URL(remoteWDHubIP);
                String response = new Api().getResponse(url.getProtocol()
                        + "://" + url.getHost() + ":" + url.getPort() + "/device-farm/api/device");
                instance = Arrays.asList(new ObjectMapper().readValue(response, Device[].class));
            }
        }
        return instance;
    }

    /**
     * Set the busy status of a device by its UDID
     * 
     * @param udid The unique device identifier
     * @param busy The busy status to set (true = busy, false = available)
     * @return true if device was found and updated, false otherwise
     */
    @SneakyThrows
    public static boolean setDeviceBusy(String udid, boolean busy) {
        if (instance == null) {
            LOGGER.warn("No devices loaded. Call getConnectedDevices() first.");
            return false;
        }

        // Find the device in the local instance
        Optional<Device> deviceOptional = instance.stream()
                .filter(device -> udid.equals(device.getUdid()))
                .findFirst();

        if (!deviceOptional.isPresent()) {
            LOGGER.warn("Device with UDID " + udid + " not found in device list.");
            return false;
        }

        Device device = deviceOptional.get();
        device.busy = busy;

        // Update device farm if using device farm mode
        // String deviceToExecute = getOverriddenStringValue("DEVICE_TO_EXECUTE", "local");
        // if ("devicefarm".equalsIgnoreCase(deviceToExecute)) {
        //     updateDeviceFarmBusyStatus(udid, busy);
        // }

        LOGGER.info("Device " + udid + " busy status set to: " + busy);
        return true;
    }

    /**
     * Set a device as busy
     * 
     * @param udid The unique device identifier
     * @return true if device was found and updated, false otherwise
     */
    public synchronized static boolean setDeviceBusy(String udid) {
        return setDeviceBusy(udid, true);
    }

    /**
     * Set a device as available (not busy)
     * 
     * @param udid The unique device identifier
     * @return true if device was found and updated, false otherwise
     */
    public static boolean setDeviceAvailable(String udid) {
        return setDeviceBusy(udid, false);
    }

    /**
     * Get a device by its UDID
     * 
     * @param udid The unique device identifier
     * @return Optional containing the device if found, empty otherwise
     */
    public static Optional<Device> getDeviceByUdid(String udid) {
        if (instance == null) {
            return Optional.empty();
        }
        return instance.stream()
                .filter(device -> udid.equals(device.getUdid()))
                .findFirst();
    }

    /**
     * Check if a device is busy
     * 
     * @param udid The unique device identifier
     * @return true if device is busy, false if available or not found
     */
    public static boolean isDeviceBusy(String udid) {
        return getDeviceByUdid(udid)
                .map(Device::isBusy)
                .orElse(false);
    }

    /**
     * Update the busy status on the device farm hub via API
     * 
     * @param udid The unique device identifier
     * @param busy The busy status to set
     */
    @SneakyThrows
    private static void updateDeviceFarmBusyStatus(String udid, boolean busy) {
        try {
            JSONObject serverConfig = Capabilities.getInstance()
                    .getCapabilityObjectFromKey("serverConfig");
            String hubUrl = serverConfig
                    .getJSONObject("server")
                    .getJSONObject("plugin")
                    .getJSONObject("device-farm")
                    .getString("hub");

            // Construct the API endpoint for updating device status
            String apiUrl = hubUrl + "/device-farm/api/device/" + udid;
            
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Create JSON payload
            String jsonPayload = String.format("{\"busy\": %s}", busy);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_ACCEPTED) {
                LOGGER.info("Successfully updated device " + udid + " busy status on device farm");
            } else {
                LOGGER.warn("Failed to update device farm. Response code: " + responseCode);
            }
            
            conn.disconnect();
        } catch (Exception e) {
            LOGGER.error("Error updating device farm busy status: " + e.getMessage(), e);
        }
    }

    /**
     * Reset the device instance to force reload on next getConnectedDevices() call
     */
    public static void resetDeviceCache() {
        instance = null;
        LOGGER.info("Device cache cleared. Will reload on next getConnectedDevices() call.");
    }

    /**
     * Get the UDID of the first available (not busy) device
     * 
     * @return Optional containing the UDID of a free device, empty if no free device found
     */
    public static Optional<String> getFreeDeviceUdid() {
        if (instance == null) {
            LOGGER.warn("No devices loaded. Call getConnectedDevices() first.");
            return Optional.empty();
        }

        return instance.stream()
                .filter(device -> !device.isBusy())
                .map(Device::getUdid)
                .findFirst();
    }

    /**
     * Get the first available (not busy) device
     * 
     * @return Optional containing a free Device, empty if no free device found
     */
    public synchronized static Optional<Device> getFreeDevice() {
        if (instance == null) {
            LOGGER.warn("No devices loaded. Call getConnectedDevices() first.");
            return Optional.empty();
        }

        return instance.stream()
                .filter(device -> !device.isBusy())
                .findFirst();
    }

    /**
     * Get all available (not busy) device UDIDs
     * 
     * @return List of UDIDs of all free devices (empty list if none available)
     */
    public static List<String> getAllFreeDeviceUdids() {
        if (instance == null) {
            LOGGER.warn("No devices loaded. Returning empty list.");
            return Arrays.asList();
        }

        return instance.stream()
                .filter(device -> !device.isBusy())
                .map(Device::getUdid)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get all available (not busy) devices
     * 
     * @return List of all free devices (empty list if none available)
     */
    public static List<Device> getAllFreeDevices() {
        if (instance == null) {
            LOGGER.warn("No devices loaded. Returning empty list.");
            return Arrays.asList();
        }

        return instance.stream()
                .filter(device -> !device.isBusy())
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get count of available (not busy) devices
     * 
     * @return Number of free devices
     */
    public static long getFreeDeviceCount() {
        if (instance == null) {
            return 0;
        }

        return instance.stream()
                .filter(device -> !device.isBusy())
                .count();
    }

    /**
     * Get count of busy devices
     * 
     * @return Number of busy devices
     */
    public static long getBusyDeviceCount() {
        if (instance == null) {
            return 0;
        }

        return instance.stream()
                .filter(Device::isBusy)
                .count();
    }
}
