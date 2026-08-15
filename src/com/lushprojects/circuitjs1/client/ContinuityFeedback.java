package com.lushprojects.circuitjs1.client;

interface ContinuityFeedback {
    void prepare();
    int getPrepareCount();
    void setActive(boolean active);
    boolean isRequestedActive();
    int getStartCount();
    int getStopCount();
}
