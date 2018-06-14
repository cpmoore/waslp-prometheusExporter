# waslp-prometheusExporter
[Prometheus JMX Exporter](https://github.com/prometheus/jmx_exporter/blob/master/README.md) rewritten as a WebSphere Liberty feature

This project creates a Liberty feature that reads mbeans from the server and exposes them in Prometheus format.
If installed to a collective controller, the metrics from the collective members can also be exposed.

## Building the Liberty feature
Currently this repository exists as an eclipse project.  Eventually it will be converted to a maven or gradle project.

## Using the Liberty feature

The Liberty feature can be added to a Liberty profile installation using the `featureManager` command as follows:

```bash
wlp\bin\featureManager install prometheusExporter-1.0.0.esa
```

A server instance wishing to use the feature should add the `usr:prometheusExporter-1.0` feature to the `featureManager` stanza in `server.xml`.
The server must (at least) be using Java 8.

After installation, metrics will be available at https://{host}:{port}/prometheusExporter/{configured_path}

### Configuration

The feature also adds the ability to specify jmx exporter configuration as part of the Liberty `server.xml`.
This is achieved by adding a `prometheusExporter` stanza to the server.xml.
You may configure multiple stanza in order to provide different rules for different connections.


```xml
<featureManager>
   <feature>usr:prometheusExporter-1.0</feature>
</featureManager>
<prometheusExporter path="/" startDelaySeconds="0" lowercaseOutputName="true" lowercaseOutputLabelNames="true">
    <connection baseURL="https://localhost:9443" username="admin" password="encoded_password"  
                   sslProtocol="SSL_TLSv2" includeMemberMetrics="true" addIdentificationLabels="true"/>
    <whitelistObjectName>foo.bar:*</whitelistObjectName>
    <blacklistObjectName>foo.bar:*</whitelistObjectName>
    <defaultRule type="GAUGE">
        <label name="label1" value="value1"/>
        <label name="label2" value="value2"/>
    </defaultRule>
    <rule name="os_metric_$1" help="Some help text" valueFactor="1" 
          attrNameSnakeCase="true" pattern="java.lang{type=OperatingSystem}{}(.*):">
        <label name="label1" value=""/>
    </rule>
    <rule name="static" value="1" type="COUNTER"/>
</prometheusExporter>
```
Name     | Description
---------|------------
startDelaySeconds | Start delay before serving any metrics.  Defaults to `0`
path | Path to make metrics avaiable on.  For instance https://{host}:{port}/prometheusExporter/path1 or https://{host}:{port}/prometheusExporter/path2.  Defaults to `/`
lowercaseOutputName | Lowercase the output metric name. Defaults to `true`.
lowercaseOutputLabelNames | Lowercase the output metric label names. Defaults to `true`.
connection | Configuration for the MBean connection.
baseURL | Base URL of liberty server.  Full JMX URLs are also supported, but they haven't been throughly tested.  If not specified the URL will be looked up from the server mbeans.
username | The Liberty admin user to use in collective routing or username to authenticate to JMX connection ax.  This value is required to export collective member metrics.
password | Password for supplied user. XOR encoded values are supported.  This value is required to export collective member metrics.
sslProtocol | Protocol version to use when connecting. Possible values are dependent on which jre is running the servers. Defaults to the highest available on the jvm.
includeMemberMetrics | Export collective member metrics using the collective MBean routing ability. Defaults to `true`
addIdentificationLabels | Add lables to each metric to identify which connection it came from.  For a Liberty connection, these are `host`,`userdir`, and `server`. For a JMX connection, these are `jmxurl`. Defaults to `true`.
whitelistObjectName | A list of [ObjectNames](http://docs.oracle.com/javase/6/docs/api/javax/management/ObjectName.html) to query. Defaults to all mBeans.
blacklistObjectName | A list of [ObjectNames](http://docs.oracle.com/javase/6/docs/api/javax/management/ObjectName.html) to not query. Takes precedence over `whitelistObjectNames`. Defaults to none.
defaultRule | Default rule configuration.  Any labels specified here are added to all rules.  If you wish to unset one of the labels at an individual rule, set the value on that rule to `""`.
rule    | A list of rules to apply in order, processing stops at the first matching rule. The processing logic comes from [Prometheus JMX Exporter](https://github.com/prometheus/jmx_exporter/blob/master/README.md).
pattern  | Regex pattern to match against each bean attribute. The pattern is not anchored. Capture groups can be used in other options. Defaults to matching everything.  Please note that unlike the JMX Exporter, the pattern here uses `{` and `}` instead of `<` and `>` as these are illegal in XML configuration without encoding.
attrNameSnakeCase | Converts the attribute name to snake case. This is seen in the names matched by the pattern and the default format. For example, anAttrName to an\_attr\_name. Defaults to `true`.
name     | The metric name to set. Capture groups from the `pattern` can be used. If not specified, the default format will be used. If it evaluates to empty, processing of this attribute stops with no output.
value    | Value for the metric. Static values and capture groups from the `pattern` can be used. If not specified the scraped mBean value will be used.
valueFactor | Optional number that `value` (or the scraped mBean value if `value` is not specified) is multiplied by, mainly used to convert mBean values from milliseconds to seconds.
label   | A label to set for the metric.  Must have both a name and value attribute.  Can be used without specifying metric name.  Capture groups from `pattern` may be used.
help     | Help text for the metric. Capture groups from `pattern` can be used. `name` must be set to use this. Defaults to the mBean attribute decription and the full name of the attribute.
type     | The type of the metric, can be `GAUGE`, `COUNTER` or `UNTYPED`. `name` must be set to use this. Defaults to `UNTYPED`.

Updates to the `server.xml` are made available dynamically.
