package com.smartbear;

import com.eviware.soapui.plugins.PluginAdapter;
import com.eviware.soapui.plugins.PluginConfiguration;

@PluginConfiguration(groupId = "com.smartbear.plugins", name = "Swagger Compliance Assertion", version = "1.0.0",
        autoDetect = true, description = "Asserts response messages against Swagger definitions",
        infoUrl = "" )
public class PluginConfig extends PluginAdapter {
}
