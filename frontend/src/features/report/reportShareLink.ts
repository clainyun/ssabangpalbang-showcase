const REPORT_SHARE_ORIGIN = 'https://portfolio.example.com';

export function createReportShareUrl(reportId: number): string {
  if (!Number.isSafeInteger(reportId) || reportId < 1) {
    throw new RangeError('A report share URL requires a positive safe integer report ID.');
  }

  return `${REPORT_SHARE_ORIGIN}/report/${reportId}`;
}
