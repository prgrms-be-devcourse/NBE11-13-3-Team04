package com.example.iter.reservation.service;

import com.example.iter.common.exception.CustomException;
import com.example.iter.common.exception.ErrorCode;
import com.example.iter.common.dto.request.CapturedImageRequest;
import com.example.iter.common.image.CaptureView;
import com.example.iter.common.storage.S3StorageProperties;
import com.example.iter.reservation.domain.entity.Rental;
import com.example.iter.reservation.domain.entity.EvidenceUploadPhase;
import com.example.iter.reservation.domain.entity.RentalEvidenceUpload;
import com.example.iter.reservation.api.RentalStatus;
import com.example.iter.reservation.domain.repository.RentalEvidenceUploadRepository;
import com.example.iter.reservation.domain.repository.RentalRepository;
import com.example.iter.reservation.dto.request.EvidenceImagePresignRequest;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RentalEvidenceUploadServiceTest {

    // Long 캐시 범위(일반적으로 -128~127) 밖의 값으로 식별자 값 비교 회귀를 검증한다.
    private static final Long USER_ID = 1_001L;
    private static final Long RENTAL_ID = 10_001L;

    @Test
    void 대여자에게_비공개_증빙_업로드_URL을_발급한다() {
        S3StorageProperties properties = properties();
        RentalRepository rentalRepository = mock(RentalRepository.class);
        RentalEvidenceUploadRepository uploadRepository = mock(RentalEvidenceUploadRepository.class);
        S3Client s3Client = mock(S3Client.class);

        when(rentalRepository.findWithLockById(RENTAL_ID)).thenReturn(Optional.of(rental(USER_ID)));
        when(uploadRepository.countByRentalIdAndUserIdAndPhase(
                RENTAL_ID,
                USER_ID,
                EvidenceUploadPhase.RECEIPT
        )).thenReturn(0L);

        // 실제 서명 코드만 검증한다. 이 테스트용 자격 증명으로 AWS에 요청하지 않는다.
        try (S3Presigner presigner = testPresigner()) {
            RentalEvidenceUploadService service = new RentalEvidenceUploadService(
                    s3Client,
                    presigner,
                    properties,
                    rentalRepository,
                    uploadRepository
            );
            var request = new EvidenceImagePresignRequest(List.of(
                    new EvidenceImagePresignRequest.Item(CaptureView.FRONT, "image/jpeg", 1024),
                    new EvidenceImagePresignRequest.Item(CaptureView.SIDE, "image/jpeg", 2048)
            ));

            var uploads = service.createPresignedUploads(USER_ID, RENTAL_ID, request).uploads();

            assertThat(uploads).hasSize(2);
            assertThat(uploads.get(0).objectKey()).isNotEqualTo(uploads.get(1).objectKey());
            for (var upload : uploads) {
                assertThat(upload.objectKey())
                        .startsWith("equipment/private/rental-evidence/10001/1001/receipt/")
                        .endsWith(".jpg");
                assertThat(upload.requiredHeaders())
                        .containsEntry("Content-Type", "image/jpeg")
                        .containsEntry("If-None-Match", "*");

                String query = URLDecoder.decode(
                        upload.uploadUrl().split("\\?", 2)[1],
                        StandardCharsets.UTF_8
                );
                assertThat(query).containsPattern("X-Amz-SignedHeaders=[^&]*if-none-match");
                assertThat(upload.viewUrl()).contains("X-Amz-Signature=");
            }
            verify(uploadRepository, org.mockito.Mockito.times(2)).save(any());
        }
    }

    @Test
    void 거래_대여자가_아니면_URL을_발급하지_않는다() {
        RentalRepository rentalRepository = mock(RentalRepository.class);
        RentalEvidenceUploadRepository uploadRepository = mock(RentalEvidenceUploadRepository.class);
        when(rentalRepository.findWithLockById(RENTAL_ID)).thenReturn(Optional.of(rental(2L)));

        try (S3Presigner presigner = testPresigner()) {
            RentalEvidenceUploadService service = new RentalEvidenceUploadService(
                    mock(S3Client.class),
                    presigner,
                    properties(),
                    rentalRepository,
                    uploadRepository
            );
            var request = new EvidenceImagePresignRequest(List.of(
                    new EvidenceImagePresignRequest.Item(CaptureView.FRONT, "image/jpeg", 1024)
            ));

            assertThatThrownBy(() -> service.createPresignedUploads(USER_ID, RENTAL_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    @Test
    void 단계별_발급_한도를_넘으면_거절한다() {
        RentalRepository rentalRepository = mock(RentalRepository.class);
        RentalEvidenceUploadRepository uploadRepository = mock(RentalEvidenceUploadRepository.class);
        when(rentalRepository.findWithLockById(RENTAL_ID)).thenReturn(Optional.of(rental(USER_ID)));
        when(uploadRepository.countByRentalIdAndUserIdAndPhase(
                RENTAL_ID,
                USER_ID,
                EvidenceUploadPhase.RECEIPT
        )).thenReturn(9L);

        try (S3Presigner presigner = testPresigner()) {
            RentalEvidenceUploadService service = new RentalEvidenceUploadService(
                    mock(S3Client.class),
                    presigner,
                    properties(),
                    rentalRepository,
                    uploadRepository
            );
            var request = new EvidenceImagePresignRequest(List.of(
                    new EvidenceImagePresignRequest.Item(CaptureView.FRONT, "image/jpeg", 1024)
            ));

            assertThatThrownBy(() -> service.createPresignedUploads(USER_ID, RENTAL_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EVIDENCE_UPLOAD_LIMIT_EXCEEDED);
        }
    }

    @Test
    void 이_대여에_발급되고_S3에_업로드된_키만_한_번_사용한다() {
        RentalEvidenceUploadRepository uploadRepository = mock(RentalEvidenceUploadRepository.class);
        S3Client s3Client = mock(S3Client.class);
        List<CapturedImageRequest> images = List.of(
                capturedImage(CaptureView.FRONT),
                capturedImage(CaptureView.SIDE),
                capturedImage(CaptureView.REAR)
        );
        List<RentalEvidenceUpload> uploads = images.stream()
                .map(image -> new RentalEvidenceUpload(
                        RENTAL_ID,
                        USER_ID,
                        EvidenceUploadPhase.RECEIPT,
                        image.objectKey(),
                        image.captureView(),
                        "image/jpeg",
                        1024,
                        LocalDateTime.now().plusMinutes(5)
                ))
                .toList();
        when(uploadRepository.findAllByObjectKeyInForUpdate(
                images.stream().map(CapturedImageRequest::objectKey).toList()))
                .thenReturn(uploads);
        when(s3Client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder()
                        .contentType("image/jpeg")
                        .contentLength(1024L)
                        .build());

        try (S3Presigner presigner = testPresigner()) {
            RentalEvidenceUploadService service = new RentalEvidenceUploadService(
                    s3Client,
                    presigner,
                    properties(),
                    mock(RentalRepository.class),
                    uploadRepository
            );

            service.validateAndUse(
                    USER_ID,
                    RENTAL_ID,
                    EvidenceUploadPhase.RECEIPT,
                    images
            );

            assertThat(uploads).allMatch(RentalEvidenceUpload::isUsed);
            assertThatThrownBy(() -> service.validateAndUse(
                    USER_ID,
                    RENTAL_ID,
                    EvidenceUploadPhase.RECEIPT,
                    images
            )).isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EVIDENCE_UPLOAD_ALREADY_USED);
        }
    }

    private CapturedImageRequest capturedImage(CaptureView view) {
        String objectKey = "equipment/private/rental-evidence/10001/1001/receipt/%s/photo.jpg"
                .formatted(view.name().toLowerCase(java.util.Locale.ROOT));
        return new CapturedImageRequest(view, objectKey);
    }

    private Rental rental(Long renterId) {
        return new Rental(
                100L,
                999L,
                renterId,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10),
                "테스트 장비",
                BigDecimal.valueOf(10000),
                9,
                BigDecimal.valueOf(90000),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                RentalStatus.SHIPPING,
                null,
                null,
                RENTAL_ID);
    }

    private S3StorageProperties properties() {
        S3StorageProperties properties = new S3StorageProperties();
        properties.setBucket("iter-evidence-test");
        properties.setRegion("ap-northeast-2");
        return properties;
    }

    private S3Presigner testPresigner() {
        return S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")
                ))
                .build();
    }
}
