-- 초기 스키마. 이 시점까지는 Hibernate 의 ddl-auto 가 스키마를 만들어 왔고,
-- 변경 이력이 아무 데도 남지 않았다. 여기서부터 Flyway 가 관리한다.
--
-- 생성 방법: 현재 엔티티 25개에서 Hibernate 스키마 생성 기능으로 뽑았다
--   (jakarta.persistence.schema-generation, MySQLDialect).
-- 손으로 쓴 것이 아니므로 엔티티와 어긋날 여지가 없다.
--
-- !! 이 파일을 수정하지 말 것 !!
-- 이미 적용된 마이그레이션을 고치면 Flyway 가 체크섬 불일치로 기동을 거부한다.
-- 스키마를 바꾸려면 V2, V3 ... 을 새로 추가한다.


create table admin_action (
    admin_id bigint not null,
    created_at datetime(6),
    id bigint not null auto_increment,
    target_id bigint not null,
    reason TEXT,
    action enum ('REJECT_REPORT','RESOLVE_DISPUTE','RESOLVE_REPORT','RESTORE_EQUIPMENT','RESTORE_USER','REVIEW_REPORT','SUSPEND_EQUIPMENT','SUSPEND_USER') not null,
    target_type enum ('DISPUTE','EQUIPMENT','REPORT','USER') not null,
    primary key (id)
) engine=InnoDB;

create table dispute (
    created_at datetime(6),
    id bigint not null auto_increment,
    rental_id bigint not null,
    reporter_id bigint not null,
    resolved_at datetime(6),
    respondent_id bigint not null,
    updated_at datetime(6),
    reason varchar(50) not null,
    admin_memo TEXT,
    description TEXT,
    fault_party enum ('BOTH','NONE','OWNER','RENTER'),
    status enum ('INVESTIGATING','REJECTED','REPORTED','RESOLVED') not null,
    primary key (id)
) engine=InnoDB;

create table dispute_image (
    sort_order integer,
    created_at datetime(6),
    dispute_id bigint not null,
    id bigint not null auto_increment,
    image_url varchar(500) not null,
    primary key (id)
) engine=InnoDB;

create table dispute_response (
    created_at datetime(6),
    dispute_id bigint not null,
    id bigint not null auto_increment,
    updated_at datetime(6),
    user_id bigint not null,
    content TEXT not null,
    primary key (id)
) engine=InnoDB;

create table equipment (
    available_from date,
    available_to date,
    daily_price decimal(10,0) not null,
    created_at datetime(6),
    id bigint not null auto_increment,
    owner_id bigint not null,
    updated_at datetime(6),
    name varchar(100) not null,
    condition_detail TEXT,
    description TEXT,
    category enum ('CAMERA','GAME_CONSOLE','LAPTOP','LENS','MONITOR','OTHER','PROJECTOR','TABLET','VR') not null,
    product_condition enum ('DAMAGED','DIRTY','MISSING_PART','NORMAL','OTHER') not null,
    status enum ('ACTIVE','DELETED','INACTIVE','MAINTENANCE','SUSPENDED') not null,
    primary key (id)
) engine=InnoDB;

create table equipment_image (
    is_thumbnail bit,
    sort_order integer,
    created_at datetime(6),
    equipment_id bigint not null,
    id bigint not null auto_increment,
    image_url varchar(500) not null,
    object_key varchar(500),
    primary key (id)
) engine=InnoDB;

create table equipment_image_upload (
    created_at datetime(6),
    expected_size bigint not null,
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    used_at datetime(6),
    user_id bigint not null,
    expected_content_type varchar(50) not null,
    object_key varchar(500) not null,
    primary key (id)
) engine=InnoDB;

create table notification (
    is_read bit not null,
    created_at datetime(6),
    id bigint not null auto_increment,
    read_at datetime(6),
    receiver_id bigint not null,
    rental_id bigint not null,
    params TEXT not null,
    type enum ('PAYMENT_COMPLETED_OWNER','PAYMENT_COMPLETED_RENTER','RENTAL_APPROVED','RENTAL_CANCELED','RENTAL_RECEIVED','RENTAL_REJECTED','RENTAL_REQUESTED','REVIEW_RECEIVED') not null,
    primary key (id)
) engine=InnoDB;

create table oauth_account (
    created_at datetime(6),
    id bigint not null auto_increment,
    updated_at datetime(6),
    user_id bigint not null,
    provider_user_id varchar(100) not null,
    provider enum ('KAKAO') not null,
    primary key (id)
) engine=InnoDB;

create table oauth_pending_token (
    created_at datetime(6),
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    target_user_id bigint,
    used_at datetime(6),
    version bigint not null,
    nickname varchar(20),
    token_hash varchar(64) not null,
    email varchar(100),
    provider_user_id varchar(100) not null,
    provider enum ('KAKAO') not null,
    purpose enum ('LOGIN_EXCHANGE','SIGNUP_OR_LINK') not null,
    primary key (id)
) engine=InnoDB;

create table payment (
    amount decimal(38,2) not null,
    created_at datetime(6),
    id bigint not null auto_increment,
    paid_at datetime(6),
    refunded_at datetime(6),
    rental_id bigint not null,
    updated_at datetime(6),
    cancel_idempotency_key varchar(255),
    idempotency_key varchar(255),
    order_id varchar(255),
    payment_key varchar(255),
    status enum ('CANCELED','FAILED','PAID','PENDING','REFUNDED') not null,
    primary key (id)
) engine=InnoDB;

create table receipt (
    created_at datetime(6),
    id bigint not null auto_increment,
    received_at datetime(6),
    rental_id bigint not null,
    condition_detail TEXT,
    product_condition enum ('DAMAGED','DIRTY','MISSING_PART','NORMAL','OTHER') not null,
    primary key (id)
) engine=InnoDB;

create table receipt_image (
    sort_order integer,
    created_at datetime(6),
    id bigint not null auto_increment,
    receipt_id bigint not null,
    image_url varchar(500) not null,
    primary key (id)
) engine=InnoDB;

create table refresh_token (
    created_at datetime(6),
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    last_used_at datetime(6),
    replaced_by_token_id bigint,
    revoked_at datetime(6),
    user_id bigint not null,
    version bigint not null,
    family_id varchar(36) not null,
    token_hash varchar(64) not null,
    device_info varchar(255),
    primary key (id)
) engine=InnoDB;

create table rental (
    daily_price_snapshot decimal(38,2) not null,
    end_date date not null,
    rental_days integer not null,
    start_date date not null,
    total_price decimal(38,2) not null,
    approved_at datetime(6),
    created_at datetime(6),
    equipment_id bigint not null,
    id bigint not null auto_increment,
    renter_id bigint not null,
    updated_at datetime(6),
    version bigint not null,
    zipcode varchar(10),
    receiver_name varchar(20),
    receiver_phone varchar(20),
    category_snapshot varchar(50),
    product_name_snapshot varchar(100) not null,
    address varchar(200),
    detail_address varchar(200),
    reject_reason varchar(200),
    request_message TEXT,
    status enum ('APPROVED','CANCELED','COMPLETED','DISPUTED','PENDING','RECEIVED','REJECTED','RENTING','REQUESTED','RETURNED','RETURNING','RETURN_REQUESTED','SHIPPING') not null,
    primary key (id)
) engine=InnoDB;

create table rental_review (
    rating integer not null,
    created_at datetime(6),
    id bigint not null auto_increment,
    rental_id bigint not null,
    reviewee_id bigint not null,
    reviewer_id bigint not null,
    updated_at datetime(6),
    content TEXT not null,
    primary key (id)
) engine=InnoDB;

create table report (
    created_at datetime(6),
    id bigint not null auto_increment,
    reporter_id bigint not null,
    resolved_at datetime(6),
    target_id bigint not null,
    updated_at datetime(6),
    reason varchar(50) not null,
    admin_memo TEXT,
    description TEXT,
    status enum ('RECEIVED','REJECTED','RESOLVED','UNDER_REVIEW') not null,
    target_type enum ('EQUIPMENT','RENTAL','USER') not null,
    primary key (id)
) engine=InnoDB;

create table return_receipt (
    return_date date,
    created_at datetime(6),
    id bigint not null auto_increment,
    rental_id bigint not null,
    condition_detail TEXT,
    product_condition enum ('DAMAGED','DIRTY','MISSING_PART','NORMAL','OTHER') not null,
    primary key (id)
) engine=InnoDB;

create table return_receipt_image (
    sort_order integer,
    created_at datetime(6),
    id bigint not null auto_increment,
    return_receipt_id bigint not null,
    image_url varchar(500) not null,
    primary key (id)
) engine=InnoDB;

create table review (
    rating integer not null,
    created_at datetime(6),
    equipment_id bigint not null,
    id bigint not null auto_increment,
    rental_id bigint not null,
    updated_at datetime(6),
    user_id bigint not null,
    content TEXT,
    primary key (id)
) engine=InnoDB;

create table shipping (
    created_at datetime(6),
    delivered_at datetime(6),
    id bigint not null auto_increment,
    rental_id bigint not null,
    shipped_at datetime(6),
    updated_at datetime(6),
    carrier varchar(50),
    tracking_number varchar(50),
    status enum ('DELIVERED','IN_TRANSIT','READY','SHIPPED') not null,
    type enum ('OUTBOUND','RETURN') not null,
    primary key (id)
) engine=InnoDB;

create table user_address (
    is_default bit not null,
    created_at datetime(6),
    id bigint not null auto_increment,
    user_id bigint not null,
    zipcode varchar(10) not null,
    recipient_name varchar(20) not null,
    recipient_phone varchar(20) not null,
    address varchar(200) not null,
    detail_address varchar(200) not null,
    primary key (id)
) engine=InnoDB;

create table users (
    point_balance decimal(12,0) not null,
    created_at datetime(6),
    deleted_at datetime(6),
    id bigint not null auto_increment,
    updated_at datetime(6),
    name varchar(20) not null,
    nick_name varchar(20),
    phone varchar(20),
    email varchar(100) not null,
    password varchar(255),
    preferred_language enum ('EN','KO') not null,
    role enum ('ADMIN','USER') not null,
    status enum ('ACTIVE','DELETED','SUSPENDED') not null,
    primary key (id)
) engine=InnoDB;

create index idx_admin_action_created_id 
   on admin_action (created_at desc, id desc);

create index idx_admin_action_target_created_id 
   on admin_action (target_type, target_id, action, created_at desc, id desc);

create index idx_equipment_created_id 
   on equipment (created_at desc, id desc);

create index idx_equipment_status_created_id 
   on equipment (status, created_at desc, id desc);

create index idx_equipment_owner_created_id 
   on equipment (owner_id, created_at desc, id desc);

alter table equipment_image_upload 
   add constraint UK765r3w9quj5q1lj44fjdrw7vc unique (object_key);

create index idx_notification_receiver_created_id 
   on notification (receiver_id, created_at desc, id desc);

create index idx_notification_receiver_read_created_id 
   on notification (receiver_id, is_read, created_at desc, id desc);

alter table oauth_account 
   add constraint uk_oauth_account_provider_user unique (provider, provider_user_id);

alter table oauth_account 
   add constraint uk_oauth_account_user_provider unique (user_id, provider);

create index idx_oauth_pending_token_expires_at 
   on oauth_pending_token (expires_at);

create index idx_oauth_pending_token_provider_user 
   on oauth_pending_token (provider, provider_user_id);

alter table oauth_pending_token 
   add constraint UKp2cb6lui0bsvs2i7ilr2jqtgg unique (token_hash);

create index idx_payment_created_id 
   on payment (created_at desc, id desc);

create index idx_payment_status_created_id 
   on payment (status, created_at desc, id desc);

alter table payment 
   add constraint UK3fb4wny8ijn07cvlaxiag8a0a unique (rental_id);

alter table payment 
   add constraint UKmf7n8wo2rwrxsd6f3t9ub2mep unique (order_id);

alter table receipt 
   add constraint UKce4w1agvtahcefdj8y41ah7d3 unique (rental_id);

create index idx_refresh_token_user_id 
   on refresh_token (user_id);

create index idx_refresh_token_family_id 
   on refresh_token (family_id);

create index idx_refresh_token_expires_at 
   on refresh_token (expires_at);

alter table refresh_token 
   add constraint UKkdj16cltjxdksuyiosdhliveg unique (token_hash);

create index idx_rental_renter_created_id 
   on rental (renter_id, created_at desc, id desc);

create index idx_rental_equipment_created_id 
   on rental (equipment_id, created_at desc, id desc);

create index idx_rental_equipment_period 
   on rental (equipment_id, start_date, end_date, id, status);

create index idx_rental_status_created 
   on rental (status, created_at);

create index idx_rental_review_reviewee_created_id 
   on rental_review (reviewee_id, created_at desc, id desc);

create index idx_rental_review_reviewer_created_id 
   on rental_review (reviewer_id, created_at desc, id desc);

alter table rental_review 
   add constraint uk_rental_review_rental_reviewer unique (rental_id, reviewer_id);

create index idx_report_created_id 
   on report (created_at desc, id desc);

create index idx_report_status_target_created_id 
   on report (status, target_type, created_at desc, id desc);

create index idx_report_reporter_created_id 
   on report (reporter_id, created_at desc, id desc);

create index idx_report_active_target 
   on report (reporter_id, target_type, target_id, status);

alter table return_receipt 
   add constraint UKs7shsu7m9c8fdkrfhy4lyy4cc unique (rental_id);

alter table review 
   add constraint UKt70f82ari6fibkqlfe2bjaehv unique (rental_id);

create index idx_user_address_user_default 
   on user_address (user_id, is_default);

create index idx_users_created_id 
   on users (created_at desc, id desc);

create index idx_users_status_created_id 
   on users (status, created_at desc, id desc);

alter table users 
   add constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email);

alter table dispute_image 
   add constraint FKk83y9pbqe66pp74ylkj8b8fp6 
   foreign key (dispute_id) 
   references dispute (id);

alter table dispute_response 
   add constraint FK2157v1vsf0nmcasmgt873bbhk 
   foreign key (dispute_id) 
   references dispute (id);

alter table equipment_image 
   add constraint FKroqb9ahurf2fdmdnk4qqcyi1w 
   foreign key (equipment_id) 
   references equipment (id);

alter table receipt 
   add constraint FK88earbjfnlk4g3ca23xv909h 
   foreign key (rental_id) 
   references rental (id);

alter table receipt_image 
   add constraint FKcb7pbku2wstaajidj8b4runy 
   foreign key (receipt_id) 
   references receipt (id);

alter table return_receipt 
   add constraint FKl1w72ralajatf9s5rdb7ha6ik 
   foreign key (rental_id) 
   references rental (id);

alter table return_receipt_image 
   add constraint FK1bsewdrqlf65jhtj9tx5cjud4 
   foreign key (return_receipt_id) 
   references return_receipt (id);

alter table review 
   add constraint FKmcv3g755cmiwkg1pmjqixl1g0 
   foreign key (equipment_id) 
   references equipment (id);
