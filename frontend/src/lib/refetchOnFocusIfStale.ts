import type { QueryClient, QueryKey } from '@tanstack/react-query';

export const FOCUS_REFRESH_INTERVAL_MS = 20_000;

/**
 * 탭·스택 화면은 navigation focus가 바뀌어도 mounted 상태로 남을 수 있습니다.
 * 마지막 성공 조회가 충분히 오래됐을 때만 활성 쿼리를 다시 불러와, 포커스마다
 * 반복되는 재조회(불필요한 네트워크, presigned URL 재발급으로 인한 이미지 깜빡임)를
 * 줄입니다.
 */
export function refetchOnFocusIfStale(
  queryClient: QueryClient,
  queryKey: QueryKey,
  now = Date.now(),
): void {
  const state = queryClient.getQueryState(queryKey);
  if (!state || state.fetchStatus === 'fetching') return;

  const age = now - state.dataUpdatedAt;
  if (state.dataUpdatedAt > 0 && age >= 0 && age < FOCUS_REFRESH_INTERVAL_MS) {
    return;
  }

  void queryClient.refetchQueries({ queryKey, exact: true, type: 'active' });
}

/**
 * 상태 변화 반영이 깜빡임보다 중요한 화면(스터디 상세 등)을 위해, 포커스마다 게이트 없이
 * 곧바로 다시 조회합니다. 이미 조회 중이면 중복 요청을 만들지 않습니다.
 */
export function refetchOnFocusNow(queryClient: QueryClient, queryKey: QueryKey): void {
  const state = queryClient.getQueryState(queryKey);
  if (state?.fetchStatus === 'fetching') return;

  void queryClient.refetchQueries({ queryKey, exact: true, type: 'active' });
}
