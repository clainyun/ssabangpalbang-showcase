import {
  type InfiniteData,
  type QueryClient,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';
import { useRef } from 'react';

import { useAuthStore } from '@/store/authStore';

import type { CommunityCommentDeleteResult } from './commentDeleteTypes';
import { createCommunityComment } from './createCommunityComment';
import { deleteCommunityComment } from './deleteCommunityComment';
import {
  CommunityApiError,
  type CommunityComment,
  type CommunityCommentCreateResult,
  type CommunityCommentList,
  type CommunityPostDetail,
  type CommunityPostList,
  type CommunityPostMetrics,
} from './types';
import { updateCommunityComment } from './updateCommunityComment';
import { communityCommentsQueryRoot } from './useCommunityComments';
import { communityPostQueryKey } from './useCommunityPost';
import { communityPostsQueryRoot } from './useCommunityPosts';

type CommunityCommentPages = InfiniteData<CommunityCommentList, number | undefined>;

function updatePostMetrics(
  queryClient: QueryClient,
  sessionVersion: number,
  postId: number,
  metrics: CommunityPostMetrics,
) {
  queryClient.setQueryData<CommunityPostDetail>(
    communityPostQueryKey(sessionVersion, postId),
    (current) =>
      current === undefined
        ? current
        : {
            ...current,
            commentCount: metrics.commentCount,
            likeCount: metrics.likeCount,
            viewCount: metrics.viewCount,
            isHot: metrics.isHot,
            hotScore: metrics.hotScore,
            hotRank: null,
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
              post.postId === postId
                ? {
                    ...post,
                    commentCount: metrics.commentCount,
                    likeCount: metrics.likeCount,
                    viewCount: metrics.viewCount,
                    isHot: metrics.isHot,
                    hotScore: metrics.hotScore,
                    hotRank: null,
                  }
                : post,
            ),
          },
  );
}

function updateCommentPages(
  queryClient: QueryClient,
  sessionVersion: number,
  postId: number,
  updater: (current: CommunityCommentPages) => CommunityCommentPages,
) {
  queryClient.setQueriesData<CommunityCommentPages>(
    {
      queryKey: communityCommentsQueryRoot(sessionVersion, postId),
    },
    (current) => (current === undefined ? current : updater(current)),
  );
}

function addCreatedComment(
  current: CommunityCommentPages,
  result: CommunityCommentCreateResult,
): CommunityCommentPages {
  const alreadyCached = current.pages.some((page) =>
    page.content.some((comment) => comment.commentId === result.comment.commentId),
  );
  const pages = current.pages.map((page) => ({
    ...page,
    totalCount: result.postMetrics.commentCount,
  }));
  const lastPageIndex = pages.length - 1;
  const lastPage = pages[lastPageIndex];

  if (alreadyCached || lastPage === undefined) {
    return {
      ...current,
      pages,
    };
  }

  pages[lastPageIndex] = {
    ...lastPage,
    content: [...lastPage.content, result.comment],
  };
  return {
    ...current,
    pages,
  };
}

function replaceCachedComment(
  current: CommunityCommentPages,
  updatedComment: CommunityComment,
): CommunityCommentPages {
  return {
    ...current,
    pages: current.pages.map((page) => ({
      ...page,
      content: page.content.map((comment) =>
        comment.commentId === updatedComment.commentId ? updatedComment : comment,
      ),
    })),
  };
}

function removeCachedComment(
  current: CommunityCommentPages,
  commentId: number,
  totalCount: number,
): CommunityCommentPages {
  return {
    ...current,
    pages: current.pages.map((page) => ({
      ...page,
      content: page.content.filter((comment) => comment.commentId !== commentId),
      totalCount,
    })),
  };
}

export interface CreateCommunityCommentVariables {
  content: string;
  postId: number;
}

export function useCreateCommunityComment() {
  const queryClient = useQueryClient();
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return useMutation({
    mutationFn: ({ content, postId }: CreateCommunityCommentVariables) => {
      if (accessToken === null) {
        throw new Error('로그인이 필요합니다.');
      }
      return createCommunityComment(accessToken, postId, content, sessionVersion);
    },
    onSuccess: async (result, { postId }) => {
      updatePostMetrics(queryClient, sessionVersion, postId, result.postMetrics);
      updateCommentPages(queryClient, sessionVersion, postId, (current) =>
        addCreatedComment(current, result),
      );
      await Promise.all([
        queryClient.invalidateQueries({
          exact: true,
          queryKey: communityPostQueryKey(sessionVersion, postId),
        }),
        queryClient.invalidateQueries({
          queryKey: communityCommentsQueryRoot(sessionVersion, postId),
          refetchType: 'none',
        }),
        queryClient.invalidateQueries({
          queryKey: communityPostsQueryRoot(sessionVersion),
        }),
      ]);
    },
  });
}

export interface UpdateCommunityCommentVariables {
  commentId: number;
  content: string;
  postId: number;
}

export function useUpdateCommunityComment() {
  const queryClient = useQueryClient();
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);

  return useMutation({
    mutationFn: ({ commentId, content }: UpdateCommunityCommentVariables) => {
      if (accessToken === null) {
        throw new Error('로그인이 필요합니다.');
      }
      return updateCommunityComment(accessToken, commentId, content, sessionVersion);
    },
    onSuccess: async (result, { postId }) => {
      updateCommentPages(queryClient, sessionVersion, postId, (current) =>
        replaceCachedComment(current, result),
      );
      await queryClient.invalidateQueries({
        queryKey: communityCommentsQueryRoot(sessionVersion, postId),
        refetchType: 'none',
      });
    },
  });
}

export interface DeleteCommunityCommentVariables {
  commentId: number;
  postId: number;
}

export type DeleteCommunityCommentOutcome =
  | {
      status: 'deleted';
      result: CommunityCommentDeleteResult;
    }
  | {
      status: 'already-deleted';
      commentId: number;
      postId: number;
    };

export function useDeleteCommunityComment() {
  const queryClient = useQueryClient();
  const accessToken = useAuthStore((state) => state.accessToken);
  const sessionVersion = useAuthStore((state) => state.sessionVersion);
  const pendingByComment = useRef(new Map<string, Promise<DeleteCommunityCommentOutcome>>());
  const queueByPost = useRef(new Map<string, Promise<void>>());

  const invalidatePostDetail = (postId: number, refetchType: 'active' | 'none') =>
    queryClient.invalidateQueries({
      exact: true,
      queryKey: communityPostQueryKey(sessionVersion, postId),
      refetchType,
    });

  const refreshPostLists = () =>
    queryClient.invalidateQueries({
      queryKey: communityPostsQueryRoot(sessionVersion),
    });

  const refreshComments = (postId: number) =>
    queryClient.invalidateQueries({
      queryKey: communityCommentsQueryRoot(sessionVersion, postId),
    });

  const executeDelete = async ({
    commentId,
    postId,
  }: DeleteCommunityCommentVariables): Promise<DeleteCommunityCommentOutcome> => {
    if (accessToken === null) {
      throw new Error('로그인이 필요합니다.');
    }

    try {
      const result = await deleteCommunityComment(accessToken, commentId, sessionVersion);
      return { status: 'deleted', result };
    } catch (error) {
      if (error instanceof CommunityApiError && error.code === 'COMMENT_ALREADY_DELETED') {
        return {
          status: 'already-deleted',
          commentId,
          postId,
        };
      }
      throw error;
    }
  };

  const enqueueDelete = (
    variables: DeleteCommunityCommentVariables,
  ): Promise<DeleteCommunityCommentOutcome> => {
    const commentKey = `${sessionVersion}:${variables.commentId}`;
    const postKey = `${sessionVersion}:${variables.postId}`;
    const pending = pendingByComment.current.get(commentKey);
    if (pending !== undefined) {
      return pending;
    }

    const previous = queueByPost.current.get(postKey);
    const request = (previous ?? Promise.resolve())
      .catch(() => undefined)
      .then(() => executeDelete(variables));
    const queueTail = request.then(
      () => undefined,
      () => undefined,
    );

    pendingByComment.current.set(commentKey, request);
    queueByPost.current.set(postKey, queueTail);

    const cleanUp = () => {
      if (pendingByComment.current.get(commentKey) === request) {
        pendingByComment.current.delete(commentKey);
      }
      if (queueByPost.current.get(postKey) === queueTail) {
        queueByPost.current.delete(postKey);
      }
    };
    void request.then(cleanUp, cleanUp);

    return request;
  };

  return useMutation({
    mutationFn: enqueueDelete,
    onSuccess: async (outcome) => {
      if (outcome.status === 'already-deleted') {
        const detailQueryKey = communityPostQueryKey(sessionVersion, outcome.postId);
        const hasCachedDetail = queryClient.getQueryData(detailQueryKey) !== undefined;

        await Promise.all([
          hasCachedDetail ? invalidatePostDetail(outcome.postId, 'active') : Promise.resolve(),
          refreshComments(outcome.postId),
          refreshPostLists(),
        ]);
        return;
      }

      const { result } = outcome;
      const detailQueryKey = communityPostQueryKey(sessionVersion, result.postId);

      if (result.postAvailable) {
        updatePostMetrics(queryClient, sessionVersion, result.postId, result.postMetrics);
        updateCommentPages(queryClient, sessionVersion, result.postId, (current) =>
          removeCachedComment(current, result.commentId, result.postMetrics.commentCount),
        );
        await Promise.all([
          invalidatePostDetail(result.postId, 'active'),
          queryClient.invalidateQueries({
            queryKey: communityCommentsQueryRoot(sessionVersion, result.postId),
            refetchType: 'none',
          }),
          queryClient.invalidateQueries({
            queryKey: communityPostsQueryRoot(sessionVersion),
          }),
        ]);
        return;
      }

      queryClient.removeQueries({
        exact: true,
        queryKey: detailQueryKey,
      });
      queryClient.removeQueries({
        queryKey: communityCommentsQueryRoot(sessionVersion, result.postId),
      });
      await refreshPostLists();
    },
  });
}
