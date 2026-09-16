package com.example.iter.reservation.domain.entity;

import com.example.iter.common.entity.BaseCreatedAtEntity;
import com.example.iter.common.image.CaptureView;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "return_receipt_image",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_return_receipt_image_capture_view",
                columnNames = {"return_receipt_id", "capture_view"}
        )
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ReturnReceiptImage extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_receipt_id", nullable = false)
    private ReturnReceipt returnReceipt;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_view", length = 16)
    private CaptureView captureView;

    @Column(name = "sort_order")
    private int sortOrder;
}
