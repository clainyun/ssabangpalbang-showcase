import { useLocalSearchParams } from 'expo-router';

import { FieldVisitWaitingScreen } from '@/features/checklist/FieldVisitWaitingScreen';

export default function FieldVisitWaitingRoute() {
  const { studyId: rawStudyId } = useLocalSearchParams<{ studyId: string }>();
  const studyId = Number(rawStudyId);

  return <FieldVisitWaitingScreen studyId={studyId} />;
}
