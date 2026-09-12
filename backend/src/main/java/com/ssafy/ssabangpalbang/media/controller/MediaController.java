package com.ssafy.ssabangpalbang.media.controller;

import com.ssafy.ssabangpalbang.auth.security.AuthenticatedMember;
import com.ssafy.ssabangpalbang.global.response.ApiResponse;
import com.ssafy.ssabangpalbang.media.dto.request.MediaPrepareRequest;
import com.ssafy.ssabangpalbang.media.dto.response.MediaDeleteResponse;
import com.ssafy.ssabangpalbang.media.dto.response.MediaFileResponse;
import com.ssafy.ssabangpalbang.media.dto.response.MediaPrepareResponse;
import com.ssafy.ssabangpalbang.media.response.MediaResponseCode;
import com.ssafy.ssabangpalbang.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/media")
@Tag(name = "미디어", description = "비공개 파일 업로드와 접근 URL 관리")
@SecurityRequirement(name = "bearerAuth")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/presigned-urls")
    @Operation(summary = "업로드 URL 발급")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "업로드 URL 발급 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청값 오류, 지원하지 않는 파일 사용 목적·형식"
                            + " 또는 파일 크기 초과"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "해당 스터디에 파일을 업로드할 권한이 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "스터디를 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "파일 저장소에 일시적으로 연결할 수 없음"
            )
    })
    public ResponseEntity<ApiResponse<MediaPrepareResponse>> prepare(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody MediaPrepareRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        MediaResponseCode.MEDIA_PRESIGNED_URL_ISSUED,
                        mediaService.prepare(member.memberId(), request)
                ));
    }

    @PostMapping("/{fileId}/complete")
    @Operation(summary = "업로드 검증 및 완료 처리")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "업로드 완료 처리 성공(이미 완료된 파일 포함)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "fileId 형식 오류 또는 업로드된 파일의"
                            + " 크기·형식이 요청 정보와 불일치"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "파일 소유자가 아님, 삭제된 파일 또는"
                            + " 스터디 업로드 권한 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "파일 또는 스터디를 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "업로드 미확인, 만료, 실패 처리됨 또는"
                            + " 검증 중 파일 변경"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "파일 저장소에 일시적으로 연결할 수 없음"
            )
    })
    public ResponseEntity<ApiResponse<MediaFileResponse>> complete(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable @Positive Long fileId
    ) {
        MediaService.CompleteResult result =
                mediaService.complete(member.memberId(), fileId);
        return ResponseEntity.ok(ApiResponse.success(
                result.alreadyCompleted()
                        ? MediaResponseCode
                                .MEDIA_UPLOAD_ALREADY_COMPLETED
                        : MediaResponseCode.MEDIA_UPLOAD_COMPLETED,
                result.response()
        ));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "파일 정보와 임시 접근 URL 조회")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "파일 정보 조회 성공(STT 음성은 접근 URL 미발급)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "fileId 형식 오류"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "파일 접근 권한 없음 또는 완료되지 않았거나 삭제된 파일"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "파일을 찾을 수 없음"
            )
    })
    public ResponseEntity<ApiResponse<MediaFileResponse>> get(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable @Positive Long fileId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                MediaResponseCode.MEDIA_FILE_GET_SUCCESS,
                mediaService.get(member.memberId(), fileId)
        ));
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "파일 삭제")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "파일 삭제 성공(이미 삭제된 파일 포함)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "fileId 형식 오류"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Access Token이 없거나 올바르지 않음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "파일 소유자가 아니거나 완료되지 않은 파일"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "파일을 찾을 수 없음"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "파일 저장소에 일시적으로 연결할 수 없음"
            )
    })
    public ResponseEntity<ApiResponse<MediaDeleteResponse>> delete(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable @Positive Long fileId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                MediaResponseCode.MEDIA_FILE_DELETE_SUCCESS,
                mediaService.delete(member.memberId(), fileId)
        ));
    }
}
