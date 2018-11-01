package io.macronova.tibco.bw6.prometheus.event.subscriber;

import org.json.JSONArray;

public abstract class JsonUtils {
	public static String[] jsonStringArray(JSONArray array, String[] defaultValue) {
		return ( array != null && ! array.isEmpty() ) ? array.toList().toArray( new String[0] ) : defaultValue;
	}
	
	public static double[] jsonDoubleArray(JSONArray array, double[] defaultValue) {
		if ( array == null || array.isEmpty() ) {
			return defaultValue;
		}
		return jsonDoubleArray( array );
	}
	
	public static double[] jsonDoubleArray(JSONArray array) {
		if ( array == null ) {
			return new double[0];
		}
		final double[] result = new double[ array.length() ];
		for ( int i = 0; i < result.length; ++i ) {
			result[i] = array.getDouble( i );
		}
		return result;
	}
}
