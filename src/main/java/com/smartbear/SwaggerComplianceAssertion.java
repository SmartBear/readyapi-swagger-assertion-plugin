package com.smartbear;

import com.eviware.soapui.config.TestAssertionConfig;
import com.eviware.soapui.impl.rest.RestRequestInterface;
import com.eviware.soapui.impl.wsdl.panels.assertions.AssertionCategoryMapping;
import com.eviware.soapui.impl.wsdl.submit.HttpMessageExchange;
import com.eviware.soapui.impl.wsdl.teststeps.HttpTestRequestInterface;
import com.eviware.soapui.impl.wsdl.teststeps.HttpTestRequestStepInterface;
import com.eviware.soapui.impl.wsdl.teststeps.RestRequestStepResult;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlMessageAssertion;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestStepResult;
import com.eviware.soapui.model.TestPropertyHolder;
import com.eviware.soapui.model.iface.MessageExchange;
import com.eviware.soapui.model.iface.SubmitContext;
import com.eviware.soapui.model.testsuite.Assertable;
import com.eviware.soapui.model.testsuite.AssertionError;
import com.eviware.soapui.model.testsuite.AssertionException;
import com.eviware.soapui.model.testsuite.ResponseAssertion;
import com.eviware.soapui.plugins.auto.PluginTestAssertion;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.xml.XmlObjectConfigurationBuilder;
import com.eviware.soapui.support.xml.XmlObjectConfigurationReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.load.Dereferencing;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfigurationBuilder;
import com.github.fge.jsonschema.core.load.download.URIDownloader;
import com.github.fge.jsonschema.core.load.uri.URITranslatorConfiguration;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.common.collect.Lists;
import io.swagger.models.HttpMethod;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import io.swagger.models.Swagger;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.parser.SwaggerParser;
import io.swagger.util.Json;
import org.apache.xmlbeans.XmlObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@PluginTestAssertion(id = "SwaggerComplianceAssertion", label = "Swagger Compliance Assertion",
    category = AssertionCategoryMapping.VALIDATE_RESPONSE_CONTENT_CATEGORY,
    description = "Asserts that the response message is compliant with a Swagger definition")
public class SwaggerComplianceAssertion extends WsdlMessageAssertion implements ResponseAssertion {
    private static final String SWAGGER_URL = "swaggerUrl";
    private String swaggerUrl;
    private Swagger swagger;
    private JsonSchema swaggerSchema;

    /**
     * Assertions need to have a constructor that takes a TestAssertionConfig and the ModelItem to be asserted
     */

    public SwaggerComplianceAssertion(TestAssertionConfig assertionConfig, Assertable modelItem) {
        super(assertionConfig, modelItem, true, false, false, true);

        XmlObjectConfigurationReader reader = new XmlObjectConfigurationReader(getConfiguration());
        swaggerUrl = reader.readString(SWAGGER_URL, null);
    }

    @Override
    public boolean configure() {

        String endpoint = UISupport.prompt("Specify endpoint to Swagger definition to use for validation", "Swagger Compliance", swaggerUrl);
        if( endpoint == null ){
            return false;
        }

        setSwaggerUrl(endpoint);

        return true;
    }

    public void setSwaggerUrl(String endpoint) {
        swaggerUrl = endpoint;
        swagger = null;
        swaggerSchema = null;

        setConfiguration( createConfiguration() );
    }

    protected XmlObject createConfiguration() {
        XmlObjectConfigurationBuilder builder = new XmlObjectConfigurationBuilder();
        return builder.add(SWAGGER_URL, swaggerUrl).finish();
    }

    @Override
    protected String internalAssertResponse(MessageExchange messageExchange, SubmitContext submitContext) throws AssertionException {

        try {

            if (swaggerUrl != null && messageExchange instanceof RestRequestStepResult ) {
                if (validateMessage((RestRequestStepResult) messageExchange, submitContext)) {
                    return "Response is compliant with Swagger Definition";
                }
            }
        } catch (AssertionException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionException(new AssertionError("Swagger Compliance Check failed; [" + e.toString() + "]"));
        }

        return "Response is compliant with Swagger definition";
    }

    private boolean validateMessage(RestRequestStepResult messageExchange, SubmitContext submitContext) throws MalformedURLException, AssertionException {
        Swagger swagger = getSwagger( submitContext );

        HttpTestRequestInterface<?> testRequest = ((HttpTestRequestInterface) messageExchange.getModelItem());
        RestRequestInterface.HttpMethod method = testRequest.getMethod();

        URL endpoint = new URL(messageExchange.getEndpoint());
        String path = endpoint.getPath();
        if( path != null ) {
            if (swagger.getBasePath() != null && path.startsWith(swagger.getBasePath())) {
                path = path.substring(swagger.getBasePath().length());
            }

            for (String swaggerPath : swagger.getPaths().keySet()) {

                if (matchesPath(path, swaggerPath)) {
                    HttpMethod methodName = HttpMethod.valueOf(method.name());
                    Operation operation = swagger.getPath(swaggerPath).getOperationMap().get(methodName);
                    if (operation != null) {
                        validateOperation( messageExchange,
                            messageExchange.getResponseContent(),
                            swagger, path, methodName, operation);

                        return true;
                    }
                    else {
                        throw new AssertionException(new AssertionError(
                            "Failed to find " + methodName + " method for path [" + path + "] in Swagger definition"));
                    }
                }
            }

            throw new AssertionException(new AssertionError( "Failed to find matching path for [" + path + "] in Swagger definition"));
        }

        return false;
    }

    private void validateOperation(RestRequestStepResult messageExchange, String contentAsString, Swagger swagger, String path, HttpMethod methodName, Operation operation) throws AssertionException {
        String responseCode = String.valueOf(messageExchange.getResponse().getStatusCode());

        Response responseSchema = operation.getResponses().get(responseCode);
        if (responseSchema == null) {
            responseSchema = operation.getResponses().get("default");
        }

        if (responseSchema != null ) {
            validateResponse(contentAsString, swagger, responseSchema);
        }
        else {
            throw new AssertionException(new AssertionError(
                "Missing response for a " + responseCode + " response from " + methodName + " " + path + " in Swagger definition"));
        }
    }

    private void validateResponse(String contentAsString, Swagger swagger, Response responseSchema) throws AssertionException {
        if( responseSchema.getSchema() != null) {
            Property schema = responseSchema.getSchema();
            if (schema instanceof RefProperty) {
                Model model = swagger.getDefinitions().get(((RefProperty) schema).getSimpleRef());
                if (model != null) {
                    validate(contentAsString, null );
                }
            } else {
                validate(contentAsString, Json.pretty(schema));
            }
        }
    }

    private boolean matchesPath(String path, String swaggerPath) {

        String[] pathSegments = path.split("\\/");
        String[] swaggerPathSegments = swaggerPath.split("\\/");

        if( pathSegments.length != swaggerPathSegments.length ){
            return false;
        }

        for( int c = 0; c < pathSegments.length; c++ ){
            String pathSegment = pathSegments[c];
            String swaggerPathSegment = swaggerPathSegments[c];

            if(swaggerPathSegment.startsWith("{") && swaggerPathSegment.endsWith("}")){
                continue;
            }
            else if( !swaggerPathSegment.equalsIgnoreCase(pathSegment) ){
                return false;
            }
        }

        return true;
    }

    private Swagger getSwagger(SubmitContext submitContext) throws AssertionException {
        if( swagger == null && swaggerUrl != null ) {
            SwaggerParser parser = new SwaggerParser();
            swagger = parser.read( submitContext.expand( swaggerUrl ));
            if( swagger == null ){
                throw new AssertionException(new AssertionError("Failed to load Swagger definition from [" + swaggerUrl + "]"));
            }
            swaggerSchema = null;
        }
        return swagger;
    }

    public void validate(String payload, String schema ) throws AssertionException {
        try {
            JsonSchema jsonSchema;

            if( schema != null ){
                JsonNode schemaObject = Json.mapper().readTree(schema);
                JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
                jsonSchema = factory.getJsonSchema(schemaObject);
            } else {
                jsonSchema = getSwaggerSchema();
            }

            JsonNode contentObject = Json.mapper().readTree(payload);

            ProcessingReport report = jsonSchema.validate(contentObject);
            if (!report.isSuccess()) {
                List<AssertionError> errors = Lists.newArrayList();
                for (ProcessingMessage message : report) {
                    if( message.getLogLevel() == LogLevel.ERROR || message.getLogLevel() == LogLevel.FATAL ) {
                        errors.add(new AssertionError(message.getMessage()));
                    }
                }

                if( !errors.isEmpty()) {
                    throw new AssertionException(errors.toArray(new AssertionError[errors.size()]));
                }
            }
        } catch (AssertionException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionException(new AssertionError("Swagger Compliance testing failed; [" + e.toString() + "]"));
        }
    }

    private JsonSchema getSwaggerSchema() throws IOException, ProcessingException {
        if( swaggerSchema == null ) {
            JsonNode schemaObject = Json.mapper().readTree(Json.pretty(swagger));
            JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
            swaggerSchema = factory.getJsonSchema(schemaObject);
        }

        return swaggerSchema;
    }

    @Override
    protected String internalAssertProperty(TestPropertyHolder source, String propertyName, MessageExchange messageExchange, SubmitContext context) throws AssertionException {
        return null;
    }
}

