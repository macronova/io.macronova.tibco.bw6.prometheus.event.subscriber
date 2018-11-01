/*
 * Copyright 2018 Macronova.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.macronova.tibco.bw6.prometheus.event.subscriber;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import com.tibco.bw.runtime.event.ActivityAuditEvent;
import com.tibco.bw.runtime.event.CoreAuditEvent;
import com.tibco.bw.runtime.event.ProcessAuditEvent;

/**
 * Example configuration in JSON format:
 * <pre>
 * {
 *   "httpPort": "1234",
 *   "includeApplications": [
 *       "samples.bwce.prometheus.application"
 *    ],
 *   "includeProcesses": [
 *       "samples.bwce.prometheus.*"
 *    ],
 *    "includeActivities": [
 *       "samples.bwce.prometheus.*#JDBC.*",
 *       "samples.bwce.prometheus.*#HDFS.*"
 *    ],
 *   "processHistogramBuckets": [
 *     0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10
 *   ],
 *   "activityHistogramBuckets": [
 *     0.002, 0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1
 *   ],
 *   "processHistogramOverrides": [
 *      { "name": "samples.bwce.prometheus.batch.*", "buckets": [ 0.5, 1, 2, 5, 10, 60, 300, 600 ] },
 *      { "name": "samples.bwce.prometheus.report.*", "buckets": [ 1, 2, 5, 10, 60, 300, 600 ] }
 *   ],
 *   "activityHistogramOverrides": [
 *      { "name": "samples.bwce.prometheus.batch.*#JDBC.*", "buckets": [ 0.1, 0.25, 0.5, 1, 2, 5, 10 ] },
 *      { "name": "samples.bwce.prometheus.batch.*#HDFS.*", "buckets": [ 0.5, 1, 2, 5, 10 ] }
 *   ],
 * }
 * </pre>
 */
public class Configuration {
	private static final int DEFAULT_HTTP_PORT = 1234;
	private static final double[] DEFAULT_HISTOGRAM_BUCKETS = new double[] { .005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10 };
	
	private int httpPort = DEFAULT_HTTP_PORT;
	private Pattern[] includeApplications = null;
	private Pattern[] includeProcesses = null;
	private Pattern[] includeActivities = null; // Format of activity name: ${process-name}#${activity-name}.
	private double[] processHistogramBuckets = DEFAULT_HISTOGRAM_BUCKETS;
	private double[] activityHistogramBuckets = DEFAULT_HISTOGRAM_BUCKETS;
	private Map<Pattern, double[]> processHistogramOverrides = new HashMap<>();
	private Map<Pattern, double[]> activityHistogramOverrides = new HashMap<>();
	
	public Configuration(String configPath) throws Exception {
		if ( configPath != null ) {
			final JSONObject json = new JSONObject( new String ( Files.readAllBytes( Paths.get( configPath ) ) ) );
			httpPort = json.optInt( "httpPort", httpPort );
			includeApplications = toPattern( JsonUtils.jsonStringArray( json.optJSONArray( "includeApplications" ), null ) );
			includeProcesses = toPattern( JsonUtils.jsonStringArray( json.optJSONArray( "includeProcesses" ), null ) );
			includeActivities = toPattern( JsonUtils.jsonStringArray( json.optJSONArray( "includeActivities" ), null ) );
			processHistogramBuckets = JsonUtils.jsonDoubleArray( json.optJSONArray( "processHistogramBuckets" ), processHistogramBuckets );
			activityHistogramBuckets = JsonUtils.jsonDoubleArray( json.optJSONArray( "activityHistogramBuckets" ), processHistogramBuckets );
			fillOverridesMap( json.optJSONArray( "processHistogramOverrides" ), processHistogramOverrides );
			fillOverridesMap( json.optJSONArray( "activityHistogramOverrides" ), activityHistogramOverrides );
		}
	}
	
	private Pattern[] toPattern(String[] regexp) {
		if ( regexp == null ) {
			return null;
		}
		final Pattern[] result = new Pattern[ regexp.length ];
		for ( int i = 0; i < regexp.length; ++i ) {
			result[i] = Pattern.compile( regexp[i] );
		}
		return result;
	}
	
	private void fillOverridesMap(JSONArray array, Map<Pattern, double[]> map) {
		if ( array != null ) {
			for ( int i = 0; i < array.length(); ++i ) {
				final JSONObject element = array.getJSONObject( i );
				map.put(
						Pattern.compile( element.getString( "name" ) ),
						JsonUtils.jsonDoubleArray( element.optJSONArray( "buckets" ) )
				);
			}
		}
	}
	
	public boolean enableStatistics(CoreAuditEvent event) {
		if ( includeApplications != null ) {
			for ( Pattern pattern : includeApplications ) {
				if ( pattern.matcher( event.getApplicationName() ).matches() ) {
					return true;
				}
			}
			return false;
		}
		return true;
	}
	
	public boolean enableStatistics(ProcessAuditEvent event) {
		if ( includeProcesses != null ) {
			for ( Pattern pattern : includeProcesses ) {
				if ( pattern.matcher( event.getProcessName() ).matches() ) {
					return true;
				}
			}
			return false;
		}
		return true;
	}
	
	public boolean enableStatistics(ActivityAuditEvent event) {
		if ( includeActivities != null ) {
			final String name = event.getProcessName() + "#" + event.getActivityName();
			for ( Pattern pattern : includeActivities ) {
				if ( pattern.matcher( name ).matches() ) {
					return true;
				}
			}
			return false;
		}
		return false;
	}
	
	public int getHttpPort() {
		return httpPort;
	}
	
	public double[] getProcessHistogramBuckets(String process) {
		for ( Map.Entry<Pattern, double[]> override : processHistogramOverrides.entrySet() ) {
			if ( override.getKey().matcher( process ).matches() ) {
				return override.getValue();
			}
		}
		return processHistogramBuckets;
	}
	
	public double[] getActivityHistogramBuckets(String process, String activity) {
		final String signature = process + "#" + activity;
		for ( Map.Entry<Pattern, double[]> override : activityHistogramOverrides.entrySet() ) {
			if ( override.getKey().matcher( signature ).matches() ) {
				return override.getValue();
			}
		}
		return activityHistogramBuckets;
	}
}
