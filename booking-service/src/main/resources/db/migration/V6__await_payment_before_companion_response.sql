CREATE INDEX IF NOT EXISTS idx_bookings_payment_status
    ON bookings (booking_status, created_at);
