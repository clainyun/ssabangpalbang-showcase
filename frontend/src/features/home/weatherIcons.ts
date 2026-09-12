/**
 * iconKey를 1순위로, 없으면 conditionCode를 봅니다. docs/API.md는 예시를 하나만
 * 주고("clear_day"/"CLEAR_SKY") 전체 값 목록을 정의하지 않아서, 흔한 패턴 문자열
 * 포함 여부로 매칭하고 모르는 값은 기본 구름 이모지로 떨어집니다.
 *
 * 라인 아이콘 대신 컬러 이모지를 씁니다 — 안드로이드 기본 이모지 폰트(Noto Color
 * Emoji)가 알록달록하고 입체적으로 그려줘서 별도 3D 아이콘 에셋 없이 원하는
 * "컬러 3D" 느낌을 낼 수 있습니다.
 */
export function resolveWeatherEmoji(iconKey: string | null, conditionCode: string | null): string {
  const key = (iconKey ?? conditionCode ?? '').toLowerCase();

  if (key.includes('thunder') || key.includes('storm')) {
    return '⛈️';
  }
  if (key.includes('snow')) {
    return '🌨️';
  }
  if (key.includes('rain') || key.includes('shower') || key.includes('drizzle')) {
    return '🌧️';
  }
  if (key.includes('fog') || key.includes('mist') || key.includes('haze')) {
    return '🌫️';
  }
  if (key.includes('partly')) {
    return key.includes('night') ? '☁️' : '⛅';
  }
  if (key.includes('cloud') || key.includes('overcast')) {
    return '☁️';
  }
  if (key.includes('clear') || key.includes('sunny')) {
    return key.includes('night') ? '🌙' : '☀️';
  }

  return '⛅';
}

/** 강수확률 칸 고정 이모지. */
export const RAIN_PROBABILITY_EMOJI = '🌧️';

/** 미세먼지 칸 고정 이모지. */
export const FINE_DUST_EMOJI = '🌫️';

// 홈 배경 떠다니는 아이콘 중 하나로 쓰는 날씨 오브젝트 이미지.
// assets/weather 폴더의 모든 png를 파일명으로 자동 수집합니다(아파트 아이콘과 동일 방식).
// → 파일명 규칙(sunny/partly_cloudy/cloudy/fog/rain/snow/thunderstorm.png)만 지키면
//   이미지를 추가하는 것만으로 코드 수정 없이 조건별로 반영됩니다.
const weatherContext = require.context('../../../assets/weather', false, /\.png$/);
const WEATHER_IMAGES: Record<string, number> = {};
weatherContext.keys().forEach((assetKey) => {
  const name = assetKey.replace(/^\.\//, '').replace(/\.png$/, ''); // "./sunny.png" → "sunny"
  WEATHER_IMAGES[name] = weatherContext(assetKey) as number;
});

/** iconKey/conditionCode → 날씨 이미지 파일명(확장자 제외). */
function weatherIconFileName(iconKey: string | null, conditionCode: string | null): string {
  const key = (iconKey ?? conditionCode ?? '').toLowerCase();
  if (key.includes('thunder') || key.includes('storm')) return 'thunderstorm';
  if (key.includes('snow')) return 'snow';
  if (key.includes('rain') || key.includes('shower') || key.includes('drizzle')) return 'rain';
  if (key.includes('fog') || key.includes('mist') || key.includes('haze')) return 'fog';
  if (key.includes('partly')) return 'partly_cloudy';
  if (key.includes('cloud') || key.includes('overcast')) return 'cloudy';
  return 'sunny'; // clear/sunny 및 기본값
}

/** iconKey/conditionCode → 현재 날씨에 맞는 오브젝트 이미지. 해당 파일이 아직 없으면 sunny로 폴백. */
export function resolveWeatherIcon(iconKey: string | null, conditionCode: string | null): number {
  const name = weatherIconFileName(iconKey, conditionCode);
  return WEATHER_IMAGES[name] ?? WEATHER_IMAGES.sunny!;
}
