import Mapbox from '@rnmapbox/maps';

import { env } from './env';

/**
 * §5.3 — Public Token(pk.) 하나만 사용합니다. Download token 은 폐기되었습니다.
 * 앱 진입 시 1회만 호출하십시오.
 */
let initialized = false;

export function initMapbox() {
  if (initialized) return;
  Mapbox.setAccessToken(env.mapboxToken);
  initialized = true;
}
