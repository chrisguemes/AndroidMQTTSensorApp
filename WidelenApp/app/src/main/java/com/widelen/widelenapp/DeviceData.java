package com.widelen.widelenapp;

import java.util.ArrayList;

public class DeviceData {
    public static final int DEVICE_LIST_SIZE = 256;

    public static class SensorData{
        long dataTime;
        int value;
    }

    public static class SensorTData{
        long dataTime;
        double value;
    }

    public static class GlobalSensorData{
        long time;
        double temperature;
        int humidity;
        int co2;
        int particles;
    }

    String dev_name = null;
    String dev_macAddr = null;
    ArrayList<SensorTData> temperatureDataList = new ArrayList<>();
    ArrayList<SensorData> humidityDataList = new ArrayList<>();
    ArrayList<SensorData> co2DataList = new ArrayList<>();
    ArrayList<SensorData> particlesDataList = new ArrayList<>();

    public DeviceData(){
        dev_name = null;
        dev_macAddr = null;
    }

    public DeviceData(String name, String mac){
        dev_name = name;
        dev_macAddr = mac;
    }

    public void addTemperatureData(SensorTData data){
        if(temperatureDataList.size() >= DEVICE_LIST_SIZE){
            temperatureDataList.remove(0);
        }
        temperatureDataList.add(data);
    }

    public void clearTemperatureData(){
        temperatureDataList.clear();
    }

    public ArrayList<SensorTData> getTemperatureData(){
        return temperatureDataList;
    }

    public void addHumidityData(SensorData data){
        if(humidityDataList.size() >= DEVICE_LIST_SIZE){
            humidityDataList.remove(0);
        }
        humidityDataList.add(data);
    }

    public void clearHumidityData(){
        humidityDataList.clear();
    }

    public ArrayList<SensorData> getHumidityData(){
        return humidityDataList;
    }

    public void addCO2Data(SensorData data){
        if(co2DataList.size() >= DEVICE_LIST_SIZE){
            co2DataList.remove(0);
        }
        co2DataList.add(data);
    }

    public void clearCO2Data(){
        co2DataList.clear();
    }

    public ArrayList<SensorData> getCO2Data(){
        return co2DataList;
    }

    public void addParticlesData(SensorData data){
        if(particlesDataList.size() >= DEVICE_LIST_SIZE){
            particlesDataList.remove(0);
        }
        particlesDataList.add(data);
    }

    public void clearParticlesData(){
        particlesDataList.clear();
    }

    public ArrayList<SensorData> getParticlesData(){
        return particlesDataList;
    }

    public void setDeviceInfo(String name, String macAddr){
        dev_name = name;
        dev_macAddr = macAddr;
    }

    public void clearDeviceInfo(){
        dev_name = null;
        dev_macAddr = null;
        temperatureDataList.clear();
        humidityDataList.clear();
        co2DataList.clear();
        particlesDataList.clear();
    }

    public String name(){
        return dev_name;
    }

    public String macAddress(){
        return dev_macAddr;
    }
}
