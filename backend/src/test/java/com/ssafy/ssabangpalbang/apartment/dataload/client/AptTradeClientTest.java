package com.ssafy.ssabangpalbang.apartment.dataload.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AptTradeClientTest {

    private static final MediaType XML_UTF_8 =
            new MediaType("application", "xml", StandardCharsets.UTF_8);

    private MockRestServiceServer server;
    private AptTradeClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new AptTradeClient(restTemplate, "test-key", 0);
    }

    @Test
    void parsesTradeXmlAndUsesUppercaseRequestParameters() {
        server.expect(requestTo(allOf(
                        containsString("LAWD_CD=11680"),
                        containsString("DEAL_YMD=202606")
                )))
                .andExpect(header("User-Agent", "ssabangpalbang-dataload/1.0"))
                .andExpect(header("Accept", MediaType.APPLICATION_XML_VALUE))
                .andRespond(withSuccess(response(1, item()), XML_UTF_8));

        var result = client.find("11680", "202606");

        assertThat(result).singleElement().satisfies(trade -> {
            assertThat(trade.aptName()).isEqualTo("삼성동롯데아파트");
            assertThat(trade.aptSeq()).isEqualTo("11680-168");
            assertThat(trade.dealAmount()).isEqualTo("235,000");
            assertThat(trade.roadName()).isEqualTo("학동로");
            assertThat(trade.roadMainNumber()).isEqualTo("00432");
        });
        server.verify();
    }

    @Test
    void requestsNextPageWhenTotalCountExceedsPageItems() {
        server.expect(requestTo(containsString("pageNo=1")))
                .andRespond(withSuccess(response(2, item()), XML_UTF_8));
        server.expect(requestTo(containsString("pageNo=2")))
                .andRespond(withSuccess(response(2, item().replace("11680-168", "11680-169")),
                        XML_UTF_8));

        assertThat(client.find("11680", "202606")).hasSize(2);
        server.verify();
    }

    @Test
    void stopsWhenSecondPageIsEmpty() {
        server.expect(requestTo(containsString("pageNo=1")))
                .andRespond(withSuccess(response(5, item()), XML_UTF_8));
        server.expect(requestTo(containsString("pageNo=2")))
                .andRespond(withSuccess(response(5, ""), XML_UTF_8));

        assertThat(client.find("11680", "202606")).hasSize(1);
        server.verify();
    }

    @Test
    void returnsEmptyListOnServiceKeyError() {
        server.expect(requestTo(containsString("serviceKey=test-key")))
                .andRespond(withSuccess("""
                        <response><header><resultCode>30</resultCode></header></response>
                        """, XML_UTF_8));

        assertThat(client.find("11680", "202606")).isEmpty();
        server.verify();
    }

    @Test
    void returnsEmptyListOnGatewayErrorEnvelope() {
        server.expect(requestTo(containsString("LAWD_CD=11680")))
                .andRespond(withSuccess("""
                        <OpenAPI_ServiceResponse><cmmMsgHeader>
                          <returnAuthMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</returnAuthMsg>
                          <returnReasonCode>22</returnReasonCode>
                        </cmmMsgHeader></OpenAPI_ServiceResponse>
                        """, XML_UTF_8));

        assertThat(client.find("11680", "202606")).isEmpty();
        server.verify();
    }

    @Test
    void returnsEmptyListWhenResultCodeIsMissing() {
        server.expect(requestTo(containsString("LAWD_CD=11680")))
                .andRespond(withSuccess("<response><body></body></response>", XML_UTF_8));

        assertThat(client.find("11680", "202606")).isEmpty();
        server.verify();
    }

    private String response(int totalCount, String items) {
        return """
                <response>
                  <header><resultCode>000</resultCode></header>
                  <body><items>%s</items><totalCount>%d</totalCount></body>
                </response>
                """.formatted(items, totalCount);
    }

    private String item() {
        return """
                <item>
                  <aptNm>삼성동롯데아파트</aptNm><aptSeq>11680-168</aptSeq>
                  <dealYear>2026</dealYear><dealMonth>6</dealMonth><dealDay>4</dealDay>
                  <dealAmount>235,000</dealAmount><excluUseAr>59.4</excluUseAr><floor>2</floor>
                  <cdealType> </cdealType><roadNm>학동로</roadNm>
                  <roadNmBonbun>00432</roadNmBonbun><roadNmBubun>00000</roadNmBubun>
                  <sggCd>11680</sggCd><umdCd>10500</umdCd>
                </item>
                """;
    }
}
