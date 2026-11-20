CREATE INDEX IF NOT EXISTS idx_bookings_companion_status_created_at
    ON bookings (companion_id, booking_status, created_at DESC);
