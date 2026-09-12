import { createHash, timingSafeEqual } from 'node:crypto';

export function requireConfiguredToken(token) {
  if (
    typeof token !== 'string' ||
    !/^[\x21-\x7e]{32,}$/u.test(token)
  ) {
    throw new Error(
      'MEDIA_GATEWAY_INTERNAL_TOKEN must be at least 32 printable ASCII characters',
    );
  }
  return token;
}

export function isAuthorized(authorization, expectedToken) {
  if (typeof authorization !== 'string') {
    return false;
  }

  const match = /^Bearer ([^\s]+)$/.exec(authorization);
  if (match === null) {
    return false;
  }

  const actual = createHash('sha256').update(match[1], 'utf8').digest();
  const expected = createHash('sha256').update(expectedToken, 'utf8').digest();
  return timingSafeEqual(actual, expected);
}
