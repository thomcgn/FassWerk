-- Revoke only accounts still carrying the publicly documented legacy seed hashes.
-- Accounts whose passwords have already been changed are preserved.
delete from refresh_tokens
where user_id in (
    select id from app_users
    where password_hash in (
        '$2b$10$ndaU3xOlzKDbH9LeOXxdd.I5SDDbW435qMesiPtXzABYLqMtUAfBO',
        '$2b$10$MutZQdRnO6ergJHIlgulveM1a3zF5z3BjtpXUx9YGvILl5RZZ9lwm'
    )
);

update app_users
set active = false,
    password_hash = 'DISABLED_LEGACY_SEED',
    updated_at = current_timestamp
where password_hash in (
    '$2b$10$ndaU3xOlzKDbH9LeOXxdd.I5SDDbW435qMesiPtXzABYLqMtUAfBO',
    '$2b$10$MutZQdRnO6ergJHIlgulveM1a3zF5z3BjtpXUx9YGvILl5RZZ9lwm'
);
