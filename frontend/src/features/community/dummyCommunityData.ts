/**
 * 🟡 커뮤니티 리디자인 확인용 임시 더미 데이터.
 *
 * 실제 API 연동이 끝나면 아래 3곳만 제거하면 됩니다:
 *   1) 이 파일
 *   2) community.tsx 의 DUMMY_POSTS 파생(목록 카드 소스)
 *   3) post/[id].tsx 의 isDummyPostId 분기(상세/댓글 fallback)
 *
 * postId는 음수(-1 ~)로, 실제 백엔드 id(양수)와 절대 겹치지 않게 합니다.
 */

import type { CommunityBoardType } from './api/types';

export interface DummyPost {
  postId: number;
  boardType: CommunityBoardType; // 'FREE' | 'INFORMATION'
  title: string;
  /** 목록 카드 한 줄 설명. */
  summary: string;
  /** 상세 본문(문단은 \n\n, 줄바꿈은 \n). */
  content: string;
  authorNickname: string;
  /** 상세 상단 날짜 표시용 ISO. */
  createdAt: string;
  /** 목록 카드 상대시간 라벨(더미 고정값). */
  postedLabel: string;
  viewCount: number;
  likeCount: number;
  commentCount: number;
  isLiked: boolean;
  isHot: boolean;
  hotRank: number | null;
  /** 대표/첨부 이미지 URL들(0장 = 이미지 없음). */
  images: string[];
  connectedApartment: { apartmentId: number; name: string } | null;
  /** 이미지가 없을 때 목록 컬러 카드 배경색. */
  bgColor?: string;
}

export interface DummyComment {
  commentId: number;
  authorNickname: string;
  content: string;
  /** 상대시간 라벨(더미 고정값). 실데이터는 createdAt에서 포맷. */
  timeLabel: string;
}

export const DUMMY_POSTS: DummyPost[] = [
  {
    postId: -1,
    boardType: 'INFORMATION',
    title: '서울숲 아침 산책길을 찾았어요',
    summary: '출근 전 걷기 좋은 코스 공유해요.',
    content:
      '평일 아침 7시, 서울숲 안쪽 산책로를 한 바퀴 걸어봤습니다.\n사람이 몰리는 큰길 대신 물가를 따라 도는 길이 훨씬 여유로웠어요.\n\n중간중간 벤치와 정수기가 있어서 잠깐 쉬어가기도 좋았고, 강아지 산책하는 이웃도 많았습니다.\n\n다음엔 저녁 시간대 조명과 사람 밀도도 확인해볼 생각이에요.',
    authorNickname: '모닝임장러',
    createdAt: '2026-08-06T07:20:00+09:00',
    postedLabel: '18분 전',
    viewCount: 842,
    likeCount: 126,
    commentCount: 38,
    isLiked: false,
    isHot: true,
    hotRank: 1,
    images: ['https://loremflickr.com/900/1100/park,river'],
    connectedApartment: { apartmentId: 101, name: '서울숲 트리마제' },
  },
  {
    postId: -2,
    boardType: 'FREE',
    title: '성수동 골목 산책 코스',
    summary: '퇴근길에 걷기 좋은 길 공유해요.',
    content:
      '성수동은 골목마다 분위기가 달라서 걷는 재미가 있어요.\n카페 거리에서 시작해 방직공장 리모델링 구역까지 이어지는 코스를 추천합니다.\n\n주말 낮에는 사람이 많으니 평일 저녁이 산책하기엔 더 좋았어요.',
    authorNickname: '걷는사람',
    createdAt: '2026-08-05T19:10:00+09:00',
    postedLabel: '2시간 전',
    viewCount: 231,
    likeCount: 41,
    commentCount: 19,
    isLiked: true,
    isHot: false,
    hotRank: null,
    images: ['https://loremflickr.com/700/800/alley,seoul'],
    connectedApartment: null,
  },
  {
    postId: -3,
    boardType: 'INFORMATION',
    title: '역 출구별 단지 소요시간',
    summary: '2번 출구가 제일 가까워요.',
    content:
      '지하철역에서 단지까지 출구별로 실제 걸어본 시간을 정리했습니다.\n1번 출구는 8분, 2번 출구는 5분, 3번 출구는 신호가 많아 10분 정도 걸렸어요.\n\n짐이 많은 날은 2번 출구가 확실히 편합니다.',
    authorNickname: '출근러',
    createdAt: '2026-08-05T08:30:00+09:00',
    postedLabel: '어제',
    viewCount: 512,
    likeCount: 73,
    commentCount: 27,
    isLiked: false,
    isHot: true,
    hotRank: 2,
    images: ['https://loremflickr.com/700/650/subway,station'],
    connectedApartment: { apartmentId: 102, name: '옥수 파크힐스' },
  },
  {
    postId: -4,
    boardType: 'FREE',
    title: '집 볼 때 꼭 묻는 질문',
    summary: '댓글로 함께 만드는 임장 질문 목록',
    content:
      '임장 다닐 때 놓치기 쉬운 질문들을 모아보고 싶어요.\n예: 관리비에 뭐가 포함되는지, 주차 대수, 층간소음, 엘리베이터 대기시간 등.\n\n여러분이 실제로 물어봤던 질문도 댓글로 남겨주세요!',
    authorNickname: '동네수다',
    createdAt: '2026-08-04T14:00:00+09:00',
    postedLabel: '2일 전',
    viewCount: 1204,
    likeCount: 210,
    commentCount: 57,
    isLiked: false,
    isHot: true,
    hotRank: 3,
    images: [],
    connectedApartment: null,
    bgColor: '#C86F56',
  },
  {
    postId: -5,
    boardType: 'INFORMATION',
    title: '옥수역 계단 동선 체크',
    summary: '비 오는 저녁, 역에서 단지까지 직접 걸어 확인했어요.',
    content:
      '토요일 오전 8시, 역에서 단지까지 직접 걸었습니다.\n지도에서는 가까워 보였지만 경사와 신호 대기 때문에 체감 시간은 달랐어요.\n\n골목의 생활 편의시설은 자연스럽게 이어졌고, 등교 시간에는 보행 흐름이 한 방향으로 모였습니다.\n숫자만으로는 알기 어려운 동네의 리듬이었습니다.\n\n다음에는 같은 길을 저녁에 다시 걸어 조도와 차량 소음도 확인하려고 합니다.',
    authorNickname: '옥수탐험대',
    createdAt: '2026-07-30T08:00:00+09:00',
    postedLabel: '3일 전',
    viewCount: 461,
    likeCount: 64,
    commentCount: 21,
    isLiked: false,
    isHot: true,
    hotRank: 4,
    images: ['https://loremflickr.com/900/1100/apartment,street'],
    connectedApartment: { apartmentId: 103, name: '래미안 옥수 리버젠' },
  },
  {
    postId: -6,
    boardType: 'INFORMATION',
    title: '관리비 비교 정리표',
    summary: '단지별 관리비 한눈에 봐요.',
    content:
      '근처 단지 5곳의 관리비 고지서를 비교해봤습니다.\n세대수가 많을수록 공용관리비가 낮아지는 경향이 확실히 있었어요.\n\n난방 방식(개별/지역)에 따라 겨울 편차가 크니 참고하세요.',
    authorNickname: '꼼꼼정리',
    createdAt: '2026-08-03T11:00:00+09:00',
    postedLabel: '3일 전',
    viewCount: 388,
    likeCount: 55,
    commentCount: 41,
    isLiked: false,
    isHot: false,
    hotRank: null,
    images: [],
    connectedApartment: null,
    bgColor: '#4F6D8C',
  },
  {
    postId: -7,
    boardType: 'FREE',
    title: '임장 끝나고 뭐 먹지?',
    summary: '근처 맛집 추천 받아요 :)',
    content:
      '오늘 임장 코스 근처에서 점심 먹을 곳을 못 정했어요.\n든든한 백반집이나 혼밥하기 좋은 곳 있으면 추천 부탁드려요!',
    authorNickname: '먹깨비',
    createdAt: '2026-08-05T11:40:00+09:00',
    postedLabel: '어제',
    viewCount: 176,
    likeCount: 22,
    commentCount: 33,
    isLiked: true,
    isHot: false,
    hotRank: null,
    images: ['https://loremflickr.com/700/700/market,food'],
    connectedApartment: null,
  },
  {
    postId: -8,
    boardType: 'FREE',
    title: '첫 임장 후기 남겨요',
    summary: '생각보다 체력전이네요 ㅎㅎ 다들 화이팅.',
    content:
      '오늘 처음으로 임장이란 걸 다녀왔습니다.\n지도만 보다가 실제로 걸어보니 경사와 소음이 완전히 다르더라고요.\n\n하루에 세 단지는 무리였어요. 다음엔 두 곳만 천천히 볼 계획입니다.',
    authorNickname: '임장뉴비',
    createdAt: '2026-08-04T18:20:00+09:00',
    postedLabel: '2일 전',
    viewCount: 264,
    likeCount: 48,
    commentCount: 34,
    isLiked: false,
    isHot: false,
    hotRank: null,
    images: [],
    connectedApartment: null,
    bgColor: '#5B8C7B',
  },
  {
    postId: -9,
    boardType: 'INFORMATION',
    title: '학군지 통학로 체크',
    summary: '횡단보도·언덕 위주로 봤어요.',
    content:
      '아이 통학로를 기준으로 단지를 둘러봤습니다.\n횡단보도 신호 대기, 언덕 경사, 인도 폭을 중심으로 확인했어요.\n\n초등학교까지 신호 없이 갈 수 있는 단지가 확실히 마음이 놓였습니다.',
    authorNickname: '학군러',
    createdAt: '2026-08-02T09:00:00+09:00',
    postedLabel: '4일 전',
    viewCount: 342,
    likeCount: 39,
    commentCount: 15,
    isLiked: false,
    isHot: false,
    hotRank: null,
    images: [],
    connectedApartment: { apartmentId: 104, name: '래미안 원베일리' },
    bgColor: '#B5763E',
  },
  {
    postId: -10,
    boardType: 'INFORMATION',
    title: '한강뷰 단지 리스트',
    summary: '직접 다녀온 곳만 골랐어요.',
    content:
      '한강이 실제로 보이는 세대와 "한강 근처"인 세대는 다릅니다.\n직접 방문해서 거실에서 강이 보이는지 확인한 단지만 정리했어요.\n\n동·층·향에 따라 편차가 크니 매물별로 꼭 확인하세요.',
    authorNickname: '뷰맛집',
    createdAt: '2026-08-01T16:30:00+09:00',
    postedLabel: '5일 전',
    viewCount: 921,
    likeCount: 132,
    commentCount: 48,
    isLiked: false,
    isHot: true,
    hotRank: 5,
    images: ['https://loremflickr.com/900/850/river,city'],
    connectedApartment: null,
  },
  {
    postId: -11,
    boardType: 'FREE',
    title: '주말 임장 메이트 구해요',
    summary: '같이 다니실 분 댓글 주세요!',
    content:
      '이번 주말에 성수·옥수 쪽 임장 계획 중인데 같이 다니실 분 있을까요?\n혼자 다니니 놓치는 게 많더라고요. 편하게 댓글 주세요!',
    authorNickname: '같이가요',
    createdAt: '2026-08-05T21:00:00+09:00',
    postedLabel: '어제',
    viewCount: 198,
    likeCount: 27,
    commentCount: 22,
    isLiked: false,
    isHot: false,
    hotRank: null,
    images: ['https://loremflickr.com/700/720/cafe,street'],
    connectedApartment: null,
  },
];

export const DUMMY_COMMENTS: Record<number, DummyComment[]> = {
  [-5]: [
    { commentId: -501, authorNickname: '성수산책러', content: '아침 시간대 정보가 궁금했는데 동선이 한눈에 들어와요.', timeLabel: '12분 전' },
    { commentId: -502, authorNickname: '서울숲주민', content: '저녁에는 공원 안쪽 조도도 함께 확인해 보세요.', timeLabel: '5분 전' },
    { commentId: -503, authorNickname: '옥수탐험대', content: '좋은 의견 감사해요! 저녁 답사도 곧 올려볼게요.', timeLabel: '2분 전' },
  ],
  [-1]: [
    { commentId: -101, authorNickname: '트리마제이웃', content: '저도 물가 코스 좋아해요. 정수기 위치까지 정리해주셔서 감사합니다.', timeLabel: '9분 전' },
    { commentId: -102, authorNickname: '아침조깅', content: '주말엔 사람이 확 많아지더라고요. 평일 추천 공감합니다.', timeLabel: '3분 전' },
  ],
  [-4]: [
    { commentId: -401, authorNickname: '실전임장러', content: '엘리베이터 대기시간 꼭 물어봐야 해요. 출근시간대에요.', timeLabel: '20분 전' },
    { commentId: -402, authorNickname: '층간소음예민', content: '위층 세대 구성(어린아이 유무)도 여쭤봐요.', timeLabel: '11분 전' },
    { commentId: -403, authorNickname: '주차전쟁', content: '세대당 주차대수는 기본, 이중주차 여부도 중요!', timeLabel: '4분 전' },
  ],
};

// 🟡 더미는 인메모리라 앱 재시작 시 초기화됨. 실 API 연동 시 이 뮤테이터들도 제거.
let nextDummyCommentId = -1000;

/** 좋아요 토글 — 더미 게시글을 직접 수정(목록/상세 공유). 새 상태 반환. */
export function toggleDummyLike(postId: number): { liked: boolean; likeCount: number } {
  const post = DUMMY_POSTS.find((item) => item.postId === postId);
  if (!post) {
    return { liked: false, likeCount: 0 };
  }
  post.isLiked = !post.isLiked;
  post.likeCount += post.isLiked ? 1 : -1;
  return { liked: post.isLiked, likeCount: post.likeCount };
}

/** 더미에서 "내" 댓글로 취급할 닉네임(작성 시 기본값). 이 닉네임 댓글만 삭제 버튼 노출. */
export const DUMMY_MY_NICKNAME = '나';

/** 댓글 추가 — 더미 댓글 목록에 넣고 게시글 commentCount 증가. */
export function addDummyComment(
  postId: number,
  content: string,
  authorNickname = DUMMY_MY_NICKNAME,
): DummyComment {
  nextDummyCommentId -= 1;
  const comment: DummyComment = {
    commentId: nextDummyCommentId,
    authorNickname,
    content,
    timeLabel: '방금 전',
  };
  let list = DUMMY_COMMENTS[postId];
  if (!list) {
    list = [];
    DUMMY_COMMENTS[postId] = list;
  }
  list.push(comment);
  const post = DUMMY_POSTS.find((item) => item.postId === postId);
  if (post) {
    post.commentCount += 1;
  }
  return comment;
}

/** 댓글 삭제 — 목록에서 제거하고 게시글 commentCount 감소. (내 댓글 삭제용) */
export function deleteDummyComment(postId: number, commentId: number): void {
  const list = DUMMY_COMMENTS[postId];
  if (!list) {
    return;
  }
  const index = list.findIndex((item) => item.commentId === commentId);
  if (index === -1) {
    return;
  }
  list.splice(index, 1);
  const post = DUMMY_POSTS.find((item) => item.postId === postId);
  if (post && post.commentCount > 0) {
    post.commentCount -= 1;
  }
}

export function isDummyPostId(postId: number): boolean {
  return postId < 0;
}

export function getDummyPost(postId: number): DummyPost | undefined {
  return DUMMY_POSTS.find((post) => post.postId === postId);
}

export function getDummyComments(postId: number): DummyComment[] {
  return DUMMY_COMMENTS[postId] ?? [];
}

export function boardLabelOf(boardType: CommunityBoardType): string {
  return boardType === 'FREE' ? '자유게시판' : '정보게시판';
}

export function boardBadgeOf(boardType: CommunityBoardType): string {
  return boardType === 'FREE' ? '자유' : '정보';
}
