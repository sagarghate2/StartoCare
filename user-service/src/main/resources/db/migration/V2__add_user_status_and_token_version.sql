ALTER TABLE users
    ADD COLUMN companion_verification_status VARCHAR(20) NOT NULL DEFAULT 'NOT_APPLICABLE',
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT ck_users_companion_verification_status
        CHECK (companion_verification_status IN ('NOT_APPLICABLE', 'PENDING', 'VERIFIED', 'REJECTED')),
    ADD CONSTRAINT ck_users_token_version
        CHECK (token_version >= 0);
