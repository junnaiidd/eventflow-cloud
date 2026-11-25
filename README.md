# EventFlow - Cloud-Enabled Smart Event Management and Attendance System

Spring Boot 3.5 / Java 21 / Maven. Single-page UI served from `src/main/resources/static`.

> **Verification status:** the code in this version was written in an environment that could not download
> Maven dependencies, so it has **not been compiled or run yet**. Run `mvn clean test` first and fix anything
> it reports (see "Known limitations").

## Features

- Event management (create / edit / delete, calendar, budget analytics, dark mode) - original features
- Roles: **Admin**, **Organizer**, **Student** (internal role `PARTICIPANT`) (sign-in required)
- Registration with capacity, **automatic waitlist** and promotion when someone cancels
- **QR e-tickets** (ZXing) with PDF download; organizer **check-in** by camera or manual ticket ID
- **Automatic certificates** (PDF, OpenPDF) for students who were checked in
- **Analytics**: registrations, attendance %, seats, popular events, trends (Chart.js)
- Event posters, local disk in development and **Amazon S3** in production
- `prod` profile for **AWS EC2 + RDS MySQL + S3** (see `docs/DEPLOYMENT_AWS.md`)

## College edition

EventFlow is configured as a single-college event management system (`app.college.*` in `application.properties`,
overridable with `COLLEGE_NAME`).

- **Roles:** Admin, Organizer and **Student**. Students are stored with the internal role `PARTICIPANT`; only the label changes in the UI, so no data migration is needed.
- **Student profile:** USN (5-20 letters/digits, unique), department and year of study, required to register for events. Edit under *My Profile*. No college-email rule.
- **Events:** category (the existing `type` column), department, venue, start/end time, registration deadline, capacity. Lists of categories and departments come from configuration (`GET /api/meta`).
- **Approval:** events created by an organizer start as *Pending Approval* and are invisible to students until an admin approves them (*Approvals* page, `POST /api/admin/events/{id}/approve|reject`). Admin-created events are published directly. Editing a rejected event resubmits it.
- **Dashboards:** student (available events, my registrations, history, certificates), organizer (own events, approval status, attendance roster with USN/department) and admin (`GET /api/admin/stats`, approvals, user management with activate/deactivate).
- New database columns are nullable and added by `spring.jpa.hibernate.ddl-auto=update`; existing users, events and registrations stay valid (a student created before this change must complete their profile before the next registration).

## Run locally (no AWS needed)

Requirements: JDK 21, Maven 3.9+.

```bash
mvn clean package        # compiles and runs all tests
mvn spring-boot:run
# open http://localhost:8080
```

Local mode uses an in-memory H2 database (reset on every start), sample events, and files in `./uploads`.

Demo accounts (local only, never created in `prod`):

| Role | Email | Password |
|---|---|---|
| Admin | admin@eventflow.local | Admin@123 |
| Organizer | organizer@eventflow.local | Organizer@123 |
| Student | aarav@eventflow.local (also diya, rohan, isha, kabir, meera) | Participant@123 |

H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:eventflowdb`, user `sa`, password `password`).

## Try the main workflows

1. **Waitlist:** sign in as `rohan@...`, open "Spring Boot Hands-on Lab" (3 seats, already full with 2 waiting).
   Join the waitlist. Sign in as a student who holds a seat (`aarav@...`), *My Registrations -> Cancel*;
   the first waitlisted person is promoted and gets a ticket.
2. **Ticket + check-in:** as a student open *My Registrations* (QR, PDF). As `organizer@...` open *Attendance*,
   choose the event, scan the QR with the camera (HTTPS or localhost) or type the ticket ID.
   Scanning again shows "already used"; a cancelled ticket or another event's ticket is rejected.
3. **Certificate:** after check-in the student finds the certificate under *Certificates*.
   The organizer sees the status of every registrant there (Issued / Pending / Did not attend).
4. **Analytics:** as organizer/admin open *Dashboard* and *Analytics*.
5. **Poster:** create or edit an event and choose an image (PNG/JPEG/WebP, max 2 MB).

## Tests

```bash
mvn test
```

Covers ticket generation/uniqueness, check-in (valid, invalid, duplicate, cancelled, wrong event), waitlist
(position, promotion, duplicates, capacity under concurrent requests), certificates (eligibility, uniqueness,
authorization), analytics counts, role/ownership/CSRF rules over HTTP, poster validation, local storage,
S3 storage (mocked AWS client) and the production configuration (secrets only from environment variables).

## REST API (all under `/api`, session cookie + `X-XSRF-TOKEN` header on writes)

| Endpoint | Who |
|---|---|
| `POST /auth/login`, `/auth/register`, `/auth/logout`, `GET /auth/me` | public / signed-in |
| `GET /events`, `GET /events/{id}`, `GET /events/search` | signed-in |
| `POST /events`, `PUT/DELETE /events/{id}` | organizer (own events) / admin |
| `POST /events/{id}/register`, `GET /registrations/mine`, `POST /registrations/{id}/cancel` | signed-in (own registrations) |
| `GET /registrations/{id}/qr.png`, `/ticket.pdf` | ticket owner, event organizer, admin |
| `POST /checkin`, `GET /checkin/events/{id}/stats`, `GET /events/{id}/registrations` | organizer (own events) / admin |
| `GET /certificates/mine`, `GET /certificates/{id}/download` | owner, event organizer, admin |
| `GET /events/{id}/certificates`, `POST /events/{id}/certificates/generate` | organizer (own events) / admin |
| `GET /analytics/overview`, `GET /events/analytics` | organizer / admin |
| `POST/DELETE /events/{id}/poster`, `GET /events/{id}/poster` | organizer (own) / signed-in |
| `GET/POST /admin/users` | admin |

## Project layout (new parts)

`security/` access rules - `storage/` local + S3 file storage - `service/` registration, check-in,
certificates, analytics, posters - `service/pdf/` ticket and certificate PDFs - `config/` security, S3,
data seeding - `static/js/eventflow-extensions.js` and `static/css/eventflow-extensions.css` for the new UI.

## Known limitations

- Not yet compiled or executed (see top). AWS (EC2/RDS/S3) was never tested against a real account.
- PDFs use the built-in Latin fonts: names in non-Latin scripts (e.g. Devanagari) will not render.
- No e-mail notifications; a waitlisted person sees their promotion next time they open the app.
- No login rate limiting or password reset.
- The camera scanner needs HTTPS or localhost; otherwise use the manual ticket field.
