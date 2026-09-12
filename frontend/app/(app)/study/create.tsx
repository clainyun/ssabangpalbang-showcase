import { Feather } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { useRouter } from 'expo-router';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Keyboard,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import Animated, { useAnimatedKeyboard, useAnimatedStyle } from 'react-native-reanimated';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import { appAlert } from '@/components/AppDialog';
import { OptionButton } from '@/components/OptionButton';
import { GlassSurface } from '@/components/GlassSurface';
import { SelectableChip } from '@/components/SelectableChip';
import {
  BUTTON_BACKGROUND_COLOR,
  DARK_GREEN_COLOR,
  LABEL_COLOR,
  MUTED_TEXT_COLOR,
  PLACEHOLDER_COLOR,
  PRIMARY_COLOR,
  SUBTLE_BACKGROUND_COLOR,
  SURFACE_COLOR,
  TEXT_COLOR,
} from '@/constants/colors';
import { formatPurpose } from '@/features/apartment/format';
import { useApartmentRegionSearch } from '@/features/apartment/api/useApartmentSearch';
import { useDistricts, useDongs } from '@/features/region/api/useRegions';
import { useCreateStudy } from '@/features/study/api/useCreateStudy';
import { StudyApiError, type StudyPurpose } from '@/features/study/api/types';
import { AuthSessionChangedError, AuthSessionExpiredError } from '@/lib/authenticatedFetch';

const TITLE_MAX_LENGTH = 200;
const INTRO_MAX_LENGTH = 1000;
const GOAL_MAX_LENGTH = 300;
const MIN_CAPACITY = 1;
const MAX_CAPACITY = 20;
const INITIAL_APARTMENT_DISPLAY_COUNT = 3;
const PURPOSES: readonly StudyPurpose[] = ['RESIDENCE', 'INVESTMENT', 'STUDY'];
const INVISIBLE_TEXT_PATTERN = /[\s\p{Cf}]/gu;
const ALL_REGION_LABEL = '전체';

function hasVisibleText(value: string): boolean {
  return value.replace(INVISIBLE_TEXT_PATTERN, '').length > 0;
}

interface SectionHeadingProps {
  title: string;
  description?: string;
}

function SectionHeading({ title, description }: SectionHeadingProps) {
  return (
    <View style={styles.sectionHeading}>
      <Text style={styles.sectionTitle}>{title}</Text>
      {description ? <Text style={styles.sectionDescription}>{description}</Text> : null}
    </View>
  );
}

interface QueryStatusProps {
  loading: boolean;
  error: boolean;
  loadingText: string;
  errorText: string;
  onRetry: () => void;
}

function QueryStatus({ loading, error, loadingText, errorText, onRetry }: QueryStatusProps) {
  if (loading) {
    return (
      <View accessibilityLiveRegion="polite" style={styles.queryStatus}>
        <ActivityIndicator color={PRIMARY_COLOR} size="small" />
        <Text style={styles.queryStatusText}>{loadingText}</Text>
      </View>
    );
  }

  if (error) {
    return (
      <View accessibilityLiveRegion="polite" style={styles.queryStatus}>
        <Text style={styles.queryStatusText}>{errorText}</Text>
        <Pressable
          accessibilityRole="button"
          onPress={onRetry}
          style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}
        >
          <Text style={styles.retryButtonText}>다시 시도</Text>
        </Pressable>
      </View>
    );
  }

  return null;
}

export default function StudyCreateScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const createMutation = useCreateStudy();

  // edge-to-edge + New Arch에선 adjustResize가 창을 안 줄여 키보드가 하단 바를 덮음.
  // reanimated useAnimatedKeyboard로 화면 전체를 키보드 높이만큼 위로 밀어 하단 바가 키보드 위에 붙는다.
  const keyboard = useAnimatedKeyboard();
  // 키보드 높이는 '입력창이 실제로 포커스일 때'만 반영한다. 안드로이드에서 액티비티·모달
  // 전환 중 close inset 애니메이션 콜백이 유실되면 keyboard.state/height가 OPEN·CLOSING에
  // stuck될 수 있는데, 포커스는 JS에서 직접 제어하므로 게이트가 내려가 있으면 stuck된
  // 값이 화면을 밀지 못한다(글 작성 create.tsx와 동일 패턴). 모든 입력창의
  // onFocus/onBlur에서 갱신한다.
  const [isInputFocused, setIsInputFocused] = useState(false);
  const keyboardPushStyle = useAnimatedStyle(() => ({
    paddingBottom: isInputFocused ? keyboard.height.value : 0,
  }));
  const bottomBarPadStyle = useAnimatedStyle(() => ({
    paddingBottom:
      isInputFocused && keyboard.height.value > 0 ? 12 : Math.max(18, insets.bottom + 8),
  }));

  // 아파트 검색 입력 포커스 시 검색 블록을 스크롤 상단으로 올려 결과가 키보드에 가리지 않게.
  const scrollRef = useRef<ScrollView>(null);
  // 아파트 검색 블록의 절대 스크롤 오프셋을 onLayout으로 추적한다.
  // 섹션 y(스크롤 콘텐츠 기준) + 블록 y(섹션 기준)를 더하면 절대 오프셋이 된다.
  const apartmentSectionYRef = useRef(0);
  const apartmentFieldYRef = useRef(0);
  // 아파트 입력에 포커스가 있는 동안만 keyboardDidShow에서 재스크롤하려고 추적한다
  // (제목·소개 등 다른 입력에 포커스가 갔을 때는 스크롤하지 않게).
  const apartmentFocusedRef = useRef(false);

  // 검색 블록을 뷰포트 최상단으로 스크롤한다. New Arch(Fabric)에선 ScrollView.getInnerViewNode()가
  // 없어 measureLayout 기준 노드를 얻지 못하므로, 섹션 y(콘텐츠 기준)와 블록 y(섹션 기준)를
  // onLayout으로 받아 더한 절대 오프셋으로 스크롤한다. 그래야 검색창이 상단에 붙고 결과 목록이
  // 키보드 위에 함께 보인다.
  const scrollApartmentFieldToTop = () => {
    const y = apartmentSectionYRef.current + apartmentFieldYRef.current;
    scrollRef.current?.scrollTo({ y: Math.max(0, y - 8), animated: true });
  };

  // onFocus 직후 한 번(rAF)만 스크롤하면, 그 시점엔 키보드가 아직 안 올라와
  // ScrollView 뷰포트가 안 줄어든 상태라 목표 오프셋이 최대 스크롤 한계에 걸려
  // 입력창이 상단까지 못 간다. 키보드가 완전히 뜬 뒤(keyboardDidShow) 다시 스크롤해
  // 결과가 키보드 위에 보이게 한다. 아파트 입력이 포커스된 동안에만 동작한다.
  useEffect(() => {
    const show = Keyboard.addListener('keyboardDidShow', () => {
      if (apartmentFocusedRef.current) scrollApartmentFieldToTop();
    });
    return () => show.remove();
    // scrollApartmentFieldToTop은 ref만 읽어 안정적이라 마운트 시 한 번만 구독한다.
  }, []);

  const [districtCode, setDistrictCode] = useState<string | null>(null);
  const [hasSelectedDistrict, setHasSelectedDistrict] = useState(true);
  const [dongCode, setDongCode] = useState<string | null>(null);
  const [apartmentId, setApartmentId] = useState<number | null>(null);
  const [apartmentQuery, setApartmentQuery] = useState('');
  const [visibleApartmentCount, setVisibleApartmentCount] = useState(
    INITIAL_APARTMENT_DISPLAY_COUNT,
  );
  const [title, setTitle] = useState('');
  const [intro, setIntro] = useState('');
  const [goal, setGoal] = useState('');
  const [capacity, setCapacity] = useState(4);
  const [purpose, setPurpose] = useState<StudyPurpose | null>(null);

  const isSubmittingRef = useRef(false);
  const isNavigatingRef = useRef(false);

  const districtsQuery = useDistricts();
  const dongsQuery = useDongs(districtCode);
  const apartmentsQuery = useApartmentRegionSearch({
    keyword: apartmentQuery,
    districtCode: districtCode ?? undefined,
    dongCode: dongCode ?? undefined,
  });

  const selectedDistrict = districtsQuery.data?.districts.find(
    (district) => district.districtCode === districtCode,
  );
  const selectedDong = dongsQuery.data?.dongs.find((dong) => dong.dongCode === dongCode);
  const selectedApartment = apartmentsQuery.data?.content.find(
    (apartment) => apartment.apartmentId === apartmentId,
  );

  const filteredApartments = useMemo(() => {
    const apartments = apartmentsQuery.data?.content ?? [];
    const query = apartmentQuery.trim();

    if (!query) {
      return apartments;
    }

    return apartments.filter((apartment) => apartment.name.includes(query));
  }, [apartmentQuery, apartmentsQuery.data?.content]);
  const visibleApartments = filteredApartments.slice(0, visibleApartmentCount);
  const hasMoreApartments = visibleApartmentCount < filteredApartments.length;

  const trimmedTitle = title.trim();
  const trimmedGoal = goal.trim();
  const trimmedIntro = intro.trim();
  const fieldsAreValid =
    apartmentId !== null &&
    hasVisibleText(trimmedTitle) &&
    trimmedTitle.length <= TITLE_MAX_LENGTH &&
    trimmedIntro.length <= INTRO_MAX_LENGTH &&
    hasVisibleText(trimmedGoal) &&
    trimmedGoal.length <= GOAL_MAX_LENGTH &&
    capacity >= MIN_CAPACITY &&
    capacity <= MAX_CAPACITY &&
    purpose !== null;
  const canSubmit = fieldsAreValid && !createMutation.isPending;

  const resetApartmentSelection = () => {
    setApartmentId(null);
    setApartmentQuery('');
    setVisibleApartmentCount(INITIAL_APARTMENT_DISPLAY_COUNT);
  };

  const handleDistrictPress = (nextDistrictCode: string) => {
    setHasSelectedDistrict(true);
    setDistrictCode(nextDistrictCode);
    setDongCode(null);
    resetApartmentSelection();
  };

  const handleAllDistrictsPress = () => {
    setHasSelectedDistrict(true);
    setDistrictCode(null);
    setDongCode(null);
    resetApartmentSelection();
  };

  const handleDongPress = (nextDongCode: string) => {
    setDongCode(nextDongCode);
    resetApartmentSelection();
  };

  const handleAllDongsPress = () => {
    setDongCode(null);
    resetApartmentSelection();
  };

  const handleSubmit = async () => {
    if (!canSubmit || apartmentId === null || purpose === null || isSubmittingRef.current) {
      return;
    }

    isSubmittingRef.current = true;

    try {
      const result = await createMutation.mutateAsync({
        apartmentId,
        title: trimmedTitle,
        ...(trimmedIntro ? { intro: trimmedIntro } : {}),
        goal: trimmedGoal,
        capacity,
        purpose,
      });

      if (isNavigatingRef.current) {
        return;
      }

      isNavigatingRef.current = true;
      router.replace({
        pathname: '/(app)/study/[id]',
        params: { id: String(result.studyId) },
      });
    } catch (error) {
      if (error instanceof AuthSessionExpiredError || error instanceof AuthSessionChangedError) {
        return;
      }

      if (error instanceof StudyApiError && error.code === 'APARTMENT_NOT_FOUND') {
        setApartmentId(null);
        appAlert('선택한 단지를 찾을 수 없어요', error.message || '다른 단지를 선택해 주세요.');
        return;
      }

      appAlert(
        '스터디를 만들지 못했어요',
        error instanceof Error && error.message.trim()
          ? error.message
          : '잠시 후 다시 시도해주세요.',
      );
    } finally {
      isSubmittingRef.current = false;
    }
  };

  const districtName = dongsQuery.data?.districtName ?? selectedDistrict?.districtName;
  const selectionSummary = selectedApartment
    ? `${districtName ?? '서울 전체'} · ${selectedDong?.dongName ?? '동 전체'} · ${selectedApartment.name}`
    : null;

  return (
    <SafeAreaView edges={['top']} style={styles.safeArea}>
      <LinearGradient
        colors={['#F7F5EC', '#EAF5ED', '#F7FBF8']}
        locations={[0, 0.52, 1]}
        style={StyleSheet.absoluteFill}
      />
      <Animated.View style={[styles.keyboardContainer, keyboardPushStyle]}>
        <View style={styles.header}>
          <Pressable
            accessibilityLabel="뒤로 가기"
            accessibilityRole="button"
            onPress={() => router.back()}
            style={({ pressed }) => [styles.headerButton, pressed && styles.pressed]}
          >
            <Feather color={TEXT_COLOR} name="chevron-left" size={24} />
          </Pressable>
          <View style={styles.headerCopy}>
            <Text style={styles.headerEyebrow}>새로운 임장 모임</Text>
            <Text style={styles.headerTitle}>스터디 만들기</Text>
          </View>
        </View>

        <ScrollView
          contentContainerStyle={styles.scrollContent}
          keyboardShouldPersistTaps="handled"
          ref={scrollRef}
          showsVerticalScrollIndicator={false}
        >
          <View
            style={styles.section}
            onLayout={(event) => {
              apartmentSectionYRef.current = event.nativeEvent.layout.y;
            }}
          >
            <LinearGradient
              colors={['rgba(255,255,255,0.94)', 'rgba(237,248,241,0.92)']}
              start={{ x: 0.08, y: 0 }}
              end={{ x: 1, y: 1 }}
              style={StyleSheet.absoluteFill}
            />
            <SectionHeading
              description="함께 살펴볼 아파트 단지를 선택해 주세요."
              title="임장 단지"
            />

            {selectionSummary ? (
              <View style={styles.selectionSummary}>
                <Feather color={PRIMARY_COLOR} name="map-pin" size={16} />
                <Text style={styles.selectionSummaryText}>{selectionSummary}</Text>
              </View>
            ) : null}

            <Text style={styles.fieldLabel}>지역</Text>
            <QueryStatus
              error={districtsQuery.isError}
              errorText="지역 정보를 불러오지 못했어요."
              loading={districtsQuery.isLoading}
              loadingText="지역을 불러오는 중이에요."
              onRetry={() => {
                void districtsQuery.refetch();
              }}
            />
            {districtsQuery.data ? (
              <ScrollView
                contentContainerStyle={styles.chipRow}
                horizontal
                showsHorizontalScrollIndicator={false}
              >
                <View
                  accessibilityRole="button"
                  accessibilityState={{ selected: districtCode === null }}
                >
                  <SelectableChip
                    label={ALL_REGION_LABEL}
                    onPress={handleAllDistrictsPress}
                    selected={districtCode === null}
                  />
                </View>
                {districtsQuery.data.districts.map((district) => {
                  const selected = districtCode !== null && district.districtCode === districtCode;

                  return (
                    <View
                      accessibilityRole="button"
                      accessibilityState={{ selected }}
                      key={district.districtCode}
                    >
                      <SelectableChip
                        label={district.districtName}
                        onPress={() => handleDistrictPress(district.districtCode)}
                        selected={selected}
                      />
                    </View>
                  );
                })}
              </ScrollView>
            ) : null}

            {hasSelectedDistrict ? (
              <>
                <View style={styles.labelRow}>
                  <Text style={styles.fieldLabel}>동</Text>
                  <Text style={styles.fieldHint}>
                    {districtCode === null
                      ? '서울 전체'
                      : `${selectedDistrict?.districtName ?? ''} 전체`}
                  </Text>
                </View>
                {districtCode !== null ? (
                  <QueryStatus
                    error={dongsQuery.isError}
                    errorText="동 정보를 불러오지 못했어요."
                    loading={dongsQuery.isLoading}
                    loadingText="동을 불러오는 중이에요."
                    onRetry={() => {
                      void dongsQuery.refetch();
                    }}
                  />
                ) : null}
                {districtCode !== null && dongsQuery.data ? (
                  <ScrollView
                    contentContainerStyle={styles.chipRow}
                    horizontal
                    showsHorizontalScrollIndicator={false}
                  >
                    <View
                      accessibilityRole="button"
                      accessibilityState={{ selected: dongCode === null }}
                    >
                      <SelectableChip
                        label={ALL_REGION_LABEL}
                        onPress={handleAllDongsPress}
                        selected={dongCode === null}
                      />
                    </View>
                    {dongsQuery.data.dongs.map((dong) => {
                      const selected = dong.dongCode === dongCode;
                      const disabled = !dong.hasApartment;

                      return (
                        <View
                          accessibilityRole="button"
                          accessibilityState={{ disabled, selected }}
                          key={dong.dongCode}
                        >
                          <SelectableChip
                            disabled={disabled}
                            label={dong.dongName}
                            onPress={() => handleDongPress(dong.dongCode)}
                            selected={selected}
                          />
                        </View>
                      );
                    })}
                  </ScrollView>
                ) : districtCode === null ? (
                  <View style={styles.allDongGroup}>
                    <View
                      accessibilityRole="button"
                      accessibilityState={{ selected: true }}
                      style={styles.allDongChip}
                    >
                      <SelectableChip
                        label={ALL_REGION_LABEL}
                        onPress={handleAllDongsPress}
                        selected
                      />
                    </View>
                    <View style={styles.allDongCard}>
                      <Feather color={PRIMARY_COLOR} name="check" size={16} />
                      <Text style={styles.allDongText}>모든 동을 대상으로 검색해요.</Text>
                    </View>
                  </View>
                ) : null}
              </>
            ) : null}

            {hasSelectedDistrict ? (
              <View
                collapsable={false}
                onLayout={(event) => {
                  apartmentFieldYRef.current = event.nativeEvent.layout.y;
                }}
              >
                <Text style={styles.fieldLabel}>아파트</Text>
                <View style={styles.searchInputContainer}>
                  <Feather color={MUTED_TEXT_COLOR} name="search" size={17} />
                  <TextInput
                    accessibilityLabel="아파트 이름 검색"
                    onChangeText={(value) => {
                      setApartmentQuery(value);
                      setVisibleApartmentCount(INITIAL_APARTMENT_DISPLAY_COUNT);
                    }}
                    onBlur={() => {
                      apartmentFocusedRef.current = false;
                      setIsInputFocused(false);
                    }}
                    onFocus={() => {
                      apartmentFocusedRef.current = true;
                      setIsInputFocused(true);
                      // 즉시(best-effort) 한 번 올리고, 키보드가 완전히 뜬 뒤에는 위의
                      // keyboardDidShow 리스너가 다시 상단으로 밀착시킨다.
                      requestAnimationFrame(scrollApartmentFieldToTop);
                    }}
                    placeholder={
                      districtCode === null ? '서울 전체에서 단지 이름 검색' : '단지 이름으로 찾기'
                    }
                    placeholderTextColor={PLACEHOLDER_COLOR}
                    style={styles.searchInput}
                    value={apartmentQuery}
                  />
                </View>

                <QueryStatus
                  error={apartmentsQuery.isError}
                  errorText="단지 정보를 불러오지 못했어요."
                  loading={apartmentsQuery.isLoading}
                  loadingText="단지를 불러오는 중이에요."
                  onRetry={() => {
                    void apartmentsQuery.refetch();
                  }}
                />

                {districtCode === null && apartmentQuery.trim().length === 0 ? (
                  <View style={styles.searchGuide}>
                    <Feather color={PRIMARY_COLOR} name="search" size={18} />
                    <Text style={styles.searchGuideText}>
                      구와 동을 전체로 선택한 경우 단지 이름을 입력하면 서울 전체에서 찾아요.
                    </Text>
                  </View>
                ) : null}

                {apartmentsQuery.data && apartmentsQuery.data.content.length === 0 ? (
                  <Text accessibilityLiveRegion="polite" style={styles.emptyText}>
                    선택한 지역에 등록된 단지가 없어요. 다른 지역을 선택해 주세요.
                  </Text>
                ) : null}

                {apartmentsQuery.data &&
                apartmentsQuery.data.content.length > 0 &&
                filteredApartments.length === 0 ? (
                  <Text accessibilityLiveRegion="polite" style={styles.emptyText}>
                    검색 결과가 없어요.
                  </Text>
                ) : null}

                <View style={styles.apartmentList}>
                  {visibleApartments.map((apartment) => {
                    const selected = apartment.apartmentId === apartmentId;

                    return (
                      <Pressable
                        accessibilityRole="button"
                        accessibilityState={{ selected }}
                        key={apartment.apartmentId}
                        onPress={() => setApartmentId(apartment.apartmentId)}
                        style={({ pressed }) => [
                          styles.apartmentItem,
                          selected && styles.apartmentItemSelected,
                          pressed && styles.pressed,
                        ]}
                      >
                        <View style={styles.apartmentText}>
                          <Text
                            numberOfLines={1}
                            style={[styles.apartmentName, selected && styles.apartmentNameSelected]}
                          >
                            {apartment.name}
                          </Text>
                          {apartment.address ? (
                            <Text numberOfLines={1} style={styles.apartmentAddress}>
                              {apartment.address}
                            </Text>
                          ) : null}
                        </View>
                        {selected ? (
                          <Feather color={PRIMARY_COLOR} name="check-circle" size={20} />
                        ) : null}
                      </Pressable>
                    );
                  })}
                </View>

                {hasMoreApartments ? (
                  <Pressable
                    accessibilityRole="button"
                    onPress={() => setVisibleApartmentCount(filteredApartments.length)}
                    style={({ pressed }) => [styles.moreButton, pressed && styles.pressed]}
                  >
                    <Text style={styles.moreButtonText}>더보기</Text>
                    <Feather color={PRIMARY_COLOR} name="chevron-down" size={18} />
                  </Pressable>
                ) : null}

                {apartmentsQuery.data?.totalElements && apartmentsQuery.data.totalElements > 100 ? (
                  <Text accessibilityLiveRegion="polite" style={styles.helperText}>
                    단지가 100개를 넘어 일부만 표시됩니다. 이름으로 검색해 주세요.
                  </Text>
                ) : null}
              </View>
            ) : null}
          </View>

          <View style={styles.section}>
            <LinearGradient
              colors={['rgba(255,255,255,0.94)', 'rgba(237,248,241,0.92)']}
              start={{ x: 0.08, y: 0 }}
              end={{ x: 1, y: 1 }}
              style={StyleSheet.absoluteFill}
            />
            <SectionHeading
              description="스터디를 알아볼 수 있는 이름을 적어 주세요."
              title="기본 정보"
            />

            <View style={styles.labelRow}>
              <Text style={styles.fieldLabel}>제목</Text>
              <Text style={styles.counterText}>
                {title.length}/{TITLE_MAX_LENGTH}
              </Text>
            </View>
            <TextInput
              accessibilityLabel="스터디 제목"
              maxLength={TITLE_MAX_LENGTH}
              onBlur={() => setIsInputFocused(false)}
              onChangeText={setTitle}
              onFocus={() => setIsInputFocused(true)}
              placeholder="예: 역삼동 실거주 임장 모임"
              placeholderTextColor={PLACEHOLDER_COLOR}
              style={styles.textInput}
              value={title}
            />

            <View style={styles.labelRow}>
              <Text style={styles.fieldLabel}>소개 (선택)</Text>
              <Text style={styles.counterText}>
                {intro.length}/{INTRO_MAX_LENGTH}
              </Text>
            </View>
            <TextInput
              accessibilityLabel="스터디 소개"
              maxLength={INTRO_MAX_LENGTH}
              multiline
              onBlur={() => setIsInputFocused(false)}
              onChangeText={setIntro}
              onFocus={() => setIsInputFocused(true)}
              placeholder="어떤 사람들이 함께하면 좋을지 알려 주세요."
              placeholderTextColor={PLACEHOLDER_COLOR}
              style={[styles.textInput, styles.multilineInput]}
              textAlignVertical="top"
              value={intro}
            />

            <View style={styles.labelRow}>
              <Text style={styles.fieldLabel}>스터디 목표</Text>
              <Text style={styles.counterText}>
                {goal.length}/{GOAL_MAX_LENGTH}
              </Text>
            </View>
            <TextInput
              accessibilityLabel="스터디 목표"
              maxLength={GOAL_MAX_LENGTH}
              multiline
              onBlur={() => setIsInputFocused(false)}
              onChangeText={setGoal}
              onFocus={() => setIsInputFocused(true)}
              placeholder="예: 단지와 주변 생활권을 직접 확인해요."
              placeholderTextColor={PLACEHOLDER_COLOR}
              style={[styles.textInput, styles.goalInput]}
              textAlignVertical="top"
              value={goal}
            />
          </View>

          <View style={styles.section}>
            <LinearGradient
              colors={['rgba(255,255,255,0.94)', 'rgba(237,248,241,0.92)']}
              start={{ x: 0.08, y: 0 }}
              end={{ x: 1, y: 1 }}
              style={StyleSheet.absoluteFill}
            />
            <SectionHeading title="모집 설정" />

            <View style={styles.capacityLabelRow}>
              <Text style={styles.fieldLabel}>정원</Text>
              <Text style={styles.capacityLimit}>(최대 20명)</Text>
            </View>
            <View style={styles.capacityRow}>
              <Pressable
                accessibilityLabel="정원 줄이기"
                accessibilityRole="button"
                accessibilityState={{
                  disabled: capacity === MIN_CAPACITY,
                }}
                disabled={capacity === MIN_CAPACITY}
                onPress={() => setCapacity((current) => Math.max(MIN_CAPACITY, current - 1))}
                style={({ pressed }) => [
                  styles.capacityButton,
                  capacity === MIN_CAPACITY && styles.capacityButtonDisabled,
                  pressed && styles.pressed,
                ]}
              >
                <Feather color={TEXT_COLOR} name="minus" size={20} />
              </Pressable>
              <View style={styles.capacityValue}>
                <Text style={styles.capacityNumber}>{capacity}</Text>
                <Text style={styles.capacityUnit}>명</Text>
              </View>
              <Pressable
                accessibilityLabel="정원 늘리기"
                accessibilityRole="button"
                accessibilityState={{
                  disabled: capacity === MAX_CAPACITY,
                }}
                disabled={capacity === MAX_CAPACITY}
                onPress={() => setCapacity((current) => Math.min(MAX_CAPACITY, current + 1))}
                style={({ pressed }) => [
                  styles.capacityButton,
                  capacity === MAX_CAPACITY && styles.capacityButtonDisabled,
                  pressed && styles.pressed,
                ]}
              >
                <Feather color={TEXT_COLOR} name="plus" size={20} />
              </Pressable>
            </View>
            <Text style={styles.fieldLabel}>목적</Text>
            <View style={styles.purposeRow}>
              {PURPOSES.map((item) => {
                const selected = purpose === item;

                return (
                  <View
                    accessibilityRole="button"
                    accessibilityState={{ selected }}
                    key={item}
                    style={styles.purposeButton}
                  >
                    <OptionButton
                      label={formatPurpose(item) ?? item}
                      onPress={() => setPurpose(item)}
                      selected={selected}
                      style={styles.purposeButton}
                    />
                  </View>
                );
              })}
            </View>
          </View>
        </ScrollView>

        <Animated.View style={[styles.bottomBar, bottomBarPadStyle]}>
          <GlassSurface tint="#EEF8F1" radius={30} tintOpacity={0.72} intensity={20} />
          <Pressable
            accessibilityRole="button"
            accessibilityState={{ disabled: !canSubmit }}
            disabled={!canSubmit}
            onPress={() => {
              void handleSubmit();
            }}
            style={({ pressed }) => [
              styles.submitButton,
              !canSubmit && styles.submitButtonDisabled,
              pressed && styles.pressed,
            ]}
          >
            <GlassSurface
              tint={canSubmit ? '#BDECCF' : '#E5EAE7'}
              radius={22}
              tintOpacity={canSubmit ? 0.38 : 0.5}
              intensity={canSubmit ? 28 : 16}
            />
            <LinearGradient
              colors={
                canSubmit
                  ? ['rgba(255,255,255,0.46)', 'rgba(157,223,183,0.52)']
                  : ['rgba(255,255,255,0.38)', 'rgba(221,228,224,0.56)']
              }
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 1 }}
              style={StyleSheet.absoluteFill}
            />
            <View pointerEvents="none" style={styles.submitHighlight} />
            {createMutation.isPending ? (
              <View accessibilityLiveRegion="polite" style={styles.submitContent}>
                <ActivityIndicator color={SURFACE_COLOR} size="small" />
              </View>
            ) : (
              <Text style={styles.submitText}>스터디 만들기</Text>
            )}
          </Pressable>
        </Animated.View>
      </Animated.View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#F4F8F4',
  },
  keyboardContainer: {
    flex: 1,
  },
  header: {
    minHeight: 64,
    marginHorizontal: 14,
    marginTop: 6,
    paddingHorizontal: 10,
    flexDirection: 'row',
    alignItems: 'center',
  },
  headerButton: {
    width: 48,
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 24,
    backgroundColor: 'rgba(255,255,255,0.56)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.9)',
  },
  headerCopy: {
    flex: 1,
    marginRight: 48,
    alignItems: 'center',
    gap: 2,
  },
  headerEyebrow: {
    color: PRIMARY_COLOR,
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.8,
  },
  headerTitle: {
    fontSize: 19,
    fontWeight: '900',
    color: DARK_GREEN_COLOR,
  },
  scrollContent: {
    paddingHorizontal: 14,
    paddingTop: 18,
    paddingBottom: 34,
    gap: 16,
  },
  section: {
    padding: 20,
    overflow: 'hidden',
    borderRadius: 26,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.96)',
    backgroundColor: '#F2F8F3',
    shadowColor: DARK_GREEN_COLOR,
    shadowOpacity: 0.06,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 8 },
    elevation: 2,
  },
  sectionHeading: {
    gap: 5,
    marginBottom: 18,
  },
  sectionTitle: {
    fontSize: 21,
    fontWeight: '900',
    color: DARK_GREEN_COLOR,
  },
  sectionDescription: {
    fontSize: 13,
    lineHeight: 20,
    color: LABEL_COLOR,
  },
  selectionSummary: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingVertical: 4,
    marginBottom: 18,
  },
  selectionSummaryText: {
    flex: 1,
    color: PRIMARY_COLOR,
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '700',
  },
  fieldLabel: {
    marginTop: 14,
    marginBottom: 8,
    color: DARK_GREEN_COLOR,
    fontSize: 14,
    fontWeight: '700',
  },
  fieldHint: {
    marginTop: 14,
    marginBottom: 8,
    color: PRIMARY_COLOR,
    fontSize: 12,
    fontWeight: '700',
  },
  chipRow: {
    gap: 8,
    paddingRight: 8,
  },
  queryStatus: {
    minHeight: 52,
    padding: 12,
    gap: 8,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 12,
    backgroundColor: SUBTLE_BACKGROUND_COLOR,
  },
  queryStatusText: {
    color: MUTED_TEXT_COLOR,
    fontSize: 13,
    textAlign: 'center',
  },
  retryButton: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 10,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  retryButtonText: {
    color: PRIMARY_COLOR,
    fontSize: 13,
    fontWeight: '700',
  },
  searchInputContainer: {
    minHeight: 52,
    paddingHorizontal: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: 'rgba(19,178,110,0.12)',
    backgroundColor: 'rgba(255,255,255,0.68)',
  },
  searchInput: {
    flex: 1,
    paddingVertical: 12,
    color: TEXT_COLOR,
    fontSize: 14,
  },
  helperText: {
    marginTop: 10,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
    lineHeight: 18,
  },
  emptyText: {
    paddingVertical: 22,
    color: MUTED_TEXT_COLOR,
    fontSize: 13,
    lineHeight: 20,
    textAlign: 'center',
  },
  apartmentList: {
    marginTop: 10,
  },
  moreButton: {
    minHeight: 44,
    marginTop: 10,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    borderRadius: 12,
    backgroundColor: BUTTON_BACKGROUND_COLOR,
  },
  moreButtonText: {
    color: PRIMARY_COLOR,
    fontSize: 14,
    fontWeight: '700',
  },
  apartmentItem: {
    minHeight: 66,
    paddingHorizontal: 14,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: 'rgba(16,39,30,0.10)',
  },
  apartmentItemSelected: {
    backgroundColor: 'rgba(221,246,233,0.9)',
  },
  apartmentText: {
    flex: 1,
    gap: 4,
  },
  apartmentName: {
    color: TEXT_COLOR,
    fontSize: 14,
    fontWeight: '700',
  },
  apartmentNameSelected: {
    color: PRIMARY_COLOR,
  },
  apartmentAddress: {
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
  },
  labelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  counterText: {
    marginTop: 14,
    marginBottom: 8,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
  },
  textInput: {
    minHeight: 50,
    paddingHorizontal: 14,
    paddingVertical: 13,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: 'rgba(16,39,30,0.06)',
    backgroundColor: 'rgba(255,255,255,0.68)',
    color: TEXT_COLOR,
    fontSize: 14,
  },
  multilineInput: {
    minHeight: 94,
  },
  goalInput: {
    minHeight: 82,
  },
  purposeRow: {
    flexDirection: 'row',
    gap: 8,
  },
  purposeButton: {
    flex: 1,
  },
  capacityRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 20,
  },
  capacityLabelRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  capacityLimit: {
    marginTop: 14,
    marginBottom: 8,
    color: MUTED_TEXT_COLOR,
    fontSize: 12,
  },
  capacityButton: {
    width: 48,
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 24,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.9)',
    backgroundColor: 'rgba(255,255,255,0.7)',
  },
  capacityButtonDisabled: {
    opacity: 0.35,
  },
  capacityValue: {
    minWidth: 72,
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'center',
    gap: 3,
  },
  capacityNumber: {
    color: TEXT_COLOR,
    fontSize: 28,
    fontWeight: '800',
  },
  capacityUnit: {
    color: LABEL_COLOR,
    fontSize: 14,
    fontWeight: '600',
  },
  bottomBar: {
    marginHorizontal: 14,
    marginBottom: 8,
    paddingTop: 10,
    paddingHorizontal: 10,
    overflow: 'hidden',
    borderRadius: 30,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.86)',
  },
  submitButton: {
    height: 58,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    borderRadius: 22,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.82)',
    backgroundColor: 'transparent',
  },
  submitButtonDisabled: {
    opacity: 0.66,
  },
  submitHighlight: {
    position: 'absolute',
    top: 2,
    left: 18,
    right: 18,
    height: 1,
    borderRadius: 1,
    backgroundColor: 'rgba(255,255,255,0.88)',
  },
  submitContent: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 9,
  },
  submitText: {
    color: DARK_GREEN_COLOR,
    fontSize: 16,
    fontWeight: '900',
  },
  allDongCard: {
    minHeight: 44,
    paddingHorizontal: 2,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  allDongGroup: {
    gap: 8,
  },
  allDongChip: {
    alignSelf: 'flex-start',
  },
  allDongText: {
    color: DARK_GREEN_COLOR,
    fontSize: 13,
    fontWeight: '700',
  },
  searchGuide: {
    marginTop: 10,
    paddingVertical: 8,
    paddingHorizontal: 2,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  searchGuideText: {
    flex: 1,
    color: LABEL_COLOR,
    fontSize: 12,
    lineHeight: 18,
    fontWeight: '600',
  },
  pressed: {
    opacity: 0.72,
  },
});
