alter table rental add column owner_id_snapshot bigint null;

update rental r
    set r.owner_id_snapshot = (
        select e.owner_id from equipment e where e.id = r.equipment_id
    );

alter table rental modify column owner_id_snapshot bigint not null;

drop index idx_rental_equipment_created_id on rental;

create index idx_rental_owner_created_id
    on rental (owner_id_snapshot, created_at desc, id desc);

create index idx_rental_owner_end_date_status
    on rental (owner_id_snapshot, end_date, status);
