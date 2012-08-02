The UncertWeb Processing Service is a generic framework for exposing processes on the web. Processes are exposed using two interfaces: SOAP/WSDL and JSON.


## Getting started

If you like to do things the easy way, clone the example project at https://github.com/itszootime/ps-example. This is a pre-configured Maven project complete with sample processes, all ready to package and deploy.

Alternatively, creating your own Maven webapp project is the next easiest way to use the framework. The UncertWeb Maven repository, hosted at the University of Münster, is required to resolve the necessary dependencies. Adding the following snippet to your `pom.xml` file will include the repository in your project.

```xml
<repositories>
  <!-- Other repositories may be here too -->
  <repository>
    <id>UncertWebMavenRepository</id>
    <name>UncertWeb Maven Repository</name>
    <url>http://giv-uw.uni-muenster.de/m2/repo</url>
  </repository>
</repositories>
```

The framework dependency can then be added too.

```xml
<dependencies>
  <!-- Other dependencies may be here too -->
  <dependency>
      <groupId>org.uncertweb</groupId>
      <artifactId>ps-framework</artifactId>
      <version>0.2.3-SNAPSHOT</version>
  </dependency>
</dependencies>
```

The last thing you'll need to do is add the framework servlet classes and mappings to the webapp configuration file, `src/main/webapp/WEB-INF/web.xml`.

```xml
<web-app>
  <!-- Other servlets/mappings/etc may be here too -->
  <servlet>
    <servlet-name>Service</servlet-name>
    <servlet-class>org.uncertweb.ps.ServiceServlet</servlet-class>
  </servlet>
  <servlet>
    <servlet-name>Data</servlet>
    <servlet-class>org.uncertweb.ps.DataServlet
  </servlet>
  <servlet-mapping>
    <servlet-name>Service</servlet-name>
    <url-pattern>/service/*</url-pattern>
  </servlet-mapping>
  <servlet-mapping>
    <servlet-name>Data</servlet-name>
    <url-pattern>/data</url-pattern>
  </servlet-mapping>
</web-app>
```

## Creating a process

A process is created by extending the `AbstractProcess` class. This class defines methods for returning input and output descriptions, metadata, and performing the actual process work.

Once your class has extended `AbstractProcess`, implement the `getInputIdentifiers` and `getOutputIdentifiers` methods. These methods should return a list of unique identifiers for the inputs and outputs of your process. The chosen identifiers will be used throughout the process class.

```java
@Override
public List<String> getInputIdentifiers() {
  return Arrays.asList(new String[] { "FirstInput", "SecondInput" });
}
```

The types of these inputs and outputs are defined in the `getInputDataDescription` and `getOutputDataDescription` methods. These methods should each return a `DataDescription` object for a given input or output identifier.

At a minimum, the `DataDescription` object is constructed with one parameter: the class the input or output will be an instance of. This is used by the framework to determine which encoding class to use when parsing and generating data. The `DataDescription` object can also have a minimum and maximum number of occurrences, and a raw flag to tell the framework to bypass encoding classes when handling referenced data. This is most useful when processing large data (such as raster coverages), when you may not want to read the whole file into memory.

```java
@Override
public DataDescription getInputDataDescription(String identifier) {
  if (identifier.equals("FirstInput")) {
    // A is a double, minimum and maximum occurrences is 1 (default)
    return new DataDescription(Double.class);
  } else if (identifier.equals("SecondInput")) {
    // B is a double, minimum occurences is 1 and maximum is unbounded
    return new DataDescription(Double.class, 1, Integer.MAX_VALUE);
  }
}
```

Processing work is performed in the run method. When a process request is received, this method is called and passed a `ProcessInputs` instance containing the parsed inputs. Individual inputs can be retrieved using their identifier, after which they can be cast to the relevant class.

```java
// Get first input (single as maximum occurrences is one)
SingleInput firstInput = inputs.get("FirstInput").getAsSingleInput();
Double first = firstInput.getObjectAs(Double.class);

// Get second input (multiple as maximum occurrences is greater than one)
MultipleInput secondInput = inputs.get("SecondInput").getAsMultipleInput();
List<Double> seconds = secondInput.getObjectsAs(Double.class);
```

The method returns an instance of `ProcessOutputs`, which contains the output objects.

```java
ProcessOutputs outputs = new ProcessOutputs();
outputs.add(new SingleOutput("Result", 0.5)); // fixed result for example
return outputs;
```

Once complete, add the fully qualified name of your process class to the framework configuration file, `src/main/resources/config.json`.

```json
{ 'encodingClasses': [],
  'gsonTypeAdapterClasses': [],
  'processClasses': [ 'com.example.YourProcessClass' ],
  'additionalProperties': [] }
```

Your project is now ready to be built and packaged into a web application archive (WAR) file.

```console
mvn clean package
```

The resulting WAR file can be deployed using Tomcat.


## Using the service

### SOAP/WSDL

The framework automatically generates a WSDL document and associated schema. These can are accessible through `/service?wsdl` and `/service?schema` respectively. The WSDL document can be used with client code generation tools such as [Apache Axis](http://axis.apache.org/axis2/java/core/) ([guide](http://axis.apache.org/axis2/java/core/docs/quickstartguide.html#clients)) and [Microsoft Visual Studio](http://www.microsoft.com/visualstudio/en-us) ([guide](http://www.techrepublic.com/article/easily-create-web-services-clients-with-visual-studio-net/1050426)), or workflow software such as [Taverna](http://www.taverna.org.uk/).

If you wish to construct requests yourself, the child element of the SOAP Body should take the following form:

```xml
<ps:ProcessIdentifierRequest xmlns:ps="http://www.uncertweb.org/ProcessingService">
  <ps:InputIdentifierA>
    <!-- inline data here -->
  </ps:InputIdentifierA>
  <ps:InputIdentifierB>
    <ps:DataReference href="http://some.url/somedata.xml" mimeType="text/xml" />
  </ps:InputIdentifierB>
</ps:ProcessIdentifierRequest>
```

The child element of the SOAP Body in the response will take the following form:

```xml
<ps:ProcessIdentifierResponse xmlns:ps="http://www.uncertweb.org/ProcessingSerivce">
  <ps:OuputIdentifierA>
    <!-- inline or data reference here -->
  </ps:OutputIdentifierA>
</ps:ProcessIdentifierResponse>
```

If any errors are encountered during request processing, the child element of the SOAP Body in the response will be a SOAP Fault.

All SOAP requests should be sent using HTTP POST to `/service/soap`.

### JSON

The framework automatically generates a basic service description which accessible through `/service?jsondesc`. This description can help to build generic execution clients.

Request objects should take the following form:

```json
{ "ProcessIdentifierRequest": {
    "InputIdentifierA": 0.523, // data could be a value, array, object
    "InputIdentifierB": {
      "DataReference": { "href": "http://some.url/somedata.xml", "mimeType": "text/xml" }
    }
} }
```

Response objects will take the following form:

```json
{ "ProcessIdentifierResponse": {
    "OutputIdentifierA": 12.094
} }
```

If any errors are encountered during request processing, an exception object is returned.

```json
{ "ServiceException": {
    "message": "something bad happened",
    "detail": "here's more detail on why it happened"
} }
```

All JSON requests should be sent with HTTP POST to `/service/json`.


## Supported data types

To allow the user to focus on the functionality of the process, encoding is automatically selected depending on the class of the input or output. This automatic selection can be controlled by implementing custom encoding classes. The framework has built-in support for GeoJSON, UncertML and the UncertWeb profiles of GML and O&M.

### Geometry

The following geometry classes in the [JTS Topology Suite](http://www.vividsolutions.com/jts/jtshome.html) will be encoded as GML (UncertWeb profile) or GeoJSON:

* Point
* LineString
* Polygon
* MultiPoint
* MultiPolygon
* MultiLineString

Plus from the UncertWeb GML profile API:

* RectifiedGrid

### Observations

All classes in the UncertWeb O&M profile API are supported for both XML and JSON encoding.

### Uncertainty

All classes in the UncertML API version 2.0 are supported for both XML and JSON encoding.

### Primitives

The following primitive wrapper classes are supported for both XML and JSON encoding:

* String
* Double

It is possible to use primitive wrapper classes in arrays when creating a `DataDescription` for an input or output:

```java
new DataDescription(Double[].class);
```

This has slightly different semantics to data with maximum occurrences set to a value greater than 1. For example, in XML, the list element will be used rather than multiple named elements.

```xml
<!-- new DataDescription(Double[].class); -->
<ps:SomeProcessRequest xmlns:ps="http://www.uncertweb.org/ProcessingService">
  <ps:SomeInput>1 2 3</ps:SomeInput>
</ps:SomeProcessRequest>

<!-- new DataDescription(Double.class, 1, Integer.MAX_VALUE) -->
<ps:SomeProcessRequest xmlns:ps="http://www.uncertweb.org/ProcessingService">
  <ps:SomeInput>1</ps:SomeInput>
  <ps:SomeInput>2</ps:SomeInput>
  <ps:SomeInput>3</ps:SomeInput>
</ps:SomeProcessRequest>
```


## Custom encoding classes

Binary encoding classes will always return as reference.


## Adding metadata to process descriptions

To help describe your processes, metadata can be added. The `AbstractProcess` class defines three methods to support this: `getMetadata`, `getInputMetadata`, and `getOutputMetadata`. The former method should return metadata for the process itself, and the latter two should return metadata for each of the inputs and outputs.

Metadata is returned as a list of `Metadata` objects. The `Metadata` object is essentially a key-value pair. The key can be any string, but here are some examples you may wish to use:

* description
* variable-units-of-measure
* spatial-crss
* spatial-resolutions

An example `getInputMetadata` implementation may look like so:

```java
@Override
public List<Metadata> getInputMetadata(String identifier) {
  List<Metadata> metadata = new ArrayList<Metadata>();
  if (identifier.equals("FirstInput")) {
    metadata.add(new Metadata("description", "A length of something"));
    metadata.add(new Metadata("variable-units-of-measure", "m"));
  }
  return metadata;
}
```

When returned by a process class, the metadata is included in the generated XML schema as annotation elements. Metadata inclusion in the JSON service description is a planned feature.