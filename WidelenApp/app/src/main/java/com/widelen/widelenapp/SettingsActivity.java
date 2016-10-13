package com.widelen.widelenapp;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;

public class SettingsActivity extends Activity {
	public final static String MQTT_SERVER = "server";
	public final static String MQTT_USER = "user";
	public final static String MQTT_PWD = "password";
	public final static String MQTT_PORT = "port";
	public final static String MQTT_TLS = "tls";
	public final static String APP_CONNECT = "connectOnStart";
	public final static Boolean APP_CONNECT_DEFAULT = false;
	public final static String APP_GET_VALUES = "getValuesOnStart";
	public final static Boolean APP_GET_VALUES_DEFAULT = false;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_void);

		FragmentManager fragmentManager = getFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		SettingsFragment fragment = new SettingsFragment();
		fragmentTransaction.replace(android.R.id.content, fragment);
		fragmentTransaction.commit();
	}
}