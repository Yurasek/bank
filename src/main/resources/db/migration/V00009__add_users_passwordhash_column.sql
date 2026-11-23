alter table users add column password_hash VARCHAR(255);


update users set password_hash='' where users.id='aaef7d39-db27-402d-97a0-7d6a683fe63a'