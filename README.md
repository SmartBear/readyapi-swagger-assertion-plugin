## Ready! API Swagger and JSON Schema Compliance Assertions

Adds assertions for validating response messages against a specified Swagger definition or standalone JSON Schema

### Swagger Compliance Assertion
 
Validates that the corresponding path, method and response messages are defined in the Swagger definition - 
and that the response payload complies with the response model/schema (if defined). Will optionaly fail for 
response status codes not defined in the Swagger definition (i.e. if the API returns a 404 which hasn't been 
defined in the definition).

### JSON Schema Assertion

Validates the response payload against the specified JSON Schema

### Implementation

Uses version 2.2.6 of the [JSON Schema Validator](https://github.com/fge/json-schema-validator) library for 
schema validations - which currently supports most of version 3 and 4 drafts of JSON Schema [Read more](https://github.com/fge/json-schema-validator/wiki/Status)

### Build info

Build with 

```
mvn clean install assembly:single
```

This will create a *-dist.jar in the target folder - which you can install from the Ready! API Plugin Manager 