package com.example.iter.reservation.service;

import com.example.iter.common.dto.request.CapturedImageRequest;
import com.example.iter.common.exception.CustomException;
import com.example.iter.common.exception.ErrorCode;
import com.example.iter.common.image.CaptureView;
import com.example.iter.common.storage.S3StorageProperties;
import com.example.iter.reservation.domain.entity.EvidenceUploadPhase;
import com.example.iter.reservation.domain.entity.Rental;
import com.example.iter.reservation.domain.entity.RentalEvidenceUpload;
import com.example.iter.reservation.api.RentalStatus;
import com.example.iter.reservation.domain.repository.RentalEvidenceUploadRepository;
import com.example.iter.reservation.domain.repository.RentalRepository;
import com.example.iter.reservation.dto.request.EvidenceImagePresignRequest;
import com.example.iter.reservation.dto.response.EvidenceImagePresignResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// 수령·반납 증빙 사진의 비공개 S3 업로드 권한과 조회용 임시 URL을 관리합니다.
@Service
@RequiredArgsConstructor
public class RentalEvidenceUploadService {

    public static final int REQUIRED_IMAGES_PER_SUBMISSION = 3;
    public static final int MAX_UPLOADS_PER_PHASE = 9;
    public static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;

    private static final String PRIVATE_EVIDENCE_DIR = "private/rental-evidence";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3StorageProperties properties;
    private final RentalRepository rentalRepository;
    private final RentalEvidenceUploadRepository uploadRepository;

    // 로그인한 대여자와 거래 상태를 확인한 뒤 현재 단계 전용 업로드 URL을 발급합니다.
    @Transactional
    public EvidenceImagePresignResponse createPresignedUploads(
            Long userId,
            Long rentalId,
            EvidenceImagePresignRequest request
    ) {
        Rental rental = rentalRepository.findWithLockById(rentalId)
                .orElseThrow(() -> new CustomException(ErrorCode.RENTAL_NOT_FOUND));

        if (!rental.isRenter(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        EvidenceUploadPhase phase = phaseFor(rental.getStatus());
        validateIssuanceLimit(rentalId, userId, phase, request.files().size());

        LocalDateTime expiresAt = LocalDateTime.now().plus(properties.getPresignedUrlValidity());
        List<EvidenceImagePresignResponse.Item> items = new ArrayList<>();

        for (EvidenceImagePresignRequest.Item file : request.files()) {
            validateMetadata(file);
            items.add(presignOne(userId, rentalId, phase, file, expiresAt));
        }

        return new EvidenceImagePresignResponse(List.copyOf(items));
    }

    // 제출된 키가 이 대여와 사용자에게 발급됐고 실제 S3 업로드까지 끝났는지 검사한 뒤 사용 처리합니다.
    @Transactional
    public void validateAndUse(
            Long userId,
            Long rentalId,
            EvidenceUploadPhase phase,
            List<CapturedImageRequest> images
    ) {
        if (!hasRequiredViews(images)) {
            throw new CustomException(ErrorCode.EVIDENCE_IMAGE_INVALID);
        }

        List<String> objectKeys = images.stream()
                .map(CapturedImageRequest::objectKey)
                .toList();
        List<RentalEvidenceUpload> uploads = uploadRepository.findAllByObjectKeyInForUpdate(objectKeys);
        Map<String, RentalEvidenceUpload> uploadsByKey = uploads.stream()
                .collect(Collectors.toMap(RentalEvidenceUpload::getObjectKey, upload -> upload));

        for (CapturedImageRequest image : images) {
            RentalEvidenceUpload upload = uploadsByKey.get(image.objectKey());
            validateUploadScope(upload, userId, rentalId, phase, image.captureView());
            validateUploadedObject(upload);
            upload.use(LocalDateTime.now());
        }
    }

    // DB에는 비공개 object key만 저장하고, 화면 조회 시점에 짧게 유효한 GET URL을 만듭니다.
    public String createReadUrl(String storedImageReference) {
        String privatePrefix = normalizedPrefix() + "/" + PRIVATE_EVIDENCE_DIR + "/";
        if (storedImageReference == null || !storedImageReference.startsWith(privatePrefix)) {
            // 기존 공개 URL 데이터는 단계적 마이그레이션 동안 그대로 읽을 수 있게 유지합니다.
            return storedImageReference;
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(storedImageReference)
                .build();

        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(properties.getPresignedUrlValidity())
                        .getObjectRequest(getObjectRequest)
                        .build())
                .url()
                .toString();
    }

    // 객체마다 고유한 비공개 키를 만들고 Content-Type, 길이, 덮어쓰기 방지 조건을 서명합니다.
    private EvidenceImagePresignResponse.Item presignOne(
            Long userId,
            Long rentalId,
            EvidenceUploadPhase phase,
            EvidenceImagePresignRequest.Item file,
            LocalDateTime expiresAt
    ) {
        String objectKey = "%s/%s/%d/%d/%s/%s/%s.%s".formatted(
                normalizedPrefix(),
                PRIVATE_EVIDENCE_DIR,
                rentalId,
                userId,
                phase.name().toLowerCase(),
                file.captureView().name().toLowerCase(),
                UUID.randomUUID(),
                extensionFor(file.contentType())
        );

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(file.contentType())
                .contentLength(file.size())
                .ifNoneMatch("*")
                .build();

        String uploadUrl = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(properties.getPresignedUrlValidity())
                        .putObjectRequest(putObjectRequest)
                        .build())
                .url()
                .toString();

        uploadRepository.save(new RentalEvidenceUpload(
                rentalId,
                userId,
                phase,
                objectKey,
                file.captureView(),
                file.contentType(),
                file.size(),
                expiresAt
        ));

        return new EvidenceImagePresignResponse.Item(
                file.captureView(),
                objectKey,
                uploadUrl,
                Map.of("Content-Type", file.contentType(), "If-None-Match", "*"),
                createReadUrl(objectKey),
                expiresAt
        );
    }

    private void validateIssuanceLimit(
            Long rentalId,
            Long userId,
            EvidenceUploadPhase phase,
            int requestedCount
    ) {
        long issuedCount = uploadRepository.countByRentalIdAndUserIdAndPhase(
                rentalId, userId, phase);
        if (issuedCount + requestedCount > MAX_UPLOADS_PER_PHASE) {
            throw new CustomException(ErrorCode.EVIDENCE_UPLOAD_LIMIT_EXCEEDED);
        }
    }

    private EvidenceUploadPhase phaseFor(RentalStatus status) {
        return switch (status) {
            case SHIPPING -> EvidenceUploadPhase.RECEIPT;
            case RETURN_REQUESTED -> EvidenceUploadPhase.RETURN;
            default -> throw new CustomException(ErrorCode.EVIDENCE_UPLOAD_NOT_ALLOWED);
        };
    }

    private void validateMetadata(EvidenceImagePresignRequest.Item file) {
        boolean allowedType = List.of("image/jpeg", "image/png", "image/webp")
                .contains(file.contentType());
        if (!allowedType || file.size() <= 0 || file.size() > MAX_IMAGE_SIZE) {
            throw new CustomException(ErrorCode.EVIDENCE_IMAGE_INVALID);
        }
    }

    private void validateUploadScope(
            RentalEvidenceUpload upload,
            Long userId,
            Long rentalId,
            EvidenceUploadPhase phase,
            CaptureView captureView
    ) {
        if (upload == null
                || !Objects.equals(upload.getUserId(), userId)
                || !Objects.equals(upload.getRentalId(), rentalId)
                || upload.getPhase() != phase
                || upload.getCaptureView() != captureView) {
            throw new CustomException(ErrorCode.EVIDENCE_UPLOAD_NOT_FOUND);
        }
        if (upload.isUsed()) {
            throw new CustomException(ErrorCode.EVIDENCE_UPLOAD_ALREADY_USED);
        }
    }

    private boolean hasRequiredViews(List<CapturedImageRequest> images) {
        if (images == null || images.size() != REQUIRED_IMAGES_PER_SUBMISSION) {
            return false;
        }
        Set<CaptureView> views = images.stream()
                .map(CapturedImageRequest::captureView)
                .collect(Collectors.toSet());
        Set<String> keys = images.stream()
                .map(CapturedImageRequest::objectKey)
                .collect(Collectors.toSet());
        return views.equals(Set.of(CaptureView.values())) && keys.size() == images.size();
    }

    // S3 HEAD 결과를 발급 당시 메타데이터와 비교해 미업로드·변조된 객체 제출을 막습니다.
    private void validateUploadedObject(RentalEvidenceUpload upload) {
        try {
            var metadata = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(upload.getObjectKey())
                    .build());

            if (metadata.contentLength() != upload.getExpectedSize()
                    || !upload.getExpectedContentType().equals(metadata.contentType())) {
                throw new CustomException(ErrorCode.EVIDENCE_IMAGE_INVALID);
            }
        } catch (CustomException exception) {
            throw exception;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new CustomException(ErrorCode.EVIDENCE_UPLOAD_NOT_FOUND);
            }
            throw new CustomException(ErrorCode.EVIDENCE_UPLOAD_FAILED);
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.EVIDENCE_UPLOAD_FAILED);
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new CustomException(ErrorCode.EVIDENCE_IMAGE_INVALID);
        };
    }

    private String normalizedPrefix() {
        String prefix = properties.getKeyPrefix().trim();
        while (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }
}
