# BOOKLY — PROJECT CONTEXT

## 1. Project Overview

Bookly is a multi-tenant SaaS appointment booking and business management platform.

The target users are appointment-based businesses such as:

- Barbers
- Hair salons
- Beauty professionals
- Personal trainers
- Tutors
- Therapists
- Clinics
- Tattoo artists
- Other service-based businesses

The project should be designed as a serious, production-oriented application rather than a simple CRUD demo.

The system should eventually support:

- Business management
- Employees
- Services
- Customers
- Appointments
- Availability calculation
- Notifications
- WhatsApp
- Social integrations
- Payments
- Subscriptions
- Analytics
- Marketing attribution
- Web dashboard
- Android application
- iOS application

---

# 2. Main Product Concept

## Business Owner

A business owner should be able to:

- Register/login
- Create a business
- Configure business profile
- Define services
- Define employees
- Define employee working hours
- Define blocked times and vacations
- Manage appointments
- Manage customers
- View analytics
- Configure integrations
- Configure payments
- Manage subscription

## Customer

A customer should be able to:

1. Open a public business booking page
2. Select a service
3. Select an employee or "any available employee"
4. Select a date
5. See available time slots
6. Select a slot
7. Enter customer details
8. Confirm the appointment
9. Receive confirmation/reminders

Example:

`bookly.app/book/{businessSlug}`

---

# 3. Technology Stack

## Backend

- Java 21+
- Spring Boot 3+
- Spring Security
- Spring Data JPA
- Hibernate
- PostgreSQL
- Flyway
- Maven
- Redis
- JUnit 5
- Mockito
- Testcontainers
- OpenAPI / Swagger

## Frontend

- Next.js
- React
- TypeScript
- Tailwind CSS
- TanStack Query
- React Hook Form
- Zod

## Infrastructure

- Docker
- Docker Compose
- GitHub Actions
- Cloud deployment
- HTTPS
- Environment variables / Secrets

## Architecture

Use a Modular Monolith.

Do NOT introduce microservices unless there is a concrete technical reason.

Do NOT add technologies just for the sake of making the project look more advanced.

Avoid premature use of:

- Kubernetes
- Kafka
- Elasticsearch
- GraphQL
- Microservices
- Other unnecessary infrastructure

---

# 4. Future Mobile Applications

The backend must be client-agnostic.

The same API should eventually serve:

```text
                 Bookly Backend API
                         |
          +--------------+--------------+
          |              |              |
          v              v              v
       Next.js        Android          iOS
                      Kotlin          Swift

Mobile applications are not part of the first MVP.
Future plans:
	•	Android → Kotlin + Jetpack Compose
	•	iOS → Swift + SwiftUI
The backend API must therefore be designed from the beginning so that Web, Android and iOS can all consume it.

5. Backend Architecture
Use a modular monolith.
Suggested structure:

backend/
└── src/main/java/com/bookly/
    ├── auth/
    ├── user/
    ├── business/
    ├── employee/
    ├── service/
    ├── customer/
    ├── appointment/
    ├── notification/
    ├── payment/
    ├── subscription/
    ├── analytics/
    ├── integration/
    ├── audit/
    └── common/

Principles:
	•	Thin controllers
	•	Business logic in services/domain components
	•	DTOs instead of exposing JPA entities
	•	Clear module boundaries
	•	Transactions where appropriate
	•	Flyway for database migrations
	•	Avoid unnecessary abstractions
	•	Avoid over-engineering

6. Multi-Tenancy
Each business is a tenant.
Tenant isolation is a critical security requirement.
A user belonging to Business A must never be able to access Business B's data.
The backend must protect against:
	•	IDOR
	•	Cross-tenant access
	•	Manipulated business IDs
	•	Incorrect authorization
	•	Insecure direct object references
Never blindly trust a businessId supplied by the frontend.
Tenant context should be derived from the authenticated user and their business membership.

7. Roles
Planned roles:
	•	SUPER_ADMIN
	•	BUSINESS_OWNER
	•	MANAGER
	•	EMPLOYEE
	•	CUSTOMER
The exact permissions of each role must be defined during specification and architecture planning.

8. Authentication
The authentication system should support:
	•	Registration
	•	Login
	•	Logout
	•	JWT access tokens
	•	Refresh tokens
	•	Refresh token rotation
	•	Secure password hashing
	•	Email verification
	•	Password reset
	•	Optional Google OAuth
Security of token storage and secrets must be explicitly designed.

9. Database
PostgreSQL is the main source of truth for business data.
Potential tables:
	•	users
	•	businesses
	•	business_members
	•	roles
	•	customers
	•	employees
	•	services
	•	employee_services
	•	working_hours
	•	blocked_times
	•	appointments
	•	appointment_status_history
	•	notifications
	•	notification_templates
	•	subscription_plans
	•	subscriptions
	•	payments
	•	social_connections
	•	booking_sources
	•	audit_logs
	•	refresh_tokens
	•	email_verification_tokens
	•	password_reset_tokens
For important tables define:
	•	Primary keys
	•	Foreign keys
	•	Unique constraints
	•	Indexes
	•	Relationships
	•	Data integrity constraints
Database changes must be managed with Flyway migrations.

10. Appointment Engine
The appointment/availability engine is one of the most important parts of the system.
Availability must be calculated dynamically.
Availability depends on:

Business Hours
+
Employee Working Hours
+
Service Duration
+
Existing Appointments
+
Blocked Times
+
Breaks
+
Holidays
+
Employee Vacation
+
Timezone
+
Daylight Saving Time

Do not use a static predefined list of slots as the source of truth.
Example:
If a service requires 45 minutes, a 30-minute remaining window cannot be returned as an available slot.
The system must support:
	•	Availability calculation
	•	Appointment creation
	•	Cancellation
	•	Rescheduling
	•	Employee assignment
	•	Conflict detection
	•	Timezone handling
	•	DST handling

11. Double Booking / Concurrency
The system must prevent double booking.
Example:

Customer A ──> 10:00
Customer B ──> 10:00

If both requests arrive simultaneously, only one should successfully create the appointment.
Architecture must explicitly address:
	•	Transactions
	•	Database locking and/or constraints
	•	Isolation levels
	•	Race conditions
	•	Redis only where justified
A real concurrency/integration test must eventually verify this behavior.

12. Notifications
The system should support:
	•	Email
	•	WhatsApp
	•	Future notification providers
Notification examples:
	•	Booking confirmation
	•	Appointment reminder
	•	Cancellation
	•	Rescheduling
Notifications should be asynchronous where appropriate.
Potential flow:

Appointment Created
        ↓
Event / Job
        ↓
Notification Service
        ↓
+----------------+
|                |
Email         WhatsApp

The system should support:
	•	Retries
	•	Failure handling
	•	Idempotency
	•	Notification history
	•	Templates

13. WhatsApp
Plan an integration with WhatsApp Business / Cloud API.
Do not assume an external API supports a feature without verifying it.
Use an abstraction such as:

NotificationProvider
    |
    +-- EmailProvider
    +-- WhatsAppProvider

Provider-specific logic should remain isolated from the core domain.

14. Social / Marketing Integrations
Potential sources:
	•	Instagram
	•	TikTok
	•	Google
	•	Direct
	•	Referral
Booking links should support attribution.
Possible mechanisms:
	•	UTM parameters
	•	booking_sources
	•	attribution information
Example:

Instagram
    ↓
Booking Link
    ↓
Bookly
    ↓
Appointment

The business owner should eventually be able to see where customers came from.

15. Payments
Design for:
	•	Full payments
	•	Deposits
	•	Refunds
	•	Payment status
	•	Failed payments
	•	Payment webhooks
Use a PaymentProvider abstraction rather than tightly coupling the business logic to one payment provider.
Webhook handling must consider:
	•	Signature verification
	•	Duplicate events
	•	Idempotency
	•	Retries
	•	Failure handling

16. SaaS Subscriptions
Possible plans:
FREE
	•	Limited employees
	•	Limited appointments
	•	Basic booking
PRO
	•	More employees
	•	More appointments
	•	WhatsApp
	•	Analytics
BUSINESS
	•	More/unlimited employees
	•	Advanced analytics
	•	Integrations
	•	Advanced features
The architecture should separate:

Subscription
Plan
Features
Usage Limits


17. Analytics
Business dashboard should eventually provide:
	•	Appointment count
	•	Revenue
	•	New customers
	•	Cancellation rate
	•	No-show rate
	•	Popular services
	•	Employee performance
	•	Peak hours
	•	Booking sources

18. Frontend
Use:
	•	Next.js
	•	React
	•	TypeScript
	•	Tailwind
	•	TanStack Query
	•	React Hook Form
	•	Zod
Main dashboard routes:

/dashboard
/dashboard/calendar
/dashboard/appointments
/dashboard/customers
/dashboard/employees
/dashboard/services
/dashboard/analytics
/dashboard/integrations
/dashboard/settings
/dashboard/billing

Public booking:

/book/{businessSlug}

Frontend should handle:
	•	Authentication
	•	Dashboard
	•	Calendar
	•	Appointments
	•	Customers
	•	Employees
	•	Services
	•	Analytics
	•	Integrations
	•	Billing
	•	Settings
	•	Public booking
Also include:
	•	Loading states
	•	Empty states
	•	Error states
	•	Responsive UI
	•	Form validation
	•	Accessibility

19. Redis
Redis should only be used where there is a real benefit.
Potential uses:
	•	Caching
	•	Rate limiting
	•	Background jobs
	•	Notification coordination
	•	Temporary data
	•	Distributed locking when justified
PostgreSQL remains the source of truth for business-critical data.

20. Testing
Testing is an important part of the project.
Required categories:
Unit Tests
For business/domain logic.
Integration Tests
For:
	•	PostgreSQL
	•	Repositories
	•	Transactions
	•	Security
	•	Redis where appropriate
API / Controller Tests
For REST endpoints.
Security Tests
For:
	•	Authentication
	•	Authorization
	•	Tenant isolation
	•	IDOR
Concurrency Tests
Especially for appointment booking.
Use Testcontainers when real infrastructure is needed.

21. Docker
Local development should be possible with:

docker compose up

Main services:

Frontend
Backend
PostgreSQL
Redis

Need to define:
	•	Environment variables
	•	Volumes
	•	Networking
	•	Health checks

22. CI/CD
Use GitHub Actions.
Potential pipeline:

Push / Pull Request
        ↓
Build
        ↓
Lint
        ↓
Unit Tests
        ↓
Integration Tests
        ↓
Docker Build
        ↓
Deploy

Different behavior can be defined for:
	•	Pull Requests
	•	Main branch
	•	Releases/tags

23. Production Deployment
Eventually deploy Bookly to the cloud.
Need:
	•	Frontend hosting
	•	Backend hosting
	•	PostgreSQL
	•	Redis
	•	HTTPS
	•	Domain
	•	Environment variables
	•	Secrets management
	•	Database migrations
	•	Backups
	•	Monitoring
	•	Logging
	•	Health checks
Do not over-engineer the first deployment.

24. Repository Structure
Suggested:

Bookly/
├── CLAUDE.md
├── README.md
├── .gitignore
├── docker-compose.yml
├── backend/
├── frontend/
├── infrastructure/
└── .github/
    └── workflows/


25. CLAUDE.md
The project should have a CLAUDE.md containing the project's coding and development rules.
It should cover:
	•	Java/Spring coding standards
	•	Naming conventions
	•	Controller/Service separation
	•	DTO usage
	•	JPA practices
	•	Database/Flyway rules
	•	Multi-tenancy rules
	•	Security rules
	•	Appointment domain rules
	•	Concurrency rules
	•	Integration abstractions
	•	Webhook rules
	•	Async processing
	•	Redis usage
	•	REST API conventions
	•	Error handling
	•	Validation
	•	Testing
	•	Logging
	•	Git conventions
	•	Development workflow
General workflow:

Understand
    ↓
Plan
    ↓
Implement
    ↓
Test
    ↓
Review

Claude should investigate the existing project before changing it.

26. Important Academic Context
This project is also part of an LLM course.
The course materials explain how to work effectively with AI/LLM systems.
Module 6 is especially important because it teaches the methodology for framing, specifying, verifying, reviewing and auditing work with models.
The lecturer explicitly stated:
"Your own project differs in that the specification is yours. It is an independent application of your own design and subject, framed in the discipline Module 6 teaches, and it runs in parallel with the running project on the same module beats: when we frame the running project, you frame this; when we specify, verify, review, and audit it, you do the same for yours the same week."
The lecturer also stated:
"What is specific here is the framing document, with its problem statement, testable definition of done, and out-of-scope list, and a commit history across at least three full turns of the spiral."
Therefore, the project is NOT simply:

Build application → Done

The important academic requirement is to demonstrate the LLM development methodology through the project.

27. Spiral / Iterative Process
The project should be developed in multiple documented turns of a spiral.
General process:

Framing
    ↓
Specification
    ↓
Planning
    ↓
Implementation
    ↓
Verification
    ↓
Review
    ↓
Audit
    ↓
Commit
    ↓
Next Turn

At least three full turns of the spiral need to appear in the Git history.
The exact meaning of each stage should be aligned with the course materials, especially Module 6.

28. Framing Document
A formal framing document is required.
It should include at least:
Problem Statement
What real problem Bookly solves.
Scope
What is included in the current project/turn.
Out of Scope
What is explicitly excluded from the current scope.
Testable Definition of Done
Objective conditions that allow us to verify that the work is complete.
Acceptance Criteria
Specific conditions that can be tested.
The framing document should describe Bookly specifically rather than copying the lecturer's example/domain.

29. Plan Mode
Claude should be used in Plan Mode before implementation.
The purpose is to:
	•	Understand the problem
	•	Understand the scope
	•	Identify requirements
	•	Propose architecture
	•	Identify risks
	•	Define verification strategy
	•	Define tests
	•	Define Definition of Done
	•	Identify open questions
	•	Wait for approval before implementation
Do not ask Claude to build the entire application at once.
Plan Mode is part of the larger LLM methodology, not simply a one-time code-generation step.

30. Architecture Review Before Implementation
Before implementation, review Claude's plan.
Pay particular attention to:
	1	Multi-tenancy
	2	Database design
	3	Appointment engine
	4	Double booking
	5	Timezones
	6	Security
	7	External APIs
	8	Payments
	9	Notifications
	10	Redis usage
	11	Testing
	12	Scalability
	13	Over-engineering
The architecture should be approved before implementation starts.

31. Suggested Technical Development Phases
These phases are technical guidance and should be mapped to the academic spiral/turns rather than blindly treated as separate academic turns.
Phase 1 — Foundation
	•	Repository
	•	CLAUDE.md
	•	Docker
	•	Spring Boot
	•	PostgreSQL
	•	Flyway
	•	Authentication
	•	Users
	•	Businesses
	•	Memberships
	•	Roles
	•	Multi-tenancy
Phase 2 — Business Management
	•	Employees
	•	Services
	•	Employee-service relationships
	•	Working hours
	•	Blocked times
	•	Vacations
Phase 3 — Core Booking
	•	Availability engine
	•	Appointments
	•	Cancellation
	•	Rescheduling
	•	Double booking protection
	•	Concurrency tests
Phase 4 — Web Application
	•	Next.js
	•	Dashboard
	•	Calendar
	•	Customers
	•	Employees
	•	Services
	•	Public booking page
Phase 5 — Notifications
	•	Redis
	•	Background jobs
	•	Email
	•	Notification history
	•	WebSockets if justified
Phase 6 — Integrations
	•	WhatsApp
	•	Instagram / Meta
	•	TikTok
	•	Booking attribution
Phase 7 — Money
	•	Payments
	•	Deposits
	•	Refunds
	•	Subscriptions
	•	Usage limits
Phase 8 — Analytics
	•	Revenue
	•	Appointments
	•	Customers
	•	Cancellation/no-show
	•	Sources
	•	Employee/service metrics
Phase 9 — Production
	•	Security hardening
	•	Full testing
	•	CI/CD
	•	Monitoring
	•	Backups
	•	Cloud deployment
Future
	•	Android Kotlin + Jetpack Compose
	•	iOS Swift + SwiftUI

32. Git / Commit History
Git history is important for the academic requirements.
The history should clearly demonstrate development across at least three complete spiral turns.
Commits should be meaningful and show actual evolution.
Example conceptual structure:

Turn 1
  framing
  specification
  implementation
  verification
  review
  audit
  commit

Turn 2
  framing/update
  specification
  implementation
  verification
  review
  audit
  commit

Turn 3
  framing/update
  specification
  implementation
  verification
  review
  audit
  commit

Do not artificially create commits just to satisfy the requirement.
The commits should correspond to real project progress and documented work.

33. What Not To Do
Do not:
	•	Build the entire application in one Claude request
	•	Accept generated code without verification
	•	Trust frontend businessId values
	•	Ignore concurrency
	•	Use static slots as the availability source of truth
	•	Assume external APIs support capabilities without verification
	•	Add infrastructure only to appear sophisticated
	•	Introduce microservices prematurely
	•	Skip tests
	•	Skip security review
	•	Skip audit/review stages
	•	Ignore the academic framing/specification requirements

34. Final Target
The final Bookly project should contain:
Backend
	•	Java 21
	•	Spring Boot
	•	Spring Security
	•	JWT
	•	PostgreSQL
	•	Flyway
	•	JPA/Hibernate
	•	Redis
	•	REST API
	•	OpenAPI
	•	Multi-tenancy
	•	RBAC
	•	Appointment engine
	•	Concurrency protection
	•	Notifications
	•	WhatsApp integration
	•	Payments
	•	Subscriptions
	•	Analytics
	•	Audit logs
	•	External integrations
	•	Unit tests
	•	Integration tests
	•	Security tests
	•	Concurrency tests
Frontend
	•	Next.js
	•	React
	•	TypeScript
	•	Tailwind
	•	TanStack Query
	•	React Hook Form
	•	Zod
	•	Dashboard
	•	Calendar
	•	Appointments
	•	Customers
	•	Employees
	•	Services
	•	Analytics
	•	Billing
	•	Integrations
	•	Public booking page
	•	Responsive UI
Infrastructure
	•	Docker
	•	Docker Compose
	•	GitHub Actions
	•	Cloud deployment
	•	HTTPS
	•	PostgreSQL
	•	Redis
	•	Environment configuration
	•	Backups
	•	Monitoring
	•	Logging
	•	Health checks
Future Mobile
	•	Android → Kotlin + Jetpack Compose
	•	iOS → Swift + SwiftUI
Academic / LLM Deliverables
	•	Framing document
	•	Problem Statement
	•	Scope
	•	Out-of-Scope
	•	Testable Definition of Done
	•	Specification
	•	Verification
	•	Review
	•	Audit
	•	At least 3 full spiral turns
	•	Git commit history demonstrating the turns

35. Immediate Next Step
Before writing implementation code:
	1	Review the course materials, especially Module 6.
	2	Determine exactly how the lecturer defines:
	◦	Framing
	◦	Specification
	◦	Verification
	◦	Review
	◦	Audit
	◦	Spiral / Turn
	3	Create the Bookly Framing Document according to the course methodology.
	4	Define the first Turn's scope.
	5	Define a testable Definition of Done.
	6	Define the Out-of-Scope list.
	7	Use Claude Plan Mode to plan the first Turn.
	8	Review the plan before implementation.
	9	Implement only the approved scope.
	10	Verify it.
	11	Review it.
	12	Audit it.
	13	Commit it.
	14	Repeat for at least three complete turns.
The course methodology should determine the exact workflow and documentation structure.
Bookly is the application/domain.
Module 6 methodology is the development process.
