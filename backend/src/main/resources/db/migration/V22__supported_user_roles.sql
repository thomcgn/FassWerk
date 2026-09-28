-- Keep existing users unchanged; provisioning BARCHEF is an explicit administrative action.
alter table app_users add constraint ck_app_users_role
    check (role in ('ADMIN', 'BARCHEF', 'STAFF'));
