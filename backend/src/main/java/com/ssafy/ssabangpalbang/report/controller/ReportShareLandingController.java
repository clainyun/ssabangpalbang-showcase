package com.ssafy.ssabangpalbang.report.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "리포트 공유", description = "외부 공유 링크에서 Android 앱으로 연결")
public class ReportShareLandingController {

    private static final long MAX_SAFE_REPORT_ID = 9_007_199_254_740_991L;
    private static final String DELIVERY_HEADER = "X-Report-Share-Delivery";
    private static final String DELIVERY_VALUE = "backend-fallback";
    private static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; "
                    + "form-action 'none'; frame-ancestors 'none'";
    private static final String PAGE_TEMPLATE = """
            <!doctype html>
            <html lang="ko" data-share-delivery="backend-fallback">
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <meta name="robots" content="noindex, nofollow, noarchive" />
                <title>싸방팔방 임장 리포트</title>
                <meta property="og:type" content="website" />
                <meta property="og:site_name" content="싸방팔방" />
                <meta property="og:locale" content="ko_KR" />
                <meta property="og:title" content="싸방팔방 임장 리포트" />
                <meta property="og:description"
                      content="싸방팔방 앱에서 함께 만든 임장 리포트를 확인해 보세요." />
                <meta property="og:url" content="https://portfolio.example.com/report/%1$d" />
                <link rel="canonical" href="https://portfolio.example.com/report/%1$d" />
                <style>
                  :root { color-scheme: light; font-family: sans-serif; color: #111827; }
                  body { display: grid; min-height: 100vh; margin: 0; padding: 24px;
                         box-sizing: border-box; place-items: center; background: #f4f7f6; }
                  main { width: min(100%%, 440px); padding: 40px 28px; box-sizing: border-box;
                         border: 1px solid #e5e7eb; border-radius: 24px; background: #fff;
                         text-align: center; box-shadow: 0 20px 50px rgb(15 23 42 / 8%%); }
                  .mark { width: 72px; height: 72px; margin: 0 auto 24px; border-radius: 24px;
                          display: grid; place-items: center; background: #d1fae5;
                          color: #087a59; font-size: 34px; font-weight: 900; }
                  h1 { margin: 0 0 12px; font-size: 26px; }
                  p { margin: 0 0 28px; color: #4b5563; line-height: 1.65; }
                  a { display: inline-flex; min-height: 50px; align-items: center;
                      justify-content: center; padding: 0 26px; border-radius: 999px;
                      background: #21c58b; color: #fff; font-weight: 800;
                      text-decoration: none; }
                  a:focus-visible { outline: 3px solid #99f6d5; outline-offset: 3px; }
                </style>
              </head>
              <body>
                <main>
                  <div class="mark" aria-hidden="true">8</div>
                  <h1>임장 리포트가 도착했어요</h1>
                  <p>리포트 내용은 로그인한 싸방팔방 앱에서 안전하게 확인할 수 있어요.</p>
                  <a href="intent://portfolio.example.com/open/report/%1$d#Intent;scheme=https;package=com.ssafy.ssabangpalbang;end">
                    싸방팔방 앱에서 열기
                  </a>
                </main>
              </body>
            </html>
            """;

    @GetMapping(value = "/report/{reportId}", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(
            summary = "리포트 공유 앱 연결 페이지",
            description = "리포트 데이터를 노출하지 않고 Android 앱의 "
                    + "인증된 리포트 화면으로 연결하는 공개 HTML 페이지입니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "앱 연결 페이지 반환",
                    content = @Content(
                            mediaType = MediaType.TEXT_HTML_VALUE,
                            schema = @Schema(implementation = String.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "리포트 ID 형식이 올바르지 않음",
                    content = @Content
            )
    })
    public ResponseEntity<String> getLanding(
            @Parameter(
                    description = "앱에서 조회할 리포트 ID",
                    required = true,
                    example = "48"
            )
            @PathVariable String reportId
    ) {
        Long parsedReportId = parseReportId(reportId);
        if (parsedReportId == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .header(DELIVERY_HEADER, DELIVERY_VALUE)
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .header("X-Robots-Tag", "noindex, nofollow, noarchive")
                .body(PAGE_TEMPLATE.formatted(parsedReportId));
    }

    private Long parseReportId(String reportId) {
        try {
            long parsedReportId = Long.parseLong(reportId);
            if (parsedReportId >= 1 && parsedReportId <= MAX_SAFE_REPORT_ID) {
                return parsedReportId;
            }
        } catch (NumberFormatException ignored) {
            // Invalid public links deliberately collapse to a plain 404.
        }
        return null;
    }
}
