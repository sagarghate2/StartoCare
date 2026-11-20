# StartoCare booking and companion location APIs

The booking backend supports verified companion search, travel estimates, and booking creation. It currently has no frontend; companion bases are stored as coordinates, while patient and hospital addresses are geocoded by Geoapify and routed as motorcycle trips. Keep the Geoapify API key on the server.

## Configuration

From the repository root, run the local launcher:

```powershell
.\scripts\start-local.ps1
```

The script starts local Kafka with Docker Compose, then prompts for PostgreSQL, SMTP, Geoapify, Razorpay test credentials, and each service's JDBC URL. It generates one random `JWT_SECRET` and `COMPANION_API_KEY` and passes the same values to all three service processes. Secrets are only passed through process environments; rerun the script after closing the service windows. It opens separate terminals for user-service (8082), booking-service (8083), and payment-service (8084). PostgreSQL databases must already exist, including `startocare_payments`. Ensure the Geoapify project/key has access to Geocoding and Routing APIs. Use Razorpay test-mode credentials. Never paste either provider's secret into Postman or commit it.

Run the launcher from the repository root rather than starting services in separate terminals without configuring their environment. All services require the same `JWT_SECRET`; booking and user services also share `COMPANION_API_KEY`. Kafka must be available on `localhost:9092`. Booking and user services use Kafka request/reply on `companion.location.requested` and `companion.location.response` for verified companion location lookups; the request is signed using the shared key. Missing values prevent startup, and mismatched secrets break authentication or service communication.

The script prompts for SMTP credentials because registration OTP delivery needs a working mail account. For Gmail, use an App Password. Geoapify, SMTP, and Razorpay credentials are kept out of source control.

The backend calculates its own motorcycle travel estimate; it is **not a Rapido fare**. An administrator must configure `baseFarePerLeg` and `ratePerKm` before estimates can be returned. Each included travel leg is priced as `base fare + route kilometers × rate per km`; each leg's amount is rounded to paise. No rates are seeded, so estimates return `503 ESTIMATE_UNAVAILABLE` until configured. Geoapify geocodes patient/hospital addresses and calculates motorcycle routes. If routing fails, the response includes Geoapify's HTTP status and error message when available; check that the key has access to Geocoding and Routing APIs and that the supplied addresses resolve to routable locations. Keep the key server-side and never paste it into Postman.

## Companion application and discovery

1. Register a companion using **POST** `http://localhost:8082/api/auth/register`. Request an OTP first with **POST** `/api/auth/send-otp`. Include `"role": "COMPANION"` in the registration body. Registration returns a `COMPANION` token with verification status `PENDING`.

2. With that companion token, submit the service-base address and its map coordinates:

   `PUT http://localhost:8082/api/users/me/companion-profile`

   ```json
   {
     "serviceLocationAddress": "Secunderabad Railway Station, Hyderabad",
     "latitude": 17.4344,
     "longitude": 78.5013
   }
   ```

   The response shows the application as `PENDING`. Companions cannot create family bookings.

3. An administrator reviews applications:

   `GET http://localhost:8082/api/admin/companions/pending`

   Verify or reject an application:

   `PATCH http://localhost:8082/api/admin/companions/{companionId}/verification`

   ```json
   { "status": "VERIFIED" }
   ```

   Only a verified, enabled companion is searchable. For local ADMIN setup, see the Admin setup section below.

4. A verified companion updates their base location after moving:

   `PUT http://localhost:8082/api/users/me/companion-location`

   It uses the same JSON shape as the application request. Changing a verified companion's location resets verification to `PENDING` until an administrator reviews it again.

5. A FAMILY user searches around the chosen map point:

   `GET http://localhost:8082/api/companions/search?latitude=17.4065&longitude=78.5572`

   Search is limited to 8 km by straight-line distance. The response includes companion ID, name, service label, and approximate distance—not the companion's exact address or coordinates.

All user-service calls above except public auth routes require a Bearer token. The search endpoint requires a FAMILY token; application/location calls use the corresponding account's token; admin review requires an ADMIN token.

## Admin setup

Public registration does not allow assigning ADMIN. For local testing, first register a separate account as FAMILY and then promote that account in the user-service database:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
```

Log in again after promotion to get an ADMIN token. Do not expose a public role-promotion endpoint or use manual database promotion as a production workflow.

## Fare configuration and booking flow

1. An ADMIN configures app-owned bike estimate rates:

   `PUT http://localhost:8083/api/admin/travel-pricing`

   ```json
   {
     "baseFarePerLeg": 20.00,
     "ratePerKm": 8.00
   }
   ```

   These example values are illustrative only; choose the real rates for your service. Inspect the current configuration with `GET /api/admin/travel-pricing`.

2. The FAMILY user requests an estimate after selecting a companion:

   `POST http://localhost:8083/api/bookings/estimate`

   ```json
   {
     "companionId": "VERIFIED_COMPANION_UUID",
     "durationMinutes": 60,
     "travelPlan": "PLAN_1",
     "hospitalName": "Apollo Hospital",
     "hospitalAddress": "MG Road, Bengaluru",
     "patientName": "Example Patient",
     "appointmentDate": "2026-12-01",
     "appointmentTime": "10:30:00",
     "patientHomeAddress": "Nagole, Hyderabad"
   }
   ```

   Send the FAMILY user's token as a Bearer token. The response includes the service fee, each route leg's distance and fare components, the total, and `travelQuoteStatus: "QUOTED"`. Estimates require a verified COMPANION account, configured rates, and a successful Geoapify geocoding and routing responses.

3. Create the booking with the same JSON fields plus optional patient contact/age:

   `POST http://localhost:8083/api/bookings`

   The booking requires the FAMILY user's Bearer token and saves the selected companion, computed amounts, and travel-leg breakdown. Its initial booking status is `AWAITING_PAYMENT`.

### Payment before companion response

1. Booking service publishes a `payment.requested` event through its transactional outbox. Payment service consumes it and creates a Razorpay order. The order may take a few seconds to appear.
2. The FAMILY user polls for checkout information:

   `GET http://localhost:8084/api/payments/bookings/{bookingId}`

   Send the FAMILY user's token. Retry if the order is not ready yet. Use the returned `keyId`, `razorpayOrderId`, `amountPaise`, and `currency` to launch Razorpay Checkout in the client application. This repository does not include checkout UI.
3. After Razorpay Checkout returns its values, verify them:

   `POST http://localhost:8084/api/payments/verify`

   ```json
   {
     "razorpay_order_id": "order_from_checkout",
     "razorpay_payment_id": "payment_from_checkout",
     "razorpay_signature": "signature_from_checkout"
   }
   ```

   Use the FAMILY user's token. Payment service verifies the signature and checks with Razorpay that the amount and currency match and the payment is captured. Kafka then changes the booking from `AWAITING_PAYMENT` to `PENDING`.
4. The optional Razorpay webhook endpoint is `POST http://localhost:8084/api/payments/razorpay/webhook`; configure it in Razorpay using the webhook secret supplied to the launcher.

After payment, the selected companion reviews their requests:

`GET http://localhost:8083/api/bookings/companion-requests?page=0&size=20`

The companion accepts or declines with their own Bearer token:

`PATCH http://localhost:8083/api/bookings/{bookingId}/companion-response`

Accept body: `{ "decision": "ACCEPTED" }`. The booking becomes `CONFIRMED` and its calculated travel quote becomes confirmed. Decline body: `{ "decision": "DECLINED" }`. A declined booking is closed; the family can create a new booking with another companion. Only the selected companion may respond while the booking is pending.

After acceptance, an admin may advance the booking through `COMPANION_ASSIGNED`, `IN_PROGRESS`, and `COMPLETED` with `PATCH /api/bookings/{bookingId}/status`. Admins cannot confirm a pending booking; that decision belongs to the selected companion.

### Included travel legs

- **PLAN_1:** companion base to patient home, patient home to companion base. The patient-home-to-hospital and return journey is not included in the companion travel fare; show it separately as patient transport.
- **PLAN_2:** companion base to hospital, hospital to companion base.
- **PLAN_3:** companion base to patient home, hospital to companion base. Patient return transport is arranged separately.

Get the available service durations from `GET http://localhost:8083/api/bookings/pricing/durations`. Families can list/view/cancel their bookings; companions can list pending requests, view requests assigned to them, and accept or decline; admins can view all bookings and manage operational status/quotations.
