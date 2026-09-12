package com.ssafy.ssabangpalbang.apartment.dataload.client;

import com.ssafy.ssabangpalbang.apartment.dataload.DataLoadProperties;
import com.ssafy.ssabangpalbang.apartment.dataload.dto.AptTradeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("dataload-tx")
public class AptTradeClient {

    private static final Logger log = LoggerFactory.getLogger(AptTradeClient.class);
    private static final String URL =
            "https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev";
    private static final String USER_AGENT = "ssabangpalbang-dataload/1.0";
    private static final int PAGE_SIZE = 1000;

    private final RestTemplate restTemplate;
    private final String serviceKey;
    private final long delayMs;
    private final AtomicInteger callCount = new AtomicInteger();
    private int lastTotalCount;

    @Autowired
    public AptTradeClient(
            RestTemplate restTemplate,
            @Value("${public-data.service-key:}") String serviceKey,
            DataLoadProperties properties
    ) {
        this(restTemplate, serviceKey, properties.getRequestDelayMs());
    }

    public AptTradeClient(RestTemplate restTemplate, String serviceKey, long delayMs) {
        this.restTemplate = restTemplate;
        this.serviceKey = serviceKey;
        this.delayMs = delayMs;
    }

    public List<AptTradeItem> find(String sigunguCode, String dealYearMonth) {
        List<AptTradeItem> result = new ArrayList<>();
        lastTotalCount = 0;
        int page = 1;
        while (true) {
            Document document = get(uri(sigunguCode, dealYearMonth, page));
            if (document == null || isError(document)) {
                return List.of();
            }
            lastTotalCount = parseInt(text(document.getDocumentElement(), "totalCount"), 0);
            int before = result.size();
            NodeList items = document.getElementsByTagName("item");
            for (int index = 0; index < items.getLength(); index++) {
                Node node = items.item(index);
                if (node instanceof Element element) {
                    result.add(toItem(element));
                }
            }
            if (result.size() == before || result.size() >= lastTotalCount) {
                break;
            }
            page++;
        }
        return result;
    }

    public int getLastTotalCount() {
        return lastTotalCount;
    }

    public int getCallCount() {
        return callCount.get();
    }

    private Document get(URI uri) {
        try {
            callCount.incrementAndGet();
            RequestEntity<Void> request = RequestEntity.get(uri)
                    .accept(MediaType.APPLICATION_XML)
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .build();
            String xml = restTemplate.exchange(request, String.class).getBody();
            if (xml == null || xml.isBlank()) {
                return null;
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (RestClientException exception) {
            log.warn("[dataload-tx] 실거래 API 조회 실패: {}", exception.getMessage());
            return null;
        } catch (Exception exception) {
            log.warn("[dataload-tx] 실거래 XML 파싱 실패: {}", exception.getMessage());
            return null;
        } finally {
            delay();
        }
    }

    private boolean isError(Document document) {
        Element root = document.getDocumentElement();
        String authMessage = text(root, "returnAuthMsg");
        if (authMessage != null) {
            log.warn("[dataload-tx] 공공데이터포털 오류: {} (reasonCode={})",
                    authMessage, text(root, "returnReasonCode"));
            return true;
        }

        String resultCode = text(root, "resultCode");
        if (resultCode == null) {
            log.warn("[dataload-tx] 실거래 API 응답에 resultCode가 없습니다.");
            return true;
        }
        boolean error = !"00".equals(resultCode) && !"000".equals(resultCode);
        if (error) {
            log.warn("[dataload-tx] 실거래 API 오류: {}", resultCode);
        }
        return error;
    }

    private AptTradeItem toItem(Element item) {
        return new AptTradeItem(
                text(item, "aptNm"), text(item, "aptSeq"),
                text(item, "dealYear"), text(item, "dealMonth"), text(item, "dealDay"),
                text(item, "dealAmount"), text(item, "excluUseAr"), text(item, "floor"),
                text(item, "cdealType"), text(item, "roadNm"),
                text(item, "roadNmBonbun"), text(item, "roadNmBubun"),
                text(item, "sggCd"), text(item, "umdCd")
        );
    }

    private String text(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private URI uri(String sigunguCode, String dealYearMonth, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(URL)
                .queryParam("LAWD_CD", sigunguCode)
                .queryParam("DEAL_YMD", dealYearMonth)
                .queryParam("pageNo", page)
                .queryParam("numOfRows", PAGE_SIZE);
        return AptListClient.PublicDataUri.build(builder, serviceKey);
    }

    private void delay() {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
