package com.appium.manager;

import com.appium.filelocations.FileLocations;
import com.appium.capabilities.Capabilities;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.time.Duration;

import static com.appium.utils.OverriddenVariable.getOverriddenStringValue;

public class AppiumServerManager {

    private static AppiumDriverLocalService appiumDriverLocalService;

    private static AppiumDriverLocalService getAppiumDriverLocalService() {
        return appiumDriverLocalService;
    }

    private static final Logger LOGGER = Logger.getLogger(AppiumServerManager.class.getName());

    private static void setAppiumDriverLocalService(
            AppiumDriverLocalService appiumDriverLocalService) {
        AppiumServerManager.appiumDriverLocalService = appiumDriverLocalService;
    }

    private URL getAppiumUrl() {
        String deviceToExecute = getOverriddenStringValue("DEVICE_TO_EXECUTE", "local");

        if ("devicefarm".equalsIgnoreCase(deviceToExecute)) {
            try {
                // Get hub URL from capabilities when using device farm
                JSONObject serverConfig = Capabilities.getInstance()
                        .getCapabilityObjectFromKey("serverConfig");
                String hubUrl = serverConfig
                        .getJSONObject("server")
                        .getJSONObject("plugin")
                        .getJSONObject("device-farm")
                        .getString("hub");
                LOGGER.info("Using Device Farm hub URL: " + hubUrl);
                return new URL(hubUrl);
            } catch (MalformedURLException e) {
                LOGGER.error("Invalid hub URL in device-farm configuration", e);
                throw new RuntimeException("Failed to get device farm hub URL", e);
            }
        }

        // Default: return local Appium server URL
        return getAppiumDriverLocalService().getUrl();
    }

    public void destroyAppiumNode() {
        String deviceToExecute = getOverriddenStringValue("DEVICE_TO_EXECUTE", "local");

        // Only destroy local Appium server, not remote device farm hub
        if (!"devicefarm".equalsIgnoreCase(deviceToExecute)) {
            LOGGER.info("Shutting down Appium Server");
            getAppiumDriverLocalService().stop();
            if (getAppiumDriverLocalService().isRunning()) {
                LOGGER.info("AppiumServer didn't shut... Trying to quit again....");
                getAppiumDriverLocalService().stop();
            }
        } else {
            LOGGER.info("Using Device Farm - skipping local Appium server shutdown");
        }
    }

    public String getRemoteWDHubIP() {
        return getAppiumUrl().toString();
    }

    public void startAppiumServer(String host) throws Exception {
        LOGGER.info(LOGGER.getName() + "Starting Appium Server on Localhost");
        new File(
                System.getProperty("user.dir")
                        + FileLocations.APPIUM_LOGS_DIRECTORY
                        + "appium_logs.txt").getParentFile().mkdirs();
        AppiumDriverLocalService appiumDriverLocalService;
        AppiumServiceBuilder builder =
                getAppiumServerBuilder(host)
                        .withLogFile(new File(
                                System.getProperty("user.dir")
                                        + FileLocations.APPIUM_LOGS_DIRECTORY
                                        + "appium_logs.txt"))
                        .withIPAddress(host)
                        .withTimeout(Duration.ofSeconds(60))
                        .withArgument(() -> "--config", System.getProperty("user.dir")
                                + FileLocations.SERVER_CONFIG)
                        .withArgument(GeneralServerFlag.RELAXED_SECURITY)
                        .usingAnyFreePort();
        if (Capabilities.getInstance().getCapabilities().has("basePath")) {
            if (!StringUtils.isBlank(getBasePath())) {
                builder.withArgument(GeneralServerFlag.BASEPATH,getBasePath());
            }
        } else {
            builder.withArgument(GeneralServerFlag.BASEPATH,"/wd/hub");
        }
        appiumDriverLocalService = builder.build();
        appiumDriverLocalService.start();
        LOGGER.info(LOGGER.getName() + "Appium Server Started at......"
                + appiumDriverLocalService.getUrl());
        setAppiumDriverLocalService(appiumDriverLocalService);
    }

    /*private void getWindowsDevice(String platform, List<Device> devices) {
        if (platform.equalsIgnoreCase(OSType.WINDOWS.name())
                && Capabilities.getInstance().isWindowsApp()) {
            Device device = new Device();
            device.setName("windows");
            device.setOs("windows");
            device.setName("windows");
            device.setUdid("win-123");
            device.setDevice(true);
            List<Device> deviceList = new ArrayList<>();
            deviceList.add(device);
            devices.addAll(deviceList);
        }
    }*/

    public int getAvailablePort(String hostMachine) throws IOException {
        ServerSocket socket = new ServerSocket(0);
        socket.setReuseAddress(true);
        int port = socket.getLocalPort();
        socket.close();
        return port;
    }

    private AppiumServiceBuilder getAppiumServerBuilder(String host) throws Exception {
        if (Capabilities.getInstance().getCapabilities().has("appiumServerPath")) {
            Path path = FileSystems.getDefault().getPath(Capabilities.getInstance()
                    .getCapabilities().get("appiumServerPath").toString());
            String serverPath = path.normalize().toAbsolutePath().toString();
            LOGGER.info("Picking UserSpecified Path for AppiumServiceBuilder");
            return getAppiumServiceBuilderWithUserAppiumPath(serverPath);
        } else {
            LOGGER.info("Picking Default Path for AppiumServiceBuilder");
            return getAppiumServiceBuilderWithDefaultPath();

        }
    }

    private AppiumServiceBuilder
    getAppiumServiceBuilderWithUserAppiumPath(String appiumServerPath) {
        return new AppiumServiceBuilder().withAppiumJS(
                new File(appiumServerPath));
    }

    private AppiumServiceBuilder getAppiumServiceBuilderWithDefaultPath() {
        return new AppiumServiceBuilder();
    }

    private String getBasePath() {
        LOGGER.info("Picking UserSpecified Base Path");
        return Capabilities.getInstance()
                .getCapabilities().get("basePath").toString();
    }

}
