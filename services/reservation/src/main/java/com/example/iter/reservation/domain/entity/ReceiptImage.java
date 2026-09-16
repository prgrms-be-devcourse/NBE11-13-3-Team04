package com.example.iter.reservation.domain.entity;

import com.example.iter.common.entity.BaseCreatedAtEntity;
import com.example.iter.common.image.CaptureView;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "receipt_image",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_receipt_image_capture_view",
                columnNames = {"receipt_id", "capture_view"}
        )
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ReceiptImage extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private Receipt receipt;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_view", length = 16)
    private CaptureView captureView;

    @Column(name = "sort_order")
    private int sortOrder;
}
