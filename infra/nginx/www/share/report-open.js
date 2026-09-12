(function attachReportLanding(factory) {
  const api = factory();

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api;
  }

  if (typeof window === 'undefined' || !window.document) return;

  window.SsabangReportLanding = api;
  const initialize = () => {
    void api.initializeLanding(window.document, window.navigator, window.location);
  };

  if (window.document.readyState === 'loading') {
    window.document.addEventListener('DOMContentLoaded', initialize, { once: true });
  } else {
    initialize();
  }
})(function createReportLanding() {
  'use strict';

  const ANDROID_PACKAGE_ID = 'com.ssafy.ssabangpalbang';

  function isTargetAppInstalled(relatedApps) {
    return (
      Array.isArray(relatedApps) &&
      relatedApps.some(
        (app) => app && app.platform === 'play' && app.id === ANDROID_PACKAGE_ID,
      )
    );
  }

  async function detectTargetApp(navigatorObject) {
    const getInstalledRelatedApps = navigatorObject?.getInstalledRelatedApps;
    if (typeof getInstalledRelatedApps !== 'function') return false;

    try {
      const relatedApps = await getInstalledRelatedApps.call(navigatorObject);
      return isTargetAppInstalled(relatedApps);
    } catch {
      return false;
    }
  }

  function showInstalledAppDialog(documentObject) {
    const dialog = documentObject?.getElementById('installed-app-dialog');
    if (!dialog || typeof dialog.showModal !== 'function') return false;

    try {
      if (!dialog.open) dialog.showModal();
      const status = documentObject.getElementById('installation-note');
      if (status) status.textContent = '설치된 싸방팔방 앱을 확인했습니다.';
      return true;
    } catch {
      return false;
    }
  }

  function isInstallFallback(locationObject) {
    const search = locationObject?.search;
    return typeof search === 'string' && /(?:^\?|&)install=1(?:&|$)/.test(search);
  }

  async function initializeLanding(documentObject, navigatorObject, locationObject) {
    if (isInstallFallback(locationObject)) {
      const status = documentObject?.getElementById('installation-note');
      if (status) status.textContent = '앱을 열지 못했습니다. 설치 후 다시 시도해 주세요.';
      return false;
    }

    const installed = await detectTargetApp(navigatorObject);
    if (installed) showInstalledAppDialog(documentObject);
    return installed;
  }

  return {
    ANDROID_PACKAGE_ID,
    detectTargetApp,
    initializeLanding,
    isInstallFallback,
    isTargetAppInstalled,
    showInstalledAppDialog,
  };
});
