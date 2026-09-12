export interface CommunityPostMetrics {
  commentCount: number;
  likeCount: number;
  viewCount: number;
  isHot: boolean;
  hotScore: number | null;
}

export interface CommunityCommentDeleteResult {
  commentId: number;
  postId: number;
  deletedAt: string;
  postAvailable: boolean;
  postMetrics: CommunityPostMetrics;
}
