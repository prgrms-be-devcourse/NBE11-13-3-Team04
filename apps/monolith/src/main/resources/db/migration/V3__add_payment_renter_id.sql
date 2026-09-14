alter table payment add column renter_id_snapshot bigint null;

update payment p
    set p.renter_id_snapshot = (
        select r.renter_id from rental r where r.id = p.rental_id
    );

alter table payment modify column renter_id_snapshot bigint not null;

create index idx_payment_renter_created_id
    on payment (renter_id_snapshot, created_at desc, id desc);
