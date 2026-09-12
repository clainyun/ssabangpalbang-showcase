import * as Crypto from 'expo-crypto';
import * as Notifications from 'expo-notifications';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

import {
  authenticatedFetch,
  AuthSessionChangedError,
  rethrowSessionFailure,
} from '@/lib/authenticatedFetch';
import { explainBeforeRequest, showPermanentlyDeniedAlert } from '@/lib/permissions';
import { useAuthStore } from '@/store/authStore';

const DEVICE_ID_KEY = 'notification.device-id';
const ANDROID_NOTIFICATION_CHANNEL_ID = 'ssabangpalbang-alerts';
let deviceIdPromise: Promise<string> | null = null;
let suspendedSessionVersion: number | null = null;
const activeSyncOperations = new Map<number, Set<Promise<string | null>>>();
const inFlightRegistrationRequests = new Map<
  string,
  Promise<FcmTokenRegistrationResponse>
>();

interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

interface FcmTokenRegistrationResponse {
  deviceId: string;
  registered: boolean;
}

interface FcmTokenDeleteResponse {
  deviceId: string;
  registered: boolean;
  deletedAt: string;
}

interface FcmTestPushResponse {
  deviceId: string;
  scheduledAt: string;
}

function isCurrentSession(sessionVersion: number): boolean {
  return useAuthStore.getState().sessionVersion === sessionVersion;
}

function isRegistrationSuspended(sessionVersion: number): boolean {
  return suspendedSessionVersion === sessionVersion;
}

function fcmTokenFrom(devicePushToken: Notifications.DevicePushToken): string | null {
  if (devicePushToken.type !== 'android' || typeof devicePushToken.data !== 'string') {
    return null;
  }

  const fcmToken = devicePushToken.data.trim();
  return fcmToken.length > 0 ? fcmToken : null;
}

async function configureAndroidChannel() {
  if (Platform.OS !== 'android') {
    return;
  }

  await Notifications.setNotificationChannelAsync(ANDROID_NOTIFICATION_CHANNEL_ID, {
    name: '싸방팔방 알림',
    importance: Notifications.AndroidImportance.HIGH,
  });
}

async function isAndroidNotificationChannelEnabled(): Promise<boolean> {
  if (Platform.OS !== 'android') {
    return true;
  }

  const channel = await Notifications.getNotificationChannelAsync(
    ANDROID_NOTIFICATION_CHANNEL_ID,
  );
  return channel !== null && channel.importance !== Notifications.AndroidImportance.NONE;
}

export function getOrCreateDeviceId(): Promise<string> {
  if (!deviceIdPromise) {
    deviceIdPromise = (async () => {
      const savedDeviceId = await SecureStore.getItemAsync(DEVICE_ID_KEY);

      if (savedDeviceId) {
        return savedDeviceId;
      }

      const deviceId = Crypto.randomUUID();
      await SecureStore.setItemAsync(DEVICE_ID_KEY, deviceId);
      return deviceId;
    })().catch((error) => {
      deviceIdPromise = null;
      throw error;
    });
  }

  return deviceIdPromise;
}

export async function requestNotificationPermission(): Promise<boolean> {
  await configureAndroidChannel();

  const current = await Notifications.getPermissionsAsync();
  if (current.granted) {
    return isAndroidNotificationChannelEnabled();
  }

  const shouldRequest = await explainBeforeRequest('notification');
  if (!shouldRequest) {
    return false;
  }

  const requested = await Notifications.requestPermissionsAsync();
  if (!requested.granted) {
    if (!requested.canAskAgain) {
      showPermanentlyDeniedAlert('notification');
    }
    return false;
  }
  return isAndroidNotificationChannelEnabled();
}

export async function isNotificationPermissionGranted(): Promise<boolean> {
  const current = await Notifications.getPermissionsAsync();
  if (!current.granted) {
    return false;
  }
  return isAndroidNotificationChannelEnabled();
}

export async function issueAndroidFcmToken(): Promise<string | null> {
  if (Platform.OS !== 'android') {
    console.info('[Notifications] Android FCM token issuance skipped: unsupported platform.');
    return null;
  }

  console.info('[Notifications] Starting Android FCM token issuance.');

  try {
    await configureAndroidChannel();
    const devicePushToken = await Notifications.getDevicePushTokenAsync();
    const fcmToken = fcmTokenFrom(devicePushToken);

    if (fcmToken) {
      console.info('[Notifications] Android FCM token issued.');
    } else {
      console.warn('[Notifications] Android FCM token issuance returned no token.');
    }

    return fcmToken;
  } catch (error) {
    console.warn('[Notifications] Android FCM token issuance failed.', {
      errorType: error instanceof Error ? error.name : 'unknown',
    });
    throw error;
  }
}

export function registerFcmToken(
  accessToken: string,
  deviceId: string,
  fcmToken: string,
  expectedSessionVersion: number = useAuthStore.getState().sessionVersion,
): Promise<FcmTokenRegistrationResponse> {
  const operationKey =
    `${expectedSessionVersion}\u0000${deviceId}\u0000${fcmToken}`;
  const existingOperation = inFlightRegistrationRequests.get(operationKey);
  if (existingOperation) {
    console.info('[Notifications] In-flight Android FCM token PUT shared.');
    return existingOperation;
  }

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };

  const operation = (async () => {
    console.info('[Notifications] Starting FCM token server registration.');
    let responseStatus: number | undefined;
    let responseSuccess: boolean | undefined;
    let responseRegistered: boolean | undefined;

    try {
      const response = await authenticatedFetch(
        `/api/v1/members/me/devices/${encodeURIComponent(deviceId)}/fcm-token`,
        {
          method: 'PUT',
          headers,
          body: JSON.stringify({ fcmToken }),
        },
        {
          expectedSessionVersion,
          fallbackAccessToken: accessToken,
        },
      );
      responseStatus = response.status;

      let payload: ApiResponse<FcmTokenRegistrationResponse> | null = null;
      try {
        payload = (await response.json()) as ApiResponse<FcmTokenRegistrationResponse>;
      } catch {
        throw new Error('FCM 토큰 등록 응답을 확인하지 못했습니다.');
      }
      responseSuccess = payload.success;
      responseRegistered = payload.data?.registered;

      if (!response.ok || !payload.success || !payload.data?.registered) {
        throw new Error(payload.message || 'FCM 토큰 등록에 실패했습니다.');
      }

      console.info('[Notifications] FCM token server registration succeeded.', {
        status: responseStatus,
      });
      return payload.data;
    } catch (error) {
      console.warn('[Notifications] FCM token server registration failed.', {
        status: responseStatus,
        success: responseSuccess,
        registered: responseRegistered,
        errorType: error instanceof Error ? error.name : 'unknown',
      });
      throw error;
    }
  })();

  let trackedOperation: Promise<FcmTokenRegistrationResponse>;
  trackedOperation = operation.finally(() => {
    if (inFlightRegistrationRequests.get(operationKey) === trackedOperation) {
      inFlightRegistrationRequests.delete(operationKey);
    }
  });
  inFlightRegistrationRequests.set(operationKey, trackedOperation);
  return trackedOperation;
}

export async function unregisterFcmToken(
  accessToken: string,
  deviceId: string,
  expectedSessionVersion: number = useAuthStore.getState().sessionVersion,
): Promise<FcmTokenDeleteResponse> {
  const response = await authenticatedFetch(
    `/api/v1/members/me/devices/${encodeURIComponent(deviceId)}/fcm-token`,
    { method: 'DELETE' },
    {
      expectedSessionVersion,
      fallbackAccessToken: accessToken,
    },
  );
  const payload = (await response.json().catch(() => null)) as ApiResponse<
    FcmTokenDeleteResponse
  > | null;

  if (!response.ok || !payload?.success || !payload.data) {
    throw new Error(payload?.message || 'FCM 토큰 연결 해제에 실패했습니다.');
  }

  return payload.data;
}

function testPushFailureMessage(code: string | undefined): string {
  switch (code) {
    case 'MEMBER_FCM_TOKEN_NOT_FOUND':
      return '이 기기에 알림 토큰이 등록되지 않았습니다. 잠시 후 다시 시도해 주세요.';
    case 'FCM_PUSH_NOT_CONFIGURED':
      return '알림 발송 설정이 아직 완료되지 않았습니다. 관리자에게 문의해 주세요.';
    case 'FCM_PUSH_DELIVERY_FAILED':
      return '알림 전송에 실패했습니다. 잠시 후 다시 시도해 주세요.';
    case 'FCM_PUSH_SCHEDULING_FAILED':
      return '알림 예약을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';
    case 'AUTH_ACCESS_TOKEN_INVALID':
    case 'AUTH_ACCESS_TOKEN_EXPIRED':
      return '로그인이 만료되었습니다. 다시 로그인해 주세요.';
    default:
      return '알림 전송을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.';
  }
}

export async function requestFcmTestPush(accessToken: string): Promise<FcmTestPushResponse> {
  const sessionVersion = useAuthStore.getState().sessionVersion;
  let permissionGranted = false;
  try {
    permissionGranted = await requestNotificationPermission();
  } catch {
    throw new Error('알림 권한을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }
  if (!permissionGranted) {
    throw new Error('알림 권한을 허용한 뒤 다시 시도해 주세요.');
  }

  let channelEnabled = false;
  try {
    channelEnabled = await isAndroidNotificationChannelEnabled();
  } catch {
    throw new Error('알림 채널 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }
  if (!channelEnabled) {
    throw new Error('기기 설정에서 싸방팔방 알림 채널을 허용한 뒤 다시 시도해 주세요.');
  }

  let fcmToken: string | null = null;
  try {
    fcmToken = await issueAndroidFcmToken();
  } catch {
    throw new Error('이 기기에서 알림 토큰을 발급하지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }
  if (!fcmToken) {
    throw new Error('이 기기에서 알림 토큰을 발급하지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }

  let deviceId: string;
  try {
    deviceId = await getOrCreateDeviceId();
  } catch {
    throw new Error('기기 알림 정보를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }
  try {
    const syncedToken = await queueFcmTokenSync(
      accessToken,
      fcmToken,
      () => true,
      sessionVersion,
    );
    if (!syncedToken) {
      throw new AuthSessionChangedError();
    }
  } catch (error) {
    rethrowSessionFailure(error);
    throw new Error('알림 토큰을 서버에 등록하지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }

  let response: Response;
  try {
    response = await authenticatedFetch(
      `/api/v1/members/me/devices/${encodeURIComponent(deviceId)}/test-fcm-push`,
      {
        method: 'POST',
      },
      {
        expectedSessionVersion: sessionVersion,
        fallbackAccessToken: accessToken,
      },
    );
  } catch (error) {
    rethrowSessionFailure(error);
    throw new Error('알림 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }

  let payload: ApiResponse<FcmTestPushResponse> | null = null;
  try {
    payload = (await response.json()) as ApiResponse<FcmTestPushResponse>;
  } catch {
    // A network proxy may return a non-JSON response. Present the same safe UI message.
  }

  if (!response.ok || !payload?.success || !payload.data) {
    throw new Error(testPushFailureMessage(payload?.code));
  }

  return payload.data;
}

export function suspendFcmTokenRegistration(
  sessionVersion: number = useAuthStore.getState().sessionVersion,
): void {
  suspendedSessionVersion = sessionVersion;
}

export function resumeFcmTokenRegistration(
  sessionVersion: number = useAuthStore.getState().sessionVersion,
): boolean {
  if (suspendedSessionVersion === sessionVersion) {
    return false;
  }
  suspendedSessionVersion = null;
  return true;
}

function shouldRunRegistration(
  sessionVersion: number,
  shouldContinue: () => boolean,
): boolean {
  return (
    isCurrentSession(sessionVersion) &&
    !isRegistrationSuspended(sessionVersion) &&
    shouldContinue()
  );
}

function trackSyncOperation(
  sessionVersion: number,
  operation: Promise<string | null>,
): Promise<string | null> {
  const sessionOperations =
    activeSyncOperations.get(sessionVersion) ?? new Set<Promise<string | null>>();
  activeSyncOperations.set(sessionVersion, sessionOperations);

  let trackedOperation: Promise<string | null>;
  trackedOperation = operation.finally(() => {
    sessionOperations.delete(trackedOperation);
    if (sessionOperations.size === 0) {
      activeSyncOperations.delete(sessionVersion);
    }
  });
  sessionOperations.add(trackedOperation);
  return trackedOperation;
}

export function queueFcmTokenSync(
  accessToken: string,
  token?: string,
  shouldContinue: () => boolean = () => true,
  sessionVersion: number = useAuthStore.getState().sessionVersion,
): Promise<string | null> {
  const requestedToken = token?.trim() || undefined;

  if (!shouldRunRegistration(sessionVersion, shouldContinue)) {
    return Promise.resolve(null);
  }

  console.info(
    requestedToken
      ? '[Notifications] Starting FCM token re-registration.'
      : '[Notifications] Starting FCM token registration flow.',
  );
  return trackSyncOperation(
    sessionVersion,
    syncFcmToken(
      accessToken,
      requestedToken,
      shouldContinue,
      sessionVersion,
    ),
  );
}

async function getExistingDeviceId(): Promise<string | null> {
  if (deviceIdPromise) {
    return deviceIdPromise;
  }
  return SecureStore.getItemAsync(DEVICE_ID_KEY);
}

export function queueFcmTokenUnregister(
  accessToken: string,
  sessionVersion: number = useAuthStore.getState().sessionVersion,
): Promise<FcmTokenDeleteResponse | null> {
  suspendFcmTokenRegistration(sessionVersion);

  return (async () => {
    const pendingOperations = [
      ...(activeSyncOperations.get(sessionVersion) ?? []),
    ];
    await Promise.allSettled(pendingOperations);
    if (!isCurrentSession(sessionVersion)) {
      throw new AuthSessionChangedError();
    }

    const deviceId = await getExistingDeviceId();
    if (!deviceId) {
      return null;
    }
    return unregisterFcmToken(
      accessToken,
      deviceId,
      sessionVersion,
    );
  })();
}

export async function syncFcmToken(
  accessToken: string,
  token?: string,
  shouldContinue: () => boolean = () => true,
  sessionVersion: number = useAuthStore.getState().sessionVersion,
): Promise<string | null> {
  if (!shouldRunRegistration(sessionVersion, shouldContinue)) {
    return null;
  }

  const notificationPermissionGranted = await requestNotificationPermission();
  if (!shouldRunRegistration(sessionVersion, shouldContinue)) {
    return null;
  }
  if (!notificationPermissionGranted) {
    // 운영체제 알림 권한과 FCM 토큰 등록은 별개입니다. 서버에는 토큰을 계속 보관합니다.
    console.info('[Notifications] Permission denied; registering the FCM token anyway.');
  }

  const fcmToken = token ?? (await issueAndroidFcmToken());
  if (!shouldRunRegistration(sessionVersion, shouldContinue)) {
    return null;
  }
  if (!fcmToken) {
    console.warn('[Notifications] Android FCM token was not issued.');
    return null;
  }

  if (token) {
    console.info('[Notifications] Re-registering refreshed Android FCM token.');
  }

  const deviceId = await getOrCreateDeviceId();
  if (!shouldRunRegistration(sessionVersion, shouldContinue)) {
    return null;
  }
  await registerFcmToken(
    accessToken,
    deviceId,
    fcmToken,
    sessionVersion,
  );
  console.info('[Notifications] FCM token sync completed.');
  return fcmToken;
}
