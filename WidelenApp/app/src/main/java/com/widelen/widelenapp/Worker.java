package com.widelen.widelenapp;

import com.widelen.widelenapp.DeviceData.SensorData;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.InputStream;
import java.util.ArrayList;

public class Worker {
    public static final String TAG = "MqttWorker";
    public static final Integer SENSOR_DATA_TMP_MASK = 1;
    public static final Integer SENSOR_DATA_HUM_MASK = 2;
    public static final Integer SENSOR_DATA_CO2_MASK = 4;
    public static final Integer SENSOR_DATA_PAR_MASK = 8;

    public static enum State{
        MQTT_STATE_DISCONNECTED,
        MQTT_STATE_CONNECTING,
        MQTT_STATE_CONNECTED,
        MQTT_STATE_WAITING_CONN_LOST_DELAY,
    }

    public interface WorkerListener{
        public void onConnectionError(int errorNo);
        public void onStateChanged(State state);
        public void onDataReceived(String devName, DeviceData.GlobalSensorData data);
    }

    private static final long ACTIVE_LIFE_TIME = 60*1000;

    private Context mContext = null;
    private WorkerListener mListener = null;
    private boolean mIStopMqttThread = true;
    private Thread mMqttCommThread = null;
    private MemoryPersistence mPersistence = null;
    private MqttClient mClient = null;
    private MqttConnectOptions mConnOpts = null;
    private State mMqttConnState = State.MQTT_STATE_DISCONNECTED;

    private boolean mIsNeedDeviceList = false;
    private DeviceData mDeviceData = new DeviceData();
    private boolean mIsConnectionError = false;

    public Worker(Context c, WorkerListener l){
        mContext = c;
        mListener = l;
        mIStopMqttThread = true;
    }

    public void onConnectionError(int errorNo){
        mIsConnectionError = true;
        if(mListener != null){
            mListener.onConnectionError(errorNo);
        }
    }

    public void onStateChanged(State state){
        Log.d(TAG, "onNotify() - state:"+state);
        if(mListener != null){
            mListener.onStateChanged(state);
        }
    }

    public void onDataReceived(String devName, DeviceData.GlobalSensorData data){
        Log.d(TAG, "onDataReceived() - devName:" + devName + ", data:" + data);
        mListener.onDataReceived(devName, data);
    }

    public void resetConnectionError(){
        mIsConnectionError = false;
    }

    public boolean start(){
        Log.d(TAG, "start()");
        if(!mIStopMqttThread || mMqttCommThread!=null){
            return true;
        }
        mMqttConnState = State.MQTT_STATE_DISCONNECTED;
        mMqttCommThread = new Thread(new Runnable(){
            @Override
            public void run() {
                mIStopMqttThread = false;
                mIsConnectionError = false;
                mMqttConnState = State.MQTT_STATE_DISCONNECTED;
                mDeviceData.clearDeviceInfo();
                while(!mIStopMqttThread && !mIsConnectionError){
                    if(mMqttConnState == State.MQTT_STATE_CONNECTED){
                    }else if(mMqttConnState == State.MQTT_STATE_DISCONNECTED){
                        mMqttConnState = State.MQTT_STATE_CONNECTING;
                        initMqtt();
                        connectMqttServer();
                    }else if(mMqttConnState == State.MQTT_STATE_WAITING_CONN_LOST_DELAY){
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        mMqttConnState = State.MQTT_STATE_DISCONNECTED;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                disconnectMqttServer();
                mIStopMqttThread = true;
            }
        });
        mMqttCommThread.start();
        return true;
    }

    public void stop(){
        Log.d(TAG, "stop()");
        mIStopMqttThread = true;
        mDeviceData.clearDeviceInfo();
        if(mMqttCommThread!=null){
            try {
                mMqttCommThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally{
                mMqttCommThread = null;
            }
        }
    }

    /*public void setTargetDevice(DeviceInfo targetDevice){
        if(targetDevice==null){
            return;
        }
        mTargetDevice.name = targetDevice.name;
        mTargetDevice.macAddr = targetDevice.macAddr;
    }*/

    private void initMqtt(){
        Log.d(TAG, "initMqtt()");
        try {
            if(mClient!=null){
                mClient.close();
                mClient=null;
            }
            if(mPersistence!=null){
                mPersistence.clear();
                mPersistence.close();
            }
            mPersistence = new MemoryPersistence();
            mClient = new MqttClient(getBrokerAddress(), getMqttClientId(), mPersistence);
            mConnOpts = new MqttConnectOptions();
            mConnOpts.setCleanSession(true);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            String user = prefs.getString(SettingsActivity.MQTT_USER, "");
            mConnOpts.setUserName(user);
            String pwd = prefs.getString(SettingsActivity.MQTT_PWD, "");
            mConnOpts.setPassword(pwd.toCharArray());

            /*Boolean setting_use_tls = prefs.getBoolean(SettingsActivity.MQTT_TLS, false);
            if(setting_use_tls){
                Log.d(TAG, "Enable SSL Connection.");

                try {
                    InputStream trustStoreStream = mContext.getResources().openRawResource(R.raw.certificate_test_mqtt);
                    mConnOpts.setSocketFactory(SSLUtil.getSSLSocketFactory(trustStoreStream, "mqtttest"));
                } catch(Exception e) {
                    Log.e(TAG, "getSSLSocketFactory error");
                    e.printStackTrace();
                }
            }*/
        } catch(MqttException me) {
            onConnectionError(me.getReasonCode());
            Log.e(TAG, "initMqtt() exception");
            Log.e(TAG, "reason "+me.getReasonCode());
            Log.e(TAG, "msg "+me.getMessage());
            Log.e(TAG, "loc "+me.getLocalizedMessage());
            Log.e(TAG, "cause "+me.getCause());
            Log.e(TAG, "excep "+me);
            me.printStackTrace();
        }
    }

    private void connectMqttServer(){
        Log.d(TAG, "connectMqttServer() - mMqttConnState:"+mMqttConnState);
        if(mMqttConnState == State.MQTT_STATE_CONNECTED){
            return;
        }
        onStateChanged(State.MQTT_STATE_CONNECTING);
        mIsNeedDeviceList = true;

        try {
            Log.d(TAG, "Connecting to broker: "+getBrokerAddress());
            mClient.setCallback(new MqttCallback(){
                @Override
                public void connectionLost(Throwable cause) {
                    Log.d(TAG, "connectionLost() - cause:"+cause.getCause()+", message:"+cause.getMessage());
                    cause.printStackTrace();
                    mMqttConnState = State.MQTT_STATE_WAITING_CONN_LOST_DELAY;
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    Log.d(TAG, "deliveryComplete()");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    Log.d(TAG, "messageArrived() - topic("+topic+"), message("+message+")");
                    Log.d(TAG, "messageArrived()- start:" + System.currentTimeMillis());
                    processMqttMessage(topic, message.toString());
                    Log.d(TAG, "messageArrived()- end:"+System.currentTimeMillis());
                }
            });
            mClient.connect(mConnOpts);
            mMqttConnState = State.MQTT_STATE_CONNECTED;
            onStateChanged(State.MQTT_STATE_CONNECTED);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            setSubscribe();
        } catch(MqttException me) {
            onConnectionError(me.getReasonCode());
            Log.e(TAG, "connectMqttServer() exception");
            Log.e(TAG, "reason "+me.getReasonCode());
            Log.e(TAG, "msg "+me.getMessage());
            Log.e(TAG, "loc "+me.getLocalizedMessage());
            Log.e(TAG, "cause "+me.getCause());
            Log.e(TAG, "excep "+me);
            me.printStackTrace();
            mMqttConnState = State.MQTT_STATE_DISCONNECTED;
            onStateChanged(State.MQTT_STATE_DISCONNECTED);
        }
    }

    private void disconnectMqttServer(){
        Log.d(TAG, "disconnectMqttServer()");
        if(mClient == null || (mMqttConnState != State.MQTT_STATE_CONNECTED)){
            return;
        }
        try {
            mClient.disconnect();
            mClient = null;
            mMqttConnState = State.MQTT_STATE_DISCONNECTED;
            onStateChanged(State.MQTT_STATE_DISCONNECTED);
        } catch(MqttException me) {
            Log.e(TAG, "disconnectMqttServer() exception");
            Log.e(TAG, "reason "+me.getReasonCode());
            Log.e(TAG, "msg "+me.getMessage());
            Log.e(TAG, "loc "+me.getLocalizedMessage());
            Log.e(TAG, "cause "+me.getCause());
            Log.e(TAG, "excep "+me);
            me.printStackTrace();
        }
    }

    private void processMqttMessage(final String mqtt_topic, final String mqtt_msg){
        SensorData data = new SensorData();
        DeviceData.SensorTData Tdata = new DeviceData.SensorTData();
        DeviceData.GlobalSensorData gData = new DeviceData.GlobalSensorData();
        String devName, devAddr, devInfo;
        Integer dataMask;
        Integer idx = 0;

        String[] arrayDataSensor = mqtt_msg.split("/");

        devInfo = arrayDataSensor[idx++];
        String[] arrayDataInfo = devInfo.split(":");
        devName = arrayDataInfo[0];
        devAddr = arrayDataInfo[1] + arrayDataInfo[2];
        mDeviceData.setDeviceInfo(devName, devAddr);

        data.dataTime = Integer.parseInt(arrayDataSensor[idx++]);
        Tdata.dataTime = data.dataTime;
        gData.time = data.dataTime;
        gData.temperature = -100;
        gData.humidity = 0;
        gData.co2 = 0;
        gData.particles = 0;
        dataMask = Integer.parseInt(arrayDataSensor[idx++]);
        if ((dataMask & SENSOR_DATA_TMP_MASK) > 0) {
            Integer i_temp_val;
            i_temp_val = Integer.parseInt(arrayDataSensor[idx++]);
            Tdata.value = (double)i_temp_val / 100;
            mDeviceData.addTemperatureData(Tdata);
            gData.temperature = Tdata.value;

        }
        if ((dataMask & SENSOR_DATA_HUM_MASK) > 0) {
            data.value = Integer.parseInt(arrayDataSensor[idx++]);
            mDeviceData.addHumidityData(data);
            gData.humidity = data.value;
        }
        if ((dataMask & SENSOR_DATA_CO2_MASK) > 0) {
            data.value = Integer.parseInt(arrayDataSensor[idx++]);
            mDeviceData.addCO2Data(data);
            gData.co2 = data.value;
        }
        if ((dataMask & SENSOR_DATA_PAR_MASK) > 0) {
            data.value = Integer.parseInt(arrayDataSensor[idx++]);
            mDeviceData.addParticlesData(data);
            gData.particles = data.value;
        }

        onDataReceived(devName, gData);
    }

    private void setSubscribe(){
        Log.d(TAG, "setSubscribe() - mClient:"+mClient+", mMqttConnState:"+mMqttConnState);
        if(mClient == null || (mMqttConnState != State.MQTT_STATE_CONNECTED)){
            return;
        }
        try {
        	String topic = mContext.getResources().getString(R.string.MQTT_RX_TOPIC);
            Log.d(TAG, "setSubscribe() - topic:" + topic);
            mClient.subscribe(topic);
        } catch(MqttException me) {
            Log.e(TAG, "setSubscribe() exception");
            Log.e(TAG, "reason "+me.getReasonCode());
            Log.e(TAG, "msg "+me.getMessage());
            Log.e(TAG, "loc "+me.getLocalizedMessage());
            Log.e(TAG, "cause "+me.getCause());
            Log.e(TAG, "excep "+me);
            me.printStackTrace();
        }
    }

    private String getMqttClientId(){
        WifiManager manager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = manager.getConnectionInfo();
        String address = info.getMacAddress(); //78:F7:BE:FA:92:5A
        address = address.replace(":", "");
        return String.format("widelen:%s", address);
    }

    public String getBrokerAddress(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String server = prefs.getString(SettingsActivity.MQTT_SERVER, "");
        String port = prefs.getString(SettingsActivity.MQTT_PORT, "");
        /*SharedPreferences prefs = mContext.getSharedPreferences(Config.PREF_NAME, Activity.MODE_PRIVATE);
        int setting_use_tls = prefs.getInt(Config.PREF_KEY_USE_TLS, 0);
        if(setting_use_tls == 1){
            return "ssl://"+Config.MQTT_BROKER_HOST+":8883";
        }else{
            return "tcp://"+Config.MQTT_BROKER_HOST+":1883";
        }*/
        return "tcp://"+server+":"+port;
    }
    
    public void setInstantValuesMode(Boolean instant_values_enable) {
            if(mClient == null || (mMqttConnState != State.MQTT_STATE_CONNECTED)){
                return;
            }

            Log.d(TAG, "Send Instant values request");
            try {
                String topic, msg_content;
                topic = mContext.getResources().getString(R.string.MQTT_TX_TOPIC);
            	if (instant_values_enable) {
                    msg_content = mContext.getResources().getString(R.string.MQTT_INSTANT_VALUES_ENABLE);
            	} else {
                    msg_content = mContext.getResources().getString(R.string.MQTT_INSTANT_VALUES_DISABLE);
            	}

            	MqttMessage message = new MqttMessage(msg_content.getBytes());
                message.setQos(2);

                mClient.publish(topic, message);
            } catch(MqttException me) {
                Log.e(TAG, "publish exception");
                Log.e(TAG, "reason "+me.getReasonCode());
                Log.e(TAG, "msg "+me.getMessage());
                Log.e(TAG, "loc "+me.getLocalizedMessage());
                Log.e(TAG, "cause "+me.getCause());
                Log.e(TAG, "excep "+me);
                me.printStackTrace();
            }
        	
    }
}
