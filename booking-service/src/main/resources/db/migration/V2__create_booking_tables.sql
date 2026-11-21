CREATE TABLE IF NOT EXISTS bookings (
    id UUID PRIMARY KEY,
    booking_reference VARCHAR(32) NOT NULL UNIQUE,
    family_user_id UUID NOT NULL,
    companion_id UUID,
    duration_minutes INTEGER NOT NULL,
    travel_plan VARCHAR(20) NOT NULL,
    patient_home_address TEXT,
    hospital_name VARCHAR(200) NOT NULL,
    hospital_address TEXT NOT NULL,
    appointment_date DATE NOT NULL,
    appointment_time TIME NOT NULL,
    patient_name VARCHAR(120) NOT NULL,
    patient_age INTEGER,
    patient_contact_number VARCHAR(20),
    service_amount NUMERIC(10,2) NOT NULL,
    travel_amount NUMERIC(10,2),
    total_amount NUMERIC(10,2),
    travel_quote_status VARCHAR(20) NOT NULL,
    booking_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bookings_family_user_id ON bookings (family_user_id);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON bookings (booking_status);
CREATE INDEX IF NOT EXISTS idx_bookings_appointment_date ON bookings (appointment_date);

CREATE TABLE IF NOT EXISTS booking_price_breakdowns (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    service_amount NUMERIC(10,2) NOT NULL,
    travel_amount NUMERIC(10,2) NOT NULL,
    total_amount NUMERIC(10,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    quoted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_booking_breakdown_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_booking_breakdowns_booking_id ON booking_price_breakdowns (booking_id);
