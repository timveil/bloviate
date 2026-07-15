create table region (r_id int primary key, r_name varchar(25) not null);
create table nation (n_id int primary key, n_region int not null references region(r_id), n_name varchar(25) not null);
create table customer (c_id int primary key, c_nation int not null references nation(n_id), c_name varchar(40) not null, c_balance numeric(12,2) not null, c_since timestamp not null, c_uuid uuid not null, c_active boolean not null, c_data varbinary(25) not null, c_bigcount bigint not null, c_rate double precision not null);
create table orders (o_id int primary key, o_cust int not null references customer(c_id), o_total numeric(12,2) not null, o_comment varchar(80) not null);
create table bridge (b_id int primary key, b_ref int not null, b_note varchar(20) not null, constraint fk_b_region foreign key (b_ref) references region(r_id), constraint fk_b_nation foreign key (b_ref) references nation(n_id))
