'use strict';

(() => {
  const bridge = window.DailyReadingNative;
  if (!bridge) return;

  let nextId = 0;
  const pending = new Map();

  bridge.onmessage = ({ data }) => {
    const message = JSON.parse(data);
    const callback = pending.get(message.id);
    if (message.event !== 'start') pending.delete(message.id);
    callback?.(message);
  };

  function send(action, fields, callback) {
    const id = ++nextId;
    if (callback) pending.set(id, callback);
    try {
      bridge.postMessage(JSON.stringify({ id, action, ...fields }));
    } catch (error) {
      pending.delete(id);
      throw error;
    }
  }

  window.DRAndroid = {
    async recognize(images) {
      const results = [];
      for (const { name, dataUrl } of images) {
        if (dataUrl.length > 16 * 1024 * 1024 + 128) {
          results.push({ file: name, error: '单张图片请控制在 12 MB 以内' });
          continue;
        }
        const result = await new Promise((resolve) => {
          send('ocr', { name, dataUrl }, ({ value, error }) => resolve(value || { file: name, error }));
        });
        results.push(result);
      }
      return results;
    },

    speech: {
      available: true,
      utter: (text, options = {}) => ({ text, rate: options.rate || 0.92, ...options }),
      play(utterance, queue = false) {
        send('speak', { text: utterance.text, rate: utterance.rate, queue }, ({ event, error }) => {
          if (event === 'start') utterance.onstart?.();
          if (event === 'error') (utterance.onError || utterance.onerror)?.(error);
          if (event !== 'start') utterance.onend?.();
        });
      },
      say(text, options = {}) {
        const utterance = this.utter(text, { ...options, onstart: options.onStart });
        this.play(utterance);
        return utterance;
      },
      stop: () => send('stopSpeech'),
    },
  };

  document.documentElement.classList.add('android-app');
  const copy = {
    '.hero-intro': '安卓本地运行 · 学习记录保存在手机里',
    '.hero-lede': '从百词斩截图导入单词，用 DeepSeek 生成考研英语阅读。文章、历史和生词本保存在手机里。',
    '.hero-formats': '选图 · 手动输入｜本机 OCR｜复习 / 真题',
    '.hero-art .hw-sub[x="280"]': 'Android OCR',
    '.dz-main': '点这里选百词斩截图',
    '.dz-sub': '从手机相册或文件里选图',
    '#cfgKeyTip': 'Key 保存在手机里，用于向你配置的模型接口发起请求。本机识别截图无需 Key。',
  };
  for (const [selector, text] of Object.entries(copy)) {
    const element = document.querySelector(selector);
    if (element) element.textContent = text;
  }
})();
