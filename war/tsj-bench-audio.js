/* Short mechanical contact clicks. Audio failures never affect the simulator. */
(function (window, document) {
  'use strict';
  var context = null, muted = false;
  try { muted = window.localStorage.getItem('tsj.relay-audio.v1') === 'muted'; } catch (_) {}
  function prepare() {
    try {
      var Audio = window.AudioContext || window.webkitAudioContext;
      if (!Audio || muted) return;
      if (!context) context = new Audio();
      if (context.state === 'suspended') context.resume().catch(function () {});
    } catch (_) {}
  }
  document.addEventListener('pointerdown', prepare, { passive: true });
  document.addEventListener('keydown', prepare, { passive: true });
  window.tsjBenchAudio = {
    getMuted: function () { return muted; },
    setMuted: function (value) {
      muted = !!value;
      try { window.localStorage.setItem('tsj.relay-audio.v1', muted ? 'muted' : 'on'); } catch (_) {}
      if (!muted) prepare();
    },
    relay: function (energized) {
      if (muted || document.hidden || window.tsjWorkbenchOverlayOpen || !context || context.state !== 'running') return;
      try {
        var duration = energized ? .035 : .027;
        var buffer = context.createBuffer(1, Math.ceil(context.sampleRate * duration), context.sampleRate);
        var values = buffer.getChannelData(0), seed = 1234567;
        for (var i = 0; i < values.length; i++) {
          seed = (Math.imul(seed, 1664525) + 1013904223) | 0;
          values[i] = (seed / 2147483648) * Math.exp(-i / (context.sampleRate * .006));
        }
        var source = context.createBufferSource(), filter = context.createBiquadFilter(), gain = context.createGain();
        source.buffer = buffer; filter.type = 'bandpass'; filter.frequency.value = energized ? 2100 : 1450;
        filter.Q.value = .7; gain.gain.value = .28;
        source.connect(filter); filter.connect(gain); gain.connect(context.destination);
        source.onended = function () { source.disconnect(); filter.disconnect(); gain.disconnect(); };
        source.start();
        document.dispatchEvent(new window.CustomEvent('tsj-relay-click', { detail: { energized: !!energized } }));
      } catch (_) {}
    }
  };
  window.addEventListener('pagehide', function () { if (context) context.suspend().catch(function () {}); });
}(window, document));
