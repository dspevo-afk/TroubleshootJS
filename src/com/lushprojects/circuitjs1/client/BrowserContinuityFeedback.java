package com.lushprojects.circuitjs1.client;

class BrowserContinuityFeedback implements ContinuityFeedback {
    private boolean requestedActive;
    private int startCount;
    private int stopCount;

    public void prepare() {
        prepareAudio();
    }

    public void setActive(boolean active) {
        if (requestedActive == active)
            return;
        requestedActive = active;
        if (active) {
            startCount++;
            startTone();
        } else {
            stopCount++;
            stopTone();
        }
    }

    public boolean isRequestedActive() {
        return requestedActive;
    }

    public int getStartCount() {
        return startCount;
    }

    public int getStopCount() {
        return stopCount;
    }

    private static native void prepareAudio() /*-{
        try {
            var Context = $wnd.AudioContext || $wnd.webkitAudioContext;
            if (!Context) return;
            var state = $wnd.__troubleshootJsContinuityAudio;
            if (!state) state = $wnd.__troubleshootJsContinuityAudio = {};
            if (!state.context) state.context = new Context();
            if (state.context.resume) state.context.resume();
        } catch (e) { }
    }-*/;

    private static native void startTone() /*-{
        try {
            var Context = $wnd.AudioContext || $wnd.webkitAudioContext;
            if (!Context) return;
            var state = $wnd.__troubleshootJsContinuityAudio;
            if (!state) state = $wnd.__troubleshootJsContinuityAudio = {};
            if (!state.context) state.context = new Context();
            if (state.context.resume) state.context.resume();
            if (state.oscillator) return;
            var gain = state.context.createGain();
            gain.gain.value = .04;
            var oscillator = state.context.createOscillator();
            oscillator.type = 'square';
            oscillator.frequency.value = 1000;
            oscillator.connect(gain);
            gain.connect(state.context.destination);
            oscillator.start();
            state.gain = gain;
            state.oscillator = oscillator;
        } catch (e) { }
    }-*/;

    private static native void stopTone() /*-{
        try {
            var state = $wnd.__troubleshootJsContinuityAudio;
            if (!state || !state.oscillator) return;
            state.oscillator.stop();
            state.oscillator.disconnect();
            if (state.gain) state.gain.disconnect();
            state.oscillator = null;
            state.gain = null;
        } catch (e) { }
    }-*/;
    }
