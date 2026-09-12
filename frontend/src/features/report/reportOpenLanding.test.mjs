import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const require = createRequire(import.meta.url);
const landingScriptUrl = new URL('../../../../infra/nginx/www/share/report-open.js', import.meta.url);
const landing = require(fileURLToPath(landingScriptUrl));

const readAsset = (relativePath) =>
  readFileSync(new URL(`../../../../${relativePath}`, import.meta.url), 'utf8');

test('대상 Android 앱만 설치된 관련 앱으로 판정한다', () => {
  assert.equal(
    landing.isTargetAppInstalled([
      { platform: 'play', id: 'com.ssafy.ssabangpalbang' },
    ]),
    true,
  );
  assert.equal(
    landing.isTargetAppInstalled([
      { platform: 'play', id: 'com.example.somewhere' },
      { platform: 'webapp', id: 'com.ssafy.ssabangpalbang' },
    ]),
    false,
  );
  assert.equal(landing.isTargetAppInstalled(undefined), false);
});

test('설치 감지 API 미지원·빈 결과·오류는 정상 fallback으로 처리한다', async () => {
  assert.equal(await landing.detectTargetApp({}), false);
  assert.equal(
    await landing.detectTargetApp({ getInstalledRelatedApps: async () => [] }),
    false,
  );
  assert.equal(
    await landing.detectTargetApp({
      getInstalledRelatedApps: async () => {
        throw new Error('browser API unavailable');
      },
    }),
    false,
  );
});

test('설치 감지 성공 시 자동 이동 없이 확인 dialog만 연다', async () => {
  let showCount = 0;
  const status = { textContent: '' };
  const dialog = {
    open: false,
    showModal() {
      showCount += 1;
      this.open = true;
    },
  };
  const documentObject = {
    getElementById(id) {
      if (id === 'installed-app-dialog') return dialog;
      if (id === 'installation-note') return status;
      return null;
    },
  };

  const installed = await landing.initializeLanding(documentObject, {
    getInstalledRelatedApps: async () => [
      { platform: 'play', id: landing.ANDROID_PACKAGE_ID },
    ],
  });

  assert.equal(installed, true);
  assert.equal(showCount, 1);
  assert.match(status.textContent, /설치된/);
});

test('앱 열기 실패 fallback에서는 설치돼 있어도 dialog를 다시 열지 않는다', async () => {
  let showCount = 0;
  const status = { textContent: '' };
  const documentObject = {
    getElementById(id) {
      if (id === 'installed-app-dialog') {
        return { open: false, showModal: () => { showCount += 1; } };
      }
      if (id === 'installation-note') return status;
      return null;
    },
  };

  const installed = await landing.initializeLanding(
    documentObject,
    {
      getInstalledRelatedApps: async () => [
        { platform: 'play', id: landing.ANDROID_PACKAGE_ID },
      ],
    },
    { search: '?install=1' },
  );

  assert.equal(installed, false);
  assert.equal(showCount, 0);
  assert.match(status.textContent, /열지 못했습니다/);
});

test('정적 랜딩은 공유 URL과 앱 진입 URL을 분리한다', () => {
  const html = readAsset('infra/nginx/www/share/report.html');
  const manifest = readAsset('infra/nginx/www/share/report.webmanifest');
  const parsedManifest = JSON.parse(manifest);
  const nginx = readAsset('infra/nginx/ssabangpalbang.conf');
  const installHref = html.match(/id="install-app"\s+href="([^"]+)"/)?.[1];

  assert.match(html, /rel="canonical" href="__REPORT_URL__"/);
  assert.match(html, /href="__REPORT_OPEN_URL__"/);
  assert.equal(new URL(installHref).protocol, 'https:');
  assert.ok(
    html.indexOf('id="install-app"') < html.indexOf('id="open-app"'),
    '미설치 fallback에서는 설치 CTA가 앱 열기보다 먼저 노출되어야 한다',
  );
  assert.deepEqual(parsedManifest.related_applications, [
    { platform: 'play', id: 'com.ssafy.ssabangpalbang' },
  ]);
  assert.doesNotMatch(manifest, /example\.com\/apps\/ssabangpalbang/);
  assert.match(manifest, /"id": "com\.ssafy\.ssabangpalbang"/);
  assert.match(nginx, /return 302 https:\/\/i15a701\.p\.ssafy\.io\/report\/\$report_id\?install=1;/);
});
