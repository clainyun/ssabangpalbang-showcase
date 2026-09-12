import { useMutation, useQueryClient, type UseMutationResult } from '@tanstack/react-query';

import { useAuthStore } from '@/store/authStore';
import { myPageQueryKeys } from '@/features/member/api/myPageQueryKeys';

import { createStudy } from './createStudy';
import type { CreateStudyInput, StudyCreateResult } from './types';

export function useCreateStudy(): UseMutationResult<StudyCreateResult, Error, CreateStudyInput> {
  const queryClient = useQueryClient();
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return useMutation({
    mutationFn: (input: CreateStudyInput) => {
      if (accessToken === null) {
        throw new Error('로그인이 필요합니다.');
      }

      return createStudy(input, accessToken, sessionVersion);
    },
    onSuccess: async (_, input) => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ['apartment', input.apartmentId, 'studies'],
          exact: true,
        }),
        queryClient.invalidateQueries({
          queryKey: myPageQueryKeys.profile(sessionVersion),
          exact: true,
        }),
        queryClient.invalidateQueries({ queryKey: myPageQueryKeys.studiesRoot }),
      ]);
    },
  });
}
