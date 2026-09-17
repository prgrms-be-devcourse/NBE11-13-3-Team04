-- 기존 사진은 실제 촬영 방향을 보장할 수 없으므로 nullable로 유지합니다.
alter table equipment_image add column capture_view varchar(16) null;
alter table equipment_image_upload add column capture_view varchar(16) null;
alter table receipt_image add column capture_view varchar(16) null;
alter table return_receipt_image add column capture_view varchar(16) null;

-- 신규 장비·수령·반납 사진은 부모 안에서 정면·측면·후면을 한 장씩만 가질 수 있습니다.
-- MySQL은 null을 서로 다른 값으로 취급하므로 기존 미분류 사진 여러 장은 보존됩니다.
create unique index uk_equipment_image_capture_view
    on equipment_image (equipment_id, capture_view);

create unique index uk_receipt_image_capture_view
    on receipt_image (receipt_id, capture_view);

create unique index uk_return_receipt_image_capture_view
    on return_receipt_image (return_receipt_id, capture_view);

-- 발급 주체·대여·단계를 저장해 임의 object key 제출 및 한 키의 재사용을 차단합니다.
create table rental_evidence_upload (
    id bigint not null auto_increment,
    created_at datetime(6),
    rental_id bigint not null,
    user_id bigint not null,
    phase varchar(10) not null,
    object_key varchar(500) not null,
    capture_view varchar(16) not null,
    expected_content_type varchar(50) not null,
    expected_size bigint not null,
    upload_url_expires_at datetime(6) not null,
    used_at datetime(6),
    primary key (id)
) engine=InnoDB;

alter table rental_evidence_upload
    add constraint uk_rental_evidence_upload_object_key unique (object_key);

create index idx_rental_evidence_upload_scope
    on rental_evidence_upload (rental_id, user_id, phase);
