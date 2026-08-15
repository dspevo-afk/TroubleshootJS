package com.lushprojects.circuitjs1.client;

class ExternalBoardPowerInput {
    private final String id;
    private final String positivePadId;
    private final String returnPadId;
    private final String positiveNetId;
    private final String returnNetId;

    ExternalBoardPowerInput(String id, String positivePadId, String returnPadId,
            String positiveNetId, String returnNetId) {
        this.id = id;
        this.positivePadId = positivePadId;
        this.returnPadId = returnPadId;
        this.positiveNetId = positiveNetId;
        this.returnNetId = returnNetId;
    }

    String getId() {
        return id;
    }

    String getPositivePadId() {
        return positivePadId;
    }

    String getReturnPadId() {
        return returnPadId;
    }

    String getPositiveNetId() {
        return positiveNetId;
    }

    String getReturnNetId() {
        return returnNetId;
    }
}
