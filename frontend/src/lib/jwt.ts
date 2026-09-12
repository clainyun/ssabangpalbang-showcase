const BASE64_ALPHABET =
  'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

// Hermes에 atob/Buffer가 항상 있다는 보장이 없어서 base64url을 직접 디코딩합니다.
function decodeBase64Url(segment: string): string {
  const base64 = segment.replace(/-/g, '+').replace(/_/g, '/');
  let bits = '';
  for (const char of base64) {
    const index = BASE64_ALPHABET.indexOf(char);
    if (index === -1) continue;
    bits += index.toString(2).padStart(6, '0');
  }

  let output = '';
  for (let i = 0; i + 8 <= bits.length; i += 8) {
    output += String.fromCharCode(parseInt(bits.slice(i, i + 8), 2));
  }
  return output;
}

/**
 * Access Token은 서명 검증까지 백엔드가 이미 하므로, 여기서는 화면에 필요한
 * memberId(sub claim)만 꺼내 씁니다 — 서명은 검증하지 않으니 인가 판단에는 쓰지 마세요.
 */
export function decodeMemberIdFromAccessToken(accessToken: string): number | null {
  const payloadSegment = accessToken.split('.')[1];
  if (!payloadSegment) {
    return null;
  }

  try {
    const json = decodeURIComponent(
      decodeBase64Url(payloadSegment)
        .split('')
        .map((char) => `%${char.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .join(''),
    );
    const payload = JSON.parse(json) as { sub?: string };
    const memberId = Number(payload.sub);
    return Number.isFinite(memberId) ? memberId : null;
  } catch {
    return null;
  }
}
