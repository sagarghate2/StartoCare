ALTER TABLE users
    ADD COLUMN service_location_address TEXT,
    ADD COLUMN service_latitude DOUBLE PRECISION,
    ADD COLUMN service_longitude DOUBLE PRECISION;

ALTER TABLE users
    ADD CONSTRAINT ck_users_service_latitude
        CHECK (service_latitude IS NULL OR service_latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_users_service_longitude
        CHECK (service_longitude IS NULL OR service_longitude BETWEEN -180 AND 180);

CREATE INDEX idx_users_companion_location
    ON users (role, companion_verification_status, service_latitude, service_longitude);
