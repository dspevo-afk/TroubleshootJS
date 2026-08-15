package com.lushprojects.circuitjs1.client;

interface ExternalPowerControl {
    void setConnected(boolean connected);
    boolean isConnected();
}