# TIBCO BusinessWorks 6 Prometheus Metrics Exporter

<img src="https://macronova.io/wp-content/themes/hnovawp/blog/bwce-grafana-dashboard.png" height="200" width="494" alt="Grafana Dashboard" align="center">

Project allows to expose common performance metrics of BW6 processes and JVM. Statistics can be further pulled by Prometheus from HTTP endpoint. Applied naming convention:
* JVM metrics follow default Prometheus implementation from `io.prometheus:simpleclient_hotspot:0.5.0` JAR.
* BW6 process latency histogram: `process__${process-name}__latency_seconds`.
* BW6 activity duration histogram: `activity__${process-name}__${activity-name}__latency_seconds`.

## Build Project

1. Clone Git repository and import project into TIBCO BusinessStudio.
2. Export the project as a plug-in by right clicking on its name and selecting _Export_ > _Export..._ > _Plug-in Development_ > _Deployable plug-ins and fragments_.
3. In the _Export_ wizard, ensure that Event Bus subscriber project has been selected, and specify a location to export binaries.
4. Click _Finish_.

## Configuration

Plug-in enables users to limit exposed metrics to given set of applications, processes and activities. Below configuration file presents all available options.

```json
{
  "httpPort": "1234",
  "includeApplications": [
    "samples.bwce.prometheus.application"
  ],
  "includeProcesses": [
    "samples.bwce.prometheus.*"
  ],
  "includeActivities": [
    "samples.bwce.prometheus.*#JDBC.*",
    "samples.bwce.prometheus.*#HDFS.*"
  ],
  "processHistogramBuckets": [
    0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10
  ],
  "activityHistogramBuckets": [
    0.002, 0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1
  ],
  "processHistogramOverrides": [
    { "name": "samples.bwce.prometheus.batch.*", "buckets": [ 0.5, 1, 2, 5, 10, 60, 300, 600 ] },
    { "name": "samples.bwce.prometheus.report.*", "buckets": [ 1, 2, 5, 10, 60, 300, 600 ] }
  ],
  "activityHistogramOverrides": [
    { "name": "samples.bwce.prometheus.batch.*#JDBC.*", "buckets": [ 0.1, 0.25, 0.5, 1, 2, 5, 10 ] },
    { "name": "samples.bwce.prometheus.batch.*#HDFS.*", "buckets": [ 0.5, 1, 2, 5, 10 ] }
  ],
}
```

| Property Name                | Default     | Description                                                                                                                                                                                                                  |
|------------------------------|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| _httpPort_                   | 1234        | HTTP server listen port number.                                                                                                                                                                                              |
| _includeApplications_        | All         | Array of regular expressions matching applications which metrics should be published.                                                                                                                                        |
| _includeProcesses_           | All         | Array of regular expressions matching BW processes which metrics should be published.                                                                                                                                        |
| _includeActivities_          | None        | Array of regular expressions matching BW activities which metrics should be published. Name of every activity is represented as: _${process-name}#${activity-name}_. By default activity-level metrics are disabled.         |
| _processHistogramBuckets_    | 0.005 .. 10 | List of histogram buckets for BW6 processes. Default value: 0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10. Consult [Prometheus documentation](https://prometheus.io/docs/practices/histograms).  |
| _activityHistogramBuckets_   | 0.005 .. 10 | List of histogram buckets for BW6 activities. Default value: 0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10. Consult [Prometheus documentation](https://prometheus.io/docs/practices/histograms). |
| _processHistogramOverrides_  | None        | Provides ability to override histogram buckets for processes matching given regular expression.                                                                                                                              |
| _activityHistogramOverrides_ | None        | Provides ability to override histogram buckets for activities matching given regular expression.                                                                                                                             |

Users need to configure JVM system property _prometheus-metrics-config_ which defines fully qualified path to described JSON configuration manifest.

You may decide to accept default values (exposing metrics of all processes, but none activities, for all applications on HTTP port 1234) and skip any configuration activities.

## Installation on BW6

1. Clone and [build project from source](#build-project) as described in above section or copy one of the releases [available on GitHub](https://github.com/macronova/io.macronova.tibco.bw6.prometheus.event.subscriber/releases).
2. Copy JAR to `${BW_HOME}/system/hotfix/shared`.
3. Create configuration manifest as described in [Configuration](#configuration) paragraph (or skip to accept default values).

  * Update AppSpace or AppNode [configuration](https://docs.tibco.com/pub/activematrix_businessworks/6.5.0/doc/html/GUID-D2B78EC9-5123-4E3B-9F49-DB40FD3C8F55.html) to include JVM system property _prometheus-metrics-config_ referencing appropriate file.
    ```
    java.extended.properties="-Dprometheus-metrics-config=/opt/tibco/bw650/config/prometheus-reporter.json"
    ```
  * If you deploy multiple AppNodes on the same machine, separate configuration for every AppNode is required to prevent HTTP port collision.

4. For changes to take effect, restart modified AppNodes or AppSpaces.

## Installation on BWCE

1. Create [customized base Docker image](https://github.com/TIBCOSoftware/bwce-docker) that includes Prometheus Metrics Exporter plug-in.

  * Clone and [build project from source](#build-project) as described in above section or copy one of the releases [available on GitHub](https://github.com/macronova/io.macronova.tibco.bw6.prometheus.event.subscriber/releases) to new folder.
  * Create _Dockerfile_ with below content.
    ```
    FROM tibco/bwce:latest
    COPY *.jar /resources/addons/jars/
    ```
  * Build base BWCE-Prometheus image.
    ```bash
    $ docker build -t tibco/bwce-prometheus:latest .
    ```

2. Edit _Dockerfile_ of your BWCE project.

  * Update base Docker image to _tibco/bwce-prometheus:latest_.
  * Add `EXPOSE` directive to expose Prometheus HTTP endpoint.
    ```
    FROM tibco/bwce-prometheus:latest
    EXPOSE 1234
    ```

3. If you wish to change default plug-in configuration, create file `config/prometheus-reporter.json` inside your BWCE project. Edit _Dockerfile_ to copy mentioned file inside Docker image and add required JVM system property.
  ```
  ENV BW_JAVA_OPTS="-Dprometheus-metrics-config=/resources/addons/config/prometheus-reporter.json ${BW_JAVA_OPTS}"
  COPY config/* /resources/addons/config/
  ```

## Tutorial

Looking for quick start? Read our five minute [tutorial](https://macronova.io/monitoring-bwce-with-prometheus).
