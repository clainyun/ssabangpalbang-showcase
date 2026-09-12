import { type QueryClient, useMutation, useQueryClient } from '@tanstack/react-query';

import { useAuthStore } from '@/store/authStore';

import { createCommunityPost } from './createCommunityPost';
import { deleteCommunityPost } from './deleteCommunityPost';
import { likeCommunityPost } from './likeCommunityPost';
import type {
  CommunityApiError,
  CommunityPostDetail,
  CommunityPostLikeResult,
  CommunityPostList,
  CommunityPostUnlikeResult,
  CreateCommunityPostInput,
  UpdateCommunityPostInput,
} from './types';
import { unlikeCommunityPost } from './unlikeCommunityPost';
import { updateCommunityPost } from './updateCommunityPost';
import { communityCommentsQueryRoot } from './useCommunityComments';
import { communityPostQueryKey } from './useCommunityPost';
import { communityPostsQueryRoot } from './useCommunityPosts';

function updatePostInteractionCaches(
  queryClient: QueryClient,
  sessionVersion: number,
  result: CommunityPostLikeResult | CommunityPostUnlikeResult,
) {
  queryClient.setQueryData<CommunityPostDetail>(
    communityPostQueryKey(sessionVersion, result.postId),
    (current) =>
      current === undefined
        ? current
        : {
            ...current,
            likedByMe: result.likedByMe,
            likeCount: result.likeCount,
            commentCount: result.commentCount,
            viewCount: result.viewCount,
            isHot: result.isHot,
            hotScore: result.hotScore,
            hotRank: result.hotRank,
          },
  );
  queryClient.setQueriesData<CommunityPostList>(
    {
      queryKey: communityPostsQueryRoot(sessionVersion),
    },
    (current) =>
      current === undefined
        ? current
        : {
            ...current,
            content: current.content.map((post) =>
              post.postId === result.postId
                ? {
                    ...post,
                    likedByMe: result.likedByMe,
                    likeCount: result.likeCount,
                    commentCount: result.commentCount,
                    viewCount: result.viewCount,
                    isHot: result.isHot,
                    hotScore: result.hotScore,
                    hotRank: result.hotRank,
                  }
                : post,
            ),
          },
  );
}

function useCommunityMutationSession() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  const requireAccessToken = () => {
    if (accessToken === null) {
      throw new Error('로그인이 필요합니다.');
    }
    return accessToken;
  };

  return {
    requireAccessToken,
    sessionVersion,
  };
}

export function useCreateCommunityPost() {
  const queryClient = useQueryClient();
  const { requireAccessToken, sessionVersion } = useCommunityMutationSession();

  return useMutation({
    mutationFn: (input: CreateCommunityPostInput) =>
      createCommunityPost(requireAccessToken(), input, sessionVersion),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: communityPostsQueryRoot(sessionVersion),
      });
    },
  });
}

export function useUpdateCommunityPost(postId: number | undefined) {
  const queryClient = useQueryClient();
  const { requireAccessToken, sessionVersion } = useCommunityMutationSession();

  return useMutation({
    mutationFn: (input: UpdateCommunityPostInput) => {
      if (postId === undefined) {
        throw new Error('게시글 번호가 올바르지 않습니다.');
      }

      return updateCommunityPost(requireAccessToken(), postId, input, sessionVersion);
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: communityPostsQueryRoot(sessionVersion),
        }),
        queryClient.invalidateQueries({
          queryKey: communityPostQueryKey(sessionVersion, postId),
        }),
      ]);
    },
  });
}

export function useDeleteCommunityPost() {
  const queryClient = useQueryClient();
  const { requireAccessToken, sessionVersion } = useCommunityMutationSession();

  return useMutation({
    mutationFn: (postId: number) =>
      deleteCommunityPost(requireAccessToken(), postId, sessionVersion),
    onSuccess: async (_result, postId) => {
      queryClient.removeQueries({
        exact: true,
        queryKey: communityPostQueryKey(sessionVersion, postId),
      });
      await queryClient.invalidateQueries({
        queryKey: communityPostsQueryRoot(sessionVersion),
      });
    },
  });
}

export function useLikeCommunityPost() {
  const queryClient = useQueryClient();
  const { requireAccessToken, sessionVersion } = useCommunityMutationSession();

  return useMutation({
    mutationFn: (targetPostId: number) =>
      likeCommunityPost(requireAccessToken(), targetPostId, sessionVersion),
    onSuccess: async (result) => {
      updatePostInteractionCaches(queryClient, sessionVersion, result);
      await queryClient.invalidateQueries({
        queryKey: communityPostsQueryRoot(sessionVersion),
      });
    },
    onError: (error: CommunityApiError | Error, targetPostId: number) => {
      if ('code' in error && error.code === 'POST_NOT_FOUND') {
        queryClient.removeQueries({
          exact: true,
          queryKey: communityPostQueryKey(sessionVersion, targetPostId),
        });
        queryClient.removeQueries({
          queryKey: communityCommentsQueryRoot(sessionVersion, targetPostId),
        });
        void queryClient.invalidateQueries({
          queryKey: communityPostsQueryRoot(sessionVersion),
        });
      }
    },
  });
}

export function useUnlikeCommunityPost() {
  const queryClient = useQueryClient();
  const { requireAccessToken, sessionVersion } = useCommunityMutationSession();

  return useMutation({
    mutationFn: (targetPostId: number) =>
      unlikeCommunityPost(requireAccessToken(), targetPostId, sessionVersion),
    onSuccess: async (result) => {
      updatePostInteractionCaches(queryClient, sessionVersion, result);
      await queryClient.invalidateQueries({
        queryKey: communityPostsQueryRoot(sessionVersion),
      });
    },
    onError: (error: CommunityApiError | Error, targetPostId: number) => {
      if ('code' in error && error.code === 'POST_NOT_FOUND') {
        queryClient.removeQueries({
          exact: true,
          queryKey: communityPostQueryKey(sessionVersion, targetPostId),
        });
        queryClient.removeQueries({
          queryKey: communityCommentsQueryRoot(sessionVersion, targetPostId),
        });
        void queryClient.invalidateQueries({
          queryKey: communityPostsQueryRoot(sessionVersion),
        });
      }
    },
  });
}
