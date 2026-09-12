import { useEffect, useState } from 'react';

export type AvailabilityStatus =
  | 'idle' // 입력이 비었거나 형식이 유효하지 않아 조회하지 않는 상태
  | 'checking' // 디바운스 후 서버 조회 중
  | 'available' // 사용 가능(미중복)
  | 'taken' // 이미 사용 중(중복)
  | 'error'; // 네트워크 등으로 조회 실패(입력은 계속 가능, 재시도 가능)

interface Options {
  /** 형식이 유효할 때만 true. false면 조회하지 않고 idle로 둡니다. */
  enabled: boolean;
  /**
   * 실제 서버 조회. available(미중복)=true 를 반환하도록 구현합니다.
   * 매 렌더 안정적인 참조가 되도록 모듈 수준 함수를 그대로 넘겨야 합니다.
   */
  check: (value: string, signal: AbortSignal) => Promise<boolean>;
  /** 입력이 멈춘 뒤 조회까지의 디바운스 지연(ms). */
  delayMs?: number;
}

/**
 * 이메일/닉네임 실시간 중복확인용 훅.
 * - 디바운스: 마지막 입력 이후 delayMs 동안 추가 입력이 없을 때만 조회합니다.
 * - 경합 방지: 값이 바뀌면 이전 타이머를 취소하고 진행 중인 요청을 abort 하며,
 *   cleanup 플래그로 늦게 도착한(오래된) 응답의 setState 를 무시합니다("마지막 입력 우선").
 * - 조회 실패는 입력을 막지 않고 'error' 로만 표시합니다(재입력 시 자동 재시도).
 */
export function useAvailabilityCheck(
  value: string,
  { enabled, check, delayMs = 450 }: Options,
): AvailabilityStatus {
  const [status, setStatus] = useState<AvailabilityStatus>('idle');

  useEffect(() => {
    let cancelled = false;

    // 조회 대상이 아니면 이전에 남은 상태(available/taken 등)를 idle로 되돌립니다.
    // setState는 effect 본문에서 동기로 호출하지 않고 콜백 안에서만 호출합니다.
    if (!enabled || !value) {
      const idleTimer = setTimeout(() => {
        if (!cancelled) setStatus('idle');
      }, 0);
      return () => {
        cancelled = true;
        clearTimeout(idleTimer);
      };
    }

    const controller = new AbortController();
    const timer = setTimeout(() => {
      if (cancelled) return;
      setStatus('checking');
      check(value, controller.signal)
        .then((available) => {
          if (cancelled) return;
          setStatus(available ? 'available' : 'taken');
        })
        .catch((err: unknown) => {
          if (cancelled) return;
          // abort(취소)로 인한 실패는 상태를 바꾸지 않습니다.
          if ((err as { name?: string })?.name === 'AbortError') return;
          setStatus('error');
        });
    }, delayMs);

    return () => {
      cancelled = true;
      clearTimeout(timer);
      controller.abort();
    };
  }, [value, enabled, delayMs, check]);

  return status;
}
