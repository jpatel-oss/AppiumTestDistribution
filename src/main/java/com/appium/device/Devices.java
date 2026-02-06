package com.appium.device;

import com.appium.capabilities.Capabilities;
import com.appium.manager.AppiumServerManager;
import com.appium.utils.Api;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.json.JSONObject;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

import static com.appium.utils.OverriddenVariable.getOverriddenStringValue;

public class Devices {
    private static List<Device> instance;

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
}
