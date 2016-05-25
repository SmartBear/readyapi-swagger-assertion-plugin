package com.smartbear;

import com.eviware.soapui.plugins.PluginAdapter;
import com.eviware.soapui.plugins.PluginConfiguration;

@PluginConfiguration(groupId = "com.smartbear.plugins", name = "Swagger and JSON Schema Assertions", version = "1.0.0",
    autoDetect = true, description = "Asserts response messages against JSON Schemas or Swagger 2.0 Definitions",
    infoUrl = "")
public class PluginConfig extends PluginAdapter {
}
