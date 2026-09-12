package com.ssafy.ssabangpalbang.home.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "ssabangpalbang.home.weather")
public class HomeWeatherProperties {

    private URI forecastBaseUrl = URI.create("https://api.open-meteo.com");
    private URI airQualityBaseUrl = URI.create(
            "https://air-quality-api.open-meteo.com"
    );
    private URI weatherAlertBaseUrl = URI.create(
            "https://apis.data.go.kr/1360000/WthrWrnInfoService"
    );
    private URI kakaoLocalBaseUrl = URI.create("https://dapi.kakao.com");
    private String kmaWeatherAlertServiceKey = "";
    private String kakaoRestApiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);
    private Duration freshTtl = Duration.ofMinutes(15);
    private Duration staleTtl = Duration.ofHours(1);
    private Duration alertFreshTtl = Duration.ofMinutes(5);
    private Duration alertStaleTtl = Duration.ofMinutes(30);
    private double defaultLatitude = 37.5665;
    private double defaultLongitude = 126.9780;
    private String defaultLocationName = "서울특별시 중구";

    public URI forecastBaseUrl() {
        return forecastBaseUrl;
    }

    public void setForecastBaseUrl(URI forecastBaseUrl) {
        this.forecastBaseUrl = forecastBaseUrl;
    }

    public URI airQualityBaseUrl() {
        return airQualityBaseUrl;
    }

    public void setAirQualityBaseUrl(URI airQualityBaseUrl) {
        this.airQualityBaseUrl = airQualityBaseUrl;
    }

    public URI weatherAlertBaseUrl() {
        return weatherAlertBaseUrl;
    }

    public void setWeatherAlertBaseUrl(URI weatherAlertBaseUrl) {
        this.weatherAlertBaseUrl = weatherAlertBaseUrl;
    }

    public URI kakaoLocalBaseUrl() {
        return kakaoLocalBaseUrl;
    }

    public void setKakaoLocalBaseUrl(URI kakaoLocalBaseUrl) {
        this.kakaoLocalBaseUrl = kakaoLocalBaseUrl;
    }

    public String kmaWeatherAlertServiceKey() {
        return kmaWeatherAlertServiceKey;
    }

    public void setKmaWeatherAlertServiceKey(
            String kmaWeatherAlertServiceKey
    ) {
        this.kmaWeatherAlertServiceKey = kmaWeatherAlertServiceKey;
    }

    public String kakaoRestApiKey() {
        return kakaoRestApiKey;
    }

    public void setKakaoRestApiKey(String kakaoRestApiKey) {
        this.kakaoRestApiKey = kakaoRestApiKey;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration freshTtl() {
        return freshTtl;
    }

    public void setFreshTtl(Duration freshTtl) {
        this.freshTtl = freshTtl;
    }

    public Duration staleTtl() {
        return staleTtl;
    }

    public void setStaleTtl(Duration staleTtl) {
        this.staleTtl = staleTtl;
    }

    public Duration alertFreshTtl() {
        return alertFreshTtl;
    }

    public void setAlertFreshTtl(Duration alertFreshTtl) {
        this.alertFreshTtl = alertFreshTtl;
    }

    public Duration alertStaleTtl() {
        return alertStaleTtl;
    }

    public void setAlertStaleTtl(Duration alertStaleTtl) {
        this.alertStaleTtl = alertStaleTtl;
    }

    public double defaultLatitude() {
        return defaultLatitude;
    }

    public void setDefaultLatitude(double defaultLatitude) {
        this.defaultLatitude = defaultLatitude;
    }

    public double defaultLongitude() {
        return defaultLongitude;
    }

    public void setDefaultLongitude(double defaultLongitude) {
        this.defaultLongitude = defaultLongitude;
    }

    public String defaultLocationName() {
        return defaultLocationName;
    }

    public void setDefaultLocationName(String defaultLocationName) {
        this.defaultLocationName = defaultLocationName;
    }
}
