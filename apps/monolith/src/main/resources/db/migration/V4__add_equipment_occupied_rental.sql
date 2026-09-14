create table equipment_occupied_rental (
    id bigint not null auto_increment,
    equipment_id bigint not null,
    rental_id bigint not null,
    start_date date not null,
    end_date date not null,
    created_at datetime(6),
    updated_at datetime(6),
    primary key (id)
) engine=InnoDB;

alter table equipment_occupied_rental
    add constraint uk_equipment_occupied_rental_rental_id unique (rental_id);

create index idx_equipment_occupied_rental_equipment_period
    on equipment_occupied_rental (equipment_id, start_date, end_date);

insert into equipment_occupied_rental (equipment_id, rental_id, start_date, end_date)
select equipment_id, id, start_date, end_date
from rental
where status not in ('REJECTED', 'CANCELED', 'COMPLETED');
