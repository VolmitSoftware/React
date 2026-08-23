(function () {
  let mode = 'dark';
  try {
    const stored = window.localStorage.getItem('reactor.theme');
    if (stored === 'light' || stored === 'dark') { mode = stored; }
  } catch (e) {}
  const root = document.documentElement;
  root.classList.remove('light', 'dark');
  root.classList.add(mode);
  const themeColor = document.getElementById('reactor-theme-color');
  if (themeColor) {
    themeColor.setAttribute(
      'content',
      mode === 'light' ? '#f5f5f6' : '#0a0a0b'
    );
  }

  const locales = {
    'en': ['en-US', false, 'en_US'],
    'de': ['de-DE', false, 'de_DE'],
    'es': ['es-ES', false, 'es_ES'],
    'fi': ['fi-FI', false, 'fi_FI'],
    'fr': ['fr-FR', false, 'fr_FR'],
    'he': ['he-IL', true, 'he_IL'],
    'it': ['it-IT', false, 'it_IT'],
    'ja': ['ja-JP', false, 'ja-JP'],
    'ko': ['ko-KR', false, 'ko_KR'],
    'lt': ['lt-LT', false, 'lt_LT'],
    'nl': ['nl-NL', false, 'nl_NL'],
    'pl': ['pl-PL', false, 'pl_PL'],
    'pt': ['pt-PT', false, 'pt_PT'],
    'ru': ['ru-RU', false, 'ru_RU'],
    'tr': ['tr-TR', false, 'tr_TR'],
    'vi': ['vi-VN', false, 'vi_VI'],
    'zh': ['zh-CN', false, 'zh_CN'],
    'zh-cn': ['zh-CN', false, 'zh_CN'],
    'zh-sg': ['zh-CN', false, 'zh_CN'],
    'zh-hans': ['zh-CN', false, 'zh_CN'],
    'zh-tw': ['zh-TW', false, 'zh_TW'],
    'zh-hk': ['zh-TW', false, 'zh_TW'],
    'zh-mo': ['zh-TW', false, 'zh_TW'],
    'zh-hant': ['zh-TW', false, 'zh_TW']
  };
  const resolveLocale = function (candidate) {
    const normalized = (candidate || '').replace('_', '-').toLowerCase();
    const exact = locales[normalized];
    if (exact) { return exact; }
    const parts = normalized.split('-');
    if (parts[0] === 'zh' && parts.some(function (part) {
      return part === 'tw' || part === 'hk' || part === 'mo' ||
        part === 'hant';
    })) {
      return locales['zh-tw'];
    }
    return locales[parts[0]] || null;
  };
  let storedLocale = null;
  try {
    storedLocale = window.localStorage.getItem('reactor.locale');
  } catch (e) {}
  let locale = resolveLocale(storedLocale);
  if (!locale) {
    const preferredLocales = window.navigator.languages &&
      window.navigator.languages.length > 0
      ? window.navigator.languages
      : [window.navigator.language];
    for (let index = 0; index < preferredLocales.length; index++) {
      locale = resolveLocale(preferredLocales[index]);
      if (locale) { break; }
    }
  }
  locale = locale || locales.en;
  root.setAttribute('lang', locale[0]);
  root.setAttribute('dir', locale[1] ? 'rtl' : 'ltr');
  document.getElementById('reactor-manifest').setAttribute(
    'href', '/manifests/' + locale[2] + '.webmanifest'
  );
})();
