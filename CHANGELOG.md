# EventFlow - College Event Management System: changelog

Copy these files over the project root, keeping the paths. Only changed (M) and new (N) files are included.
Baseline: the Turn 1 enhanced project (QR tickets, waitlist, certificates, analytics, S3/prod profile).
Internal role `PARTICIPANT` is kept; the UI shows it as "Student". No AWS/prod files are touched.

## Modified files
| File | Change |
|---|---|
| README.md | "College edition" section, Student wording, My Registrations / Attendance names |
| src/main/resources/application.properties | `app.college.name/departments/categories/max-year` (name via `COLLEGE_NAME`) |
| config/AccountSeeder.java | Six demo students with USN, department, year |
| config/DataInitializer.java | Eight college sample events (relative dates, departments, one Pending Approval) |
| config/DemoRegistrationSeeder.java | Seeded registrations use the new event names |
| config/SecurityConfig.java | `/api/meta` is public |
| controller/AdminUserController.java | `POST /api/admin/users/{id}/active`; create user accepts student profile |
| controller/AuthController.java | Register takes USN/department/year; `PUT /api/auth/profile`; deactivated-account message |
| controller/EventController.java | Hides unapproved events from students, passes the actor, rethrows ApiException |
| dto/AuthRequests.java | Register/Profile/SetActive/CreateUser with student fields |
| dto/EventDTO.java | department, endTime, registrationDeadline |
| dto/RegistrationDTO.java | usn, department, yearOfStudy |
| dto/UserDTO.java | usn, department, yearOfStudy, active |
| model/AppUser.java | usn (unique), department, yearOfStudy, nullable `active` (null = active) |
| model/Event.java | department, endTime, registrationDeadline (nullable columns) |
| repository/AppUserRepository.java | existsByUsn, existsByUsnAndIdNot, countByRole |
| repository/EventRepository.java | countByStatusAndEventDateGreaterThanEqual |
| repository/RegistrationRepository.java | countByStatus |
| security/AppUserDetails.java | `isEnabled()` follows `active` |
| service/EventService.java | Approval workflow (Pending Approval / approve / reject), visibility, college field validation, category = `type` |
| service/RegistrationService.java | Deadline and complete-student-profile checks |
| service/UserService.java | Profile validation (USN `^[A-Z0-9]{5,20}$`, unique), updateProfile, setActive |
| static/index.html | Category/department/end-time/deadline fields, filters, Student labels, signup profile fields, Approvals and Profile pages, role nav |
| static/js/eventflow-extensions.js | /api/meta loading, filters, student/organizer/admin dashboards, approvals, profile, user management, roster with USN |
| static/css/eventflow-extensions.css | Filters, banners, nav count badge |
| test/.../TestSupport.java | Students get a unique USN, department and year |
| test/.../SecurityApiTest.java | Sign-up JSON includes the new required fields |

(Java paths are under `src/main/java/com/example/eventflow/` and tests under `src/test/java/com/example/eventflow/`.)

## New files
| File | Purpose |
|---|---|
| controller/AdminController.java | `GET /api/admin/stats`, `POST /api/admin/events/{id}/approve` and `/reject` |
| controller/MetaController.java | Public `GET /api/meta` (college name, departments, categories, years) |
| service/CollegeCatalog.java | Configured lists and validation helpers |
| test/.../CollegeTest.java | Seven tests: profile validation and USN uniqueness, approval workflow and visibility, rejection and resubmission, field validation, deadline and profile checks, admin-only endpoints, deactivated login |

## Unverified (important)
Maven Central was blocked, so **none of the Java was compiled and no test was run**. Only a syntax-level parse
of the Java files and a browser run of the frontend against a mock API (student, admin, organizer) were done.
Unverified: compilation; all existing and new tests; backend rules (deadline, USN uniqueness, approval visibility,
deactivated login, role restrictions); that the Turn 1 features (QR, check-in, waitlist, certificates) still work;
`ddl-auto=update` adding the new nullable columns to an existing database; the real server end to end.
The deactivated-login test assumes HTTP 401 and may need adjusting.

Verify locally (Windows PowerShell, JDK 21 + Maven): `mvn clean test` then `mvn spring-boot:run`.
