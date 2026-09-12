import type { Href } from 'expo-router';
import { create } from 'zustand';

const HOME_ROUTE = '/(app)/(tabs)/home' as const;
const REPORT_PATH_PATTERN = /^(?:\/\(app\))?\/open\/report\/([1-9]\d*)$/;

interface PendingAppLinkState {
  pendingRoute: Href | null;
  capturePath: (pathname: string) => boolean;
  captureRoute: (href: Href) => void;
  clear: () => void;
  takeRoute: () => Href | null;
}

export function parsePendingReportId(pathname: string): string | null {
  const match = REPORT_PATH_PATTERN.exec(pathname);
  if (!match) return null;

  const reportId = Number(match[1]);
  if (!Number.isSafeInteger(reportId) || reportId < 1) return null;

  return String(reportId);
}

export const usePendingAppLinkStore = create<PendingAppLinkState>((set, get) => ({
  pendingRoute: null,

  capturePath: (pathname) => {
    const reportId = parsePendingReportId(pathname);
    if (!reportId) return false;

    set({
      pendingRoute: {
        pathname: '/(app)/report/[reportId]',
        params: { reportId },
      },
    });
    return true;
  },

  captureRoute: (href) => set({ pendingRoute: href }),

  clear: () => set({ pendingRoute: null }),

  takeRoute: () => {
    const href = get().pendingRoute;
    if (href) set({ pendingRoute: null });
    return href;
  },
}));

export function takePostAuthRoute(): Href {
  return usePendingAppLinkStore.getState().takeRoute() ?? HOME_ROUTE;
}
