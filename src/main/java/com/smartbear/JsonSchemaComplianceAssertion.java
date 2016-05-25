package com.smartbear;

import com.eviware.soapui.config.TestAssertionConfig;
import com.eviware.soapui.impl.wsdl.panels.assertions.AssertionCategoryMapping;
import com.eviware.soapui.impl.wsdl.submit.HttpMessageExchange;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlMessageAssertion;
import com.eviware.soapui.model.TestPropertyHolder;
import com.eviware.soapui.model.iface.MessageExchange;
import com.eviware.soapui.model.iface.SubmitContext;
import com.eviware.soapui.model.testsuite.Assertable;
import com.eviware.soapui.model.testsuite.AssertionError;
import com.eviware.soapui.model.testsuite.AssertionException;
import com.eviware.soapui.model.testsuite.ResponseAssertion;
import com.eviware.soapui.plugins.auto.PluginTestAssertion;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.types.StringToStringMap;
import com.eviware.soapui.support.xml.XmlObjectConfigurationBuilder;
import com.eviware.soapui.support.xml.XmlObjectConfigurationReader;
import com.eviware.x.form.XForm;
import com.eviware.x.form.XFormDialog;
import com.eviware.x.form.XFormDialogBuilder;
import com.eviware.x.form.XFormFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import io.swagger.util.Json;
import org.apache.xmlbeans.XmlObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

@PluginTestAssertion(id = "JsonSchemaComplianceAssertion", label = "JSON Schema Compliance Assertion",
    category = AssertionCategoryMapping.VALIDATE_RESPONSE_CONTENT_CATEGORY,
    description = "Asserts that the response message is compliant with a JSON Schema definition")
public class JsonSchemaComplianceAssertion extends WsdlMessageAssertion implements ResponseAssertion {
    private static final String SCHEMA_URL = "schemaUrl";
    private static final String SCHEMA_URL_FIELD = "Schema URL";
    private String schemaUrl;
    private JsonSchema jsonSchema;
    private XFormDialog dialog;

    /**
     * Assertions need to have a constructor that takes a TestAssertionConfig and the ModelItem to be asserted
     */

    public JsonSchemaComplianceAssertion(TestAssertionConfig assertionConfig, Assertable modelItem) {
        super(assertionConfig, modelItem, true, false, false, true);

        XmlObjectConfigurationReader reader = new XmlObjectConfigurationReader(getConfiguration());
        schemaUrl = reader.readString(SCHEMA_URL, null);
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    public boolean configure() {
        if (dialog == null) {
            buildDialog();
        }

        StringToStringMap values = new StringToStringMap();
        values.put(SCHEMA_URL_FIELD, schemaUrl);

        values = dialog.show(values);
        if (dialog.getReturnValue() == XFormDialog.OK_OPTION) {
            setSchemaUrl(values.get(SCHEMA_URL_FIELD));
        }

        setConfiguration(createConfiguration());
        return true;
    }

    private void buildDialog() {
        XFormDialogBuilder builder = XFormFactory.createDialogBuilder("JSON Schema Compliance Assertion");
        XForm mainForm = builder.createForm("Basic");

        mainForm.addTextField(SCHEMA_URL_FIELD, "URL for JSON Schema Definition", XForm.FieldType.URL).setWidth(40);

        dialog = builder.buildDialog(builder.buildOkCancelActions(),
            "Specify JSON Schema URL", UISupport.OPTIONS_ICON);
    }

    public void setSchemaUrl(String endpoint) {
        schemaUrl = endpoint;
        jsonSchema = null;
    }

    protected XmlObject createConfiguration() {
        XmlObjectConfigurationBuilder builder = new XmlObjectConfigurationBuilder();
        return builder.add(SCHEMA_URL, schemaUrl).finish();
    }

    @Override
    protected String internalAssertResponse(MessageExchange messageExchange, SubmitContext submitContext) throws AssertionException {

        try {
            if (schemaUrl != null && messageExchange instanceof HttpMessageExchange) {
                if (!messageExchange.hasResponse() || ((HttpMessageExchange) messageExchange).getResponseStatusCode() == 0) {
                    throw new AssertionException(new AssertionError("Missing response to validate"));
                }

                if (validateMessage((HttpMessageExchange) messageExchange, submitContext)) {
                    return "Response is compliant with JSON Schema";
                }
            }
        } catch (AssertionException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionException(new AssertionError("JSON Schema validation failed; [" + e.toString() + "]"));
        }

        return "Response is compliant with JSON Schema";
    }

    private boolean validateMessage(HttpMessageExchange messageExchange, SubmitContext submitContext) throws MalformedURLException, AssertionException {
        try {
            JsonSchema jsonSchema = getJsonSchema( submitContext);
            JsonNode contentObject = Json.mapper().readTree(messageExchange.getResponseContent());

            ValidationSupport.validateMessage(jsonSchema, contentObject);
        } catch (AssertionException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionException(new AssertionError("JSON Schema validation failed; [" + e.toString() + "]"));
        }

        return true;
    }

    private JsonSchema getJsonSchema(SubmitContext submitContext) throws IOException, ProcessingException {
        if (jsonSchema == null) {
            JsonNode schemaObject = Json.mapper().readTree(new URL(submitContext.expand(schemaUrl)));
            JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
            jsonSchema = factory.getJsonSchema(schemaObject);
        }

        return jsonSchema;
    }

    @Override
    protected String internalAssertProperty(TestPropertyHolder source, String propertyName, MessageExchange messageExchange, SubmitContext context) throws AssertionException {
        return null;
    }
}

