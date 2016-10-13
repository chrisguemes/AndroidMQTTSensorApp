package com.widelen.widelenapp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

public class CloudActivity extends AppCompatActivity {
    public static final String CTAG = "CloudActivity";
    Button btnTemp, btnHum, btnSettings, btnSensor3, btnSensor4, btnConnect;
    TextView txtTemp, txtHum, txtCO2, txtPart;
    private Worker mWorker = null;
    private Handler mHandler = new Handler();
    private DeviceData.GlobalSensorData mGlobalData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Cargamos las preferencias
        PreferenceManager.setDefaultValues(this, R.xml.settings, false);
    }

    private View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d(CTAG, "onClick()");
            switch (v.getId()) {
                case R.id.btnTemperature:
                    openTemperatureActivity();
                    break;
                case R.id.btnHumidity:
                    break;
                case R.id.btnSettings:
                    openSettingsActivity();
                    break;
                case R.id.btnConnect:
                    manageMQTTconnection();
                    break;
            }
        }
    };

    private Worker.WorkerListener mWorkListener = new Worker.WorkerListener() {
        @Override
        public void onStateChanged(Worker.State state) {
            Log.d(CTAG, "worker.onStateChanged() - state:" + state);
            switch(state){
                case MQTT_STATE_DISCONNECTED:
                    runOnUiThread(new Runnable(){
                        @Override
                        public void run() {
                            showProgress("Disconnected");
                            //hideProgress(true);
                            //findViewById(R.id.menu_list).setVisibility(View.INVISIBLE);
                        }
                    });
                    break;
                case MQTT_STATE_CONNECTING:
                    runOnUiThread(new Runnable(){
                        @Override
                        public void run() {
                            showProgress("Connecting...");
                            //showProgress();
                            //findViewById(R.id.menu_list).setVisibility(View.INVISIBLE);
                        }
                    });
                    break;
                case MQTT_STATE_CONNECTED:
                    runOnUiThread(new Runnable(){
                        @Override
                        public void run() {
                            Boolean get_values = false;

                            showProgress("Connected");

                            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                            if (sharedPreferences.contains(SettingsActivity.MQTT_SERVER)) {
                                get_values = sharedPreferences.getBoolean(SettingsActivity.APP_GET_VALUES,
                                        SettingsActivity.APP_CONNECT_DEFAULT);
                            }

                            if (get_values) {
                                Log.d(CTAG, "Send Instant values request");
                                mWorker.setInstantValuesMode(true);
                            }
                            //findViewById(R.id.menu_list).setVisibility(View.VISIBLE);
                            //hideProgress(false);
                            //showDeviceListDialog();
                        }
                    });
                    break;
            }
        }

        @Override
        public void onDataReceived(String devName, DeviceData.GlobalSensorData data) {
            Log.d(CTAG, "worker.onDataReceived() - devName:" + devName + ", data:" + data);
            mGlobalData = data;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mGlobalData.temperature > -100) {
                        txtTemp.setText("Temp.: " + mGlobalData.temperature + " ºC");
                    }
                    if (mGlobalData.humidity > 0) {
                        txtHum.setText("Hum.: " + mGlobalData.humidity + " %");
                    }
                    if (mGlobalData.co2 > 0) {
                        txtCO2.setText("CO2: " + mGlobalData.co2 + " ppm");
                    }
                    if (mGlobalData.particles > 0) {
                        txtPart.setText("Part.: " + mGlobalData.particles + " TPS");
                    }
                }
            });
        }

        @Override
        public void onConnectionError(final int errorNo) {
            Log.d(CTAG, "worker.onConnectionError() - errorNo:" + errorNo);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    //hideProgress(true);
                    Toast.makeText(getApplicationContext(), getString(R.string.connection_is_failed), Toast.LENGTH_SHORT).show();
                    if (mWorker == null) {
                        startMqtt();
                    }
                }
            });
        }
    };

    private void openTemperatureActivity() {
        Log.d(CTAG, "openTemperatureActivity()");
        //startActivity(new Intent(this, MainActivity.class));
    }

    private void openSettingsActivity() {
        Log.d(CTAG, "openSettingsActivity()");
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void manageMQTTconnection() {
        Log.d(CTAG, "manageMQTTconnection()");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.CloudDialogConnectTitle);
        if (mWorker == null) {
            builder.setMessage(R.string.CloudDialogConnectMessage);
        } else {
            builder.setMessage(R.string.CloudDialogDisconnectMessage);
        }
        builder.setPositiveButton("Yes",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (mWorker == null) {
                            startMqtt();
                        } else {
                            endMqtt();
                        }
                        dialog.dismiss();
                    }
                });
        builder.setNegativeButton("No",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        Dialog dialog = builder.create();
        dialog.show();
    }

    @Override
    protected void onResume() {
        Boolean connect = false;

        Log.d(CTAG, "onResume()");
        initLayout();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.contains(SettingsActivity.MQTT_SERVER)) {
            connect = sharedPreferences.getBoolean(SettingsActivity.APP_CONNECT,
                    SettingsActivity.APP_CONNECT_DEFAULT);
        }

        if((mWorker == null) && (connect == true)) {
            startMqtt();
        }
        setWakelock(true);
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        Log.d(CTAG, "onDestroy()");
        if(mWorker!=null){
            mWorker.stop();
            mWorker=null;
        }
        setWakelock(false);
        super.onDestroy();
    }

    private void initLayout(){
        Log.d(CTAG, "initLayout()");
        btnConnect = (Button) findViewById(R.id.btnConnect);
        btnTemp = (Button)findViewById(R.id.btnTemperature);
        btnHum = (Button)findViewById(R.id.btnHumidity);
        btnSettings = (Button)findViewById(R.id.btnSettings);

        txtTemp = (TextView)findViewById(R.id.txtTemperature);
        txtHum = (TextView)findViewById(R.id.txtHumidity);
        txtCO2 = (TextView)findViewById(R.id.txtCO2);
        txtPart = (TextView)findViewById(R.id.txtParticles);

        btnConnect.setOnClickListener(mClickListener);
        btnTemp.setOnClickListener(mClickListener);
        btnHum.setOnClickListener(mClickListener);
        btnSettings.setOnClickListener(mClickListener);
    }

    public void showProgress(String mqtt_status){
        btnConnect.setText(mqtt_status);
        Log.d(CTAG, "MQTT state: " + mqtt_status);

    }

    /*public void showSensorData(DeviceData.GlobalSensorData data){
        if (data.temperature > -100) {
            //txtTemp.setText("Temp.: " + data.temperature + " ºC");
            txtTemp.setText("Temp.: xx ºC");
        }
        if (data.humidity > 0) {
            txtHum.setText("Hum.: " + data.humidity + " %");
        }
        if (data.co2 > 0) {
            txtCO2.setText("CO2: " + data.co2 + " ppm");
        }
        if (data.particles > 0) {
            txtPart.setText("Part.: " + data.particles + " TPS");
        }
    }*/

    public void startMqtt(){
        Log.d(CTAG, "startMqtt()");
        if(mWorker!=null){
            mWorker.stop();
            mWorker = null;
        }

        mWorker = new Worker(CloudActivity.this, mWorkListener);
        mWorker.start();

        //clearAlertState();
        //mTempGraph.clearGraph();
        //mLightGraph.clearGraph();
    }

    public void endMqtt(){
        Log.d(CTAG, "endMqtt()");
        mWorker.setInstantValuesMode(false);

        if(mWorker!=null){
            mWorker.stop();
            mWorker = null;
        }
    }

    public void setWakelock(boolean state){
        if (state) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
}
