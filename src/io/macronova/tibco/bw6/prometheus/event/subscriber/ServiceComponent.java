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

import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import com.tibco.bw.runtime.event.ActivityAuditEvent;
import com.tibco.bw.runtime.event.CoreAuditEvent;
import com.tibco.bw.runtime.event.ProcessAuditEvent;
import com.tibco.bw.runtime.event.State;
import com.tibco.bw.runtime.event.TransitionAuditEvent;

public class ServiceComponent implements EventHandler {
	private final ConcurrentMap<String, Histogram> histograms = new ConcurrentHashMap<String, Histogram>();
	private final ConcurrentMap<String, Long> processStartTimestamps = new ConcurrentHashMap<String, Long>();
	private final ConcurrentMap<String, Long> activityStartTimestamps = new ConcurrentHashMap<String, Long>();
	private Configuration configuration = null;
	private HTTPServer server = null;
	
	public void handleEvent(Event event) {
		final CoreAuditEvent auditEvent = (CoreAuditEvent) event.getProperty( "eventData" );
		
		if ( ! configuration.enableStatistics( auditEvent ) ) {
			return;
		}
		
		if ( auditEvent instanceof ProcessAuditEvent ) {
			final ProcessAuditEvent processEvent = (ProcessAuditEvent) auditEvent;
			if ( ! configuration.enableStatistics( processEvent ) ) {
				return;
			}
			if ( State.STARTED.equals( processEvent.getProcessInstanceState() ) ) {
				processStart( processEvent );
			}
			if ( State.COMPLETED.equals( processEvent.getProcessInstanceState() )
					|| State.FAULTED.equals( processEvent.getProcessInstanceState() ) ) {
				processEnd( processEvent );
			}
		}
		else if ( auditEvent instanceof ActivityAuditEvent ) {
			final ActivityAuditEvent activityEvent = (ActivityAuditEvent) auditEvent;
			if ( ! configuration.enableStatistics( activityEvent ) ) {
				return;
			}
			if ( State.STARTED.equals( activityEvent.getActivityState() ) ) {
				activityStart( activityEvent );
			}
			if ( State.COMPLETED.equals( activityEvent.getActivityState() )
					|| State.FAULTED.equals( activityEvent.getActivityState() ) ) {
				activityEnd( activityEvent );
			}
		}
		else if ( auditEvent instanceof TransitionAuditEvent ) {
		}
	}
	
	private String normalize(String name) {
		return name.replaceAll( "[^a-zA-Z0-9:_]", "_" );
	}
	
	private void processStart(final ProcessAuditEvent event) {
		histograms.computeIfAbsent( event.getProcessName(), new Function<String, Histogram>() {
				@Override
				public Histogram apply(String key) {
					return Histogram.build()
							.name( "process__" + normalize( event.getProcessName() ) + "__latency_seconds" )
							.buckets( configuration.getProcessHistogramBuckets( event.getProcessName() ) )
							.labelNames( "application", "success" )
							.help( "Latency of BW process execution." )
							.register();
				}
			}
		);
		processStartTimestamps.put( event.getProcessInstanceId(), event.getProcessInstanceStartTime() );
	}
	
	private void processEnd(ProcessAuditEvent event) {
		final Long duration = event.getProcessInstanceEndTime() - processStartTimestamps.remove( event.getProcessInstanceId() );
		histograms.get( event.getProcessName() ).labels(
				event.getApplicationName(),
				String.valueOf( State.COMPLETED.equals( event.getProcessInstanceState() ) )
		).observe( duration.doubleValue() / 1000.0d );
	}
	
	private void activityStart(final ActivityAuditEvent event) {
		histograms.computeIfAbsent( event.getProcessName() + "/" + event.getActivityName(), new Function<String, Histogram> () {
				@Override
				public Histogram apply(String arg0) {
					return Histogram.build()
							.name( "activity__" + normalize( event.getProcessName() ) + "__" + normalize( event.getActivityName() ) + "__latency_seconds" )
							.buckets( configuration.getActivityHistogramBuckets( event.getProcessName(), event.getActivityName() ) )
							.labelNames( "application", "success" )
							.help( "Latency of individual BW activities execution." )
							.register();
				}
			}
		);
		activityStartTimestamps.put( event.getActivityExecutionId(), event.getActivityStartTime() );
	}
	
	private void activityEnd(ActivityAuditEvent event) {
		final Long duration = event.getActivityEndTime() - activityStartTimestamps.remove( event.getActivityExecutionId() );
		histograms.get( event.getProcessName() + "/" + event.getActivityName() ).labels(
				event.getApplicationName(),
				String.valueOf( State.COMPLETED.equals( event.getActivityState() ) )
		).observe( duration.doubleValue() / 1000.0d );
	}
	
	@Activate
	public void activate(Map<String, String> properties) {
		try {
			configuration = new Configuration( System.getProperty( "prometheus-metrics-config" ) );
			server = new HTTPServer( configuration.getHttpPort() );
			DefaultExports.initialize(); // Register collectors for garbage collection, memory pools, JMX, class loading, and thread counts.
		}
		catch ( Exception e ) {
			throw new RuntimeException( "Failed to initialize Prometheus metrics reporter: " + e.getMessage() + ".",  e );
		}
	}
	
	@Deactivate
	public void deactivate(Map<String, String> properties) {
		if ( server != null ) {
			server.stop();
			server = null;
		}
		histograms.clear();
		processStartTimestamps.clear();
		activityStartTimestamps.clear();
	}
}
