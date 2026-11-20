CREATE TABLE travel_pricing_config (
    id INTEGER PRIMARY KEY,
    base_fare_per_leg NUMERIC(10,2),
    rate_per_km NUMERIC(10,2),
    CONSTRAINT ck_travel_pricing_config_singleton CHECK (id = 1),
    CONSTRAINT ck_travel_pricing_base_fare_nonnegative
        CHECK (base_fare_per_leg IS NULL OR base_fare_per_leg >= 0),
    CONSTRAINT ck_travel_pricing_rate_nonnegative
        CHECK (rate_per_km IS NULL OR rate_per_km >= 0)
);

CREATE TABLE booking_travel_legs (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    leg_order INTEGER NOT NULL,
    leg_type VARCHAR(40) NOT NULL,
    origin_label VARCHAR(80) NOT NULL,
    destination_label VARCHAR(80) NOT NULL,
    distance_meters BIGINT NOT NULL,
    distance_km NUMERIC(10,3) NOT NULL,
    base_fare NUMERIC(10,2) NOT NULL,
    distance_charge NUMERIC(10,2) NOT NULL,
    total_fare NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_booking_travel_legs_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT ck_booking_travel_legs_order_positive CHECK (leg_order > 0),
    CONSTRAINT ck_booking_travel_legs_distance_nonnegative CHECK (distance_meters >= 0)
);

CREATE INDEX idx_booking_travel_legs_booking_id
    ON booking_travel_legs (booking_id, leg_order);
