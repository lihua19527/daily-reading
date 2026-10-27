/* 浏览器朗读（SpeechSynthesis）统一入口。
 *
 * 直接把 cancel() 和 speak() 写在一起，在 Windows 上有几个常见的坑：
 *   1. cancel() 之后个别机器会停在 paused 状态，不 resume() 就再也不出声；
 *   2. cancel() 与 speak() 落在同一帧时，Chromium 偶尔把这句丢掉；
 *   3. 不显式指定 voice 时，系统可能挑到一个没装好的语音包，表现为“点了没反应”；
 *   4. 静默失败完全没有反馈——用户不知道是没声音还是没点中。
 * 这里把这四件事一次处理掉。 */
(function () {
  const synth = window.speechSynthesis;

  /* voices 是异步加载的，第一次调用常常是空数组。
   * 优先挑本地语音：Edge 里那些 “(Natural) Online” 的云语音要联网取音频，
   * 网络不稳时表现就是点了没声音，本地 SAPI 语音没这个毛病。 */
  function pickVoice() {
    if (!synth) return null;
    const voices = synth.getVoices() || [];
    if (!voices.length) return null;
    const en = voices.filter((v) => /^en[-_]/i.test(v.lang));
    if (!en.length) return null;
    const local = en.filter((v) => v.localService);
    const pool = local.length ? local : en;
    return (
      pool.find((v) => /zira|aria|jenny|sonia|guy|david|mark|hazel/i.test(v.name)) ||
      pool.find((v) => /google/i.test(v.name)) ||
      pool.find((v) => !/online/i.test(v.name)) ||
      pool[0]
    );
  }

  function utter(text, opts = {}) {
    const u = new SpeechSynthesisUtterance(text);
    u.lang = 'en-US';
    u.rate = opts.rate || 0.92;
    const voice = pickVoice();
    if (voice) u.voice = voice;
    if (opts.onStart) u.onstart = opts.onStart;
    u.onerror = (e) => {
      // interrupted / canceled 是我们自己打断的，不算出错
      const err = e && e.error;
      if (!err || err === 'interrupted' || err === 'canceled') return;
      if (opts.onError) opts.onError(err);
    };
    return u;
  }

  function say(text, opts = {}) {
    if (!synth) return null;
    try {
      synth.cancel();
      // cancel 之后可能停在 paused，不 resume 就永远不出声
      synth.resume();
    } catch (_) {}
    const u = utter(text, opts);
    let started = false;
    const prevStart = u.onstart;
    u.onstart = (e) => {
      started = true;
      if (prevStart) prevStart.call(u, e);
    };
    // cancel 与 speak 同帧时偶尔丢句子，挪到下一轮事件循环
    setTimeout(() => {
      try {
        synth.speak(u);
      } catch (_) {}
    }, 0);
    // 一点动静都没有 → 多半是没装英文语音包 / 输出设备不对，给一句人话提示
    if (opts.onSilent) {
      setTimeout(() => {
        if (!started) opts.onSilent();
      }, 1400);
    }
    return u;
  }

  window.DRSpeech = { available: !!synth, say, utter, pickVoice };

  /* 插拔耳机 / 切换蓝牙时，Chromium 有时仍把语音往旧设备送，表现就是“静音”。
     设备一变就把队列清掉、解除 paused，下一次点朗读才会重新落到当前设备上。 */
  if (synth && navigator.mediaDevices && navigator.mediaDevices.addEventListener) {
    navigator.mediaDevices.addEventListener('devicechange', () => {
      try {
        synth.cancel();
        synth.resume();
      } catch (_) {}
    });
  }
})();
