alter table transfer_history
alter column from_account type varchar(40) using from_account::varchar(40);

alter table transfer_history
alter column to_account type varchar(40) using to_account::varchar(40);