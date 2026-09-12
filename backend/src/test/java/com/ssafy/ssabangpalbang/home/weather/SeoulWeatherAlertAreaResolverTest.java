package com.ssafy.ssabangpalbang.home.weather;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeoulWeatherAlertAreaResolverTest {

    private final KakaoRegionClient kakaoRegionClient = mock(
            KakaoRegionClient.class
    );
    private final SeoulWeatherAlertAreaResolver resolver =
            new SeoulWeatherAlertAreaResolver(kakaoRegionClient);

    @Test
    void 다음_임장_주소의_송파구를_서울동남권으로_매핑한다() {
        HomeWeatherLocation location = new HomeWeatherLocation(
                37.51,
                127.10,
                "서울특별시 송파구 잠실동",
                HomeWeatherLocation.Basis.NEXT_VISIT_APARTMENT
        );

        Optional<SeoulWeatherAlertArea> result = resolver.resolve(location);

        assertThat(result).contains(new SeoulWeatherAlertArea(
                "L1100100",
                "서울동남권"
        ));
        verify(kakaoRegionClient, never()).resolveSeoulDistrict(
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble()
        );
    }

    @Test
    void 현재_위치는_카카오_좌표변환_결과로_서울동북권을_찾는다() {
        HomeWeatherLocation location = new HomeWeatherLocation(
                37.5412,
                127.0178,
                "현재 위치",
                HomeWeatherLocation.Basis.CURRENT_LOCATION
        );
        when(kakaoRegionClient.resolveSeoulDistrict(
                37.5412,
                127.0178
        )).thenReturn(Optional.of("성동구"));

        Optional<SeoulWeatherAlertArea> result = resolver.resolve(location);

        assertThat(result).contains(new SeoulWeatherAlertArea(
                "L1100200",
                "서울동북권"
        ));
    }
}
