export type CommunityBoardType = 'INFORMATION' | 'FREE';
export type CommunityPostSort = 'HOT' | 'LATEST';

export interface ApiEnvelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
}

export class CommunityApiError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'CommunityApiError';
    this.code = code;
  }
}

export interface CommunityPostAuthor {
  memberId: number | null;
  nickname: string;
  profileImageUrl: string | null;
  selectedCharacterId: string;
  authorType: string;
}

export interface CommunityPostListItem {
  postId: number;
  boardType: CommunityBoardType;
  title: string;
  contentPreview: string;
  author: CommunityPostAuthor;
  thumbnailUrl: string | null;
  isAutoReport: boolean;
  apartment: {
    apartmentId: number;
    name: string;
  } | null;
  report: {
    reportId: number;
    status: string;
    reportAvailable: boolean;
  } | null;
  viewCount: number;
  likeCount: number;
  commentCount: number;
  likedByMe: boolean;
  isMine: boolean;
  isHot: boolean;
  hotScore: number | null;
  hotRank: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface CommunityPostList {
  boardType: CommunityBoardType;
  sort: CommunityPostSort;
  keyword: string | null;
  content: CommunityPostListItem[];
  pageInfo: {
    size: number;
    nextCursor: string | null;
    hasNext: boolean;
  };
}

export interface CommunityPostDetail {
  postId: number;
  boardType: CommunityBoardType;
  title: string;
  content: string;
  status: string;
  originalAvailable: boolean;
  author: CommunityPostAuthor;
  isAutoReport: boolean;
  apartment: {
    apartmentId: number;
    name: string;
    address: string;
  } | null;
  report: {
    reportId: number;
    status: string;
    reportAvailable: boolean;
  } | null;
  attachments: {
    fileId: number;
    originalName: string | null;
    contentType: string | null;
    fileUrl: string | null;
    displayOrder: number;
    available: boolean;
    expiresAt: string | null;
  }[];
  viewCount: number;
  likeCount: number;
  commentCount: number;
  likedByMe: boolean;
  isMine: boolean;
  isHot: boolean;
  hotScore: number | null;
  hotRank: number | null;
  permissions: {
    canEdit: boolean;
    canDelete: boolean;
    canLike: boolean;
    canComment: boolean;
  };
  createdAt: string;
  updatedAt: string;
}

export interface CreateCommunityPostInput {
  boardType: CommunityBoardType;
  title: string;
  content: string;
  apartmentId?: number;
  fileIds?: number[];
}

export interface UpdateCommunityPostInput {
  boardType?: CommunityBoardType;
  title?: string;
  content?: string;
  apartmentId?: number | null;
  fileIds?: number[];
}

export interface CommunityPostWriteResult {
  postId: number;
  boardType: CommunityBoardType;
  title: string;
  content: string;
  status: string;
  author: CommunityPostAuthor;
  isAutoReport: boolean;
  apartment: {
    apartmentId: number;
    name: string;
  } | null;
  report: {
    reportId: number;
    status: string;
    reportAvailable: boolean;
  } | null;
  attachments: {
    fileId: number;
    originalName: string;
    contentType: string;
    fileUrl: string | null;
    displayOrder: number;
    expiresAt?: string | null;
  }[];
  viewCount: number;
  likeCount: number;
  commentCount: number;
  likedByMe: boolean;
  isMine: boolean;
  isHot: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CommunityPostDeleteResult {
  postId: number;
  deletedAt: string;
}

export interface CommunityPostLikeResult {
  postId: number;
  likedByMe: true;
  likeCount: number;
  commentCount: number;
  viewCount: number;
  isHot: boolean;
  hotScore: number | null;
  hotRank: number | null;
  likedAt: string;
}

export interface CommunityPostUnlikeResult {
  postId: number;
  likedByMe: false;
  likeCount: number;
  commentCount: number;
  viewCount: number;
  isHot: boolean;
  hotScore: number | null;
  hotRank: number | null;
  unlikedAt: string;
}

export interface CommunityCommentAuthor {
  memberId: number | null;
  nickname: string;
  profileImageUrl: string | null;
  selectedCharacterId: string;
}

export interface CommunityComment {
  commentId: number;
  content: string;
  author: CommunityCommentAuthor;
  isMine: boolean;
  isPostAuthor: boolean;
  canEdit: boolean;
  canDelete: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CommunityCommentList {
  postId: number;
  content: CommunityComment[];
  totalCount: number;
  nextCursor: number | null;
  hasNext: boolean;
}

export interface CommunityPostMetrics {
  commentCount: number;
  likeCount: number;
  viewCount: number;
  isHot: boolean;
  hotScore: number | null;
}

export interface CommunityCommentCreateResult {
  comment: CommunityComment;
  postMetrics: CommunityPostMetrics;
}
