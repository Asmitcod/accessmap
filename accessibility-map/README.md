# AccessMap — Delhi accessibility, together

An accessibility-first map for community reports on entrances, lifts, ramps, and restrooms. The current build is an initial vertical slice: browse and search places, inspect feature status, and submit a report.

## Place data and attribution

The API imports `backend/src/main/resources/osm/delhi-places.geojson` on its first start when the database is empty. It contains 245 named OpenStreetMap places in the Connaught Place pilot area. Imported places start with no accessibility features; community members can report features and their status. OSM place tags are not treated as verified accessibility reports. Place data is from OpenStreetMap contributors, © OpenStreetMap contributors, available under the Open Database License (ODbL). The map also displays OpenStreetMap tiles and links to attribution.

If the GeoJSON resource is absent, the API loads fictional demo places instead. Demo locations and reports are labelled and must not be used for travel planning. Reports submitted to the local prototype only update the local database; email verification, moderation, trust scoring, photo uploads, and notifications are not part of this milestone.

## Technology

- Java 21, Spring Boot 3, Maven
- React, TypeScript, Vite, Leaflet
- File-backed H2 for local development; PostgreSQL/PostGIS profile for production-style persistence
- OpenStreetMap tiles (follow the tile usage policy; use a suitable provider before production traffic)

## Run locally

Install Java 21, Maven, and Node.js 20.19+ (or 22.12+).

1. Start the API from `backend`:

   ```powershell
   mvn spring-boot:run
   ```

   The default profile stores data in `backend/data/accessmap.mv.db` and imports the bundled OpenStreetMap places on first start. Accounts, places, features, and reports survive API restarts. Accessibility statuses remain unknown until a report is submitted. If the OSM dataset is missing, clearly labelled fictional samples are used instead. Keep this local database file private and back it up if you need to preserve your data.

   To use another H2 database URL, set `ACCESSMAP_H2_URL` before starting the API. For production configuration, provide a strong Base64-encoded `ACCESSMAP_JWT_SECRET` (at least 32 random bytes after decoding); the built-in secret is only for local development.

2. Start the frontend from `frontend`:

   ```powershell
   npm install
   npm run dev
   ```

   Open the local URL printed by Vite. The dev server forwards `/api` calls to `http://localhost:8080`.

3. For PostgreSQL development, start the included database and use the production database profile:

   ```powershell
   docker compose up -d database
   cd backend
   $env:SPRING_PROFILES_ACTIVE = "production"
   mvn spring-boot:run
   ```

   The local Compose credentials are for development only. PostgreSQL uses persistent Docker volume storage. Configure strong credentials and a managed database before deployment.

If the API is unavailable, the frontend displays a sample-only preview and disables report submission. Sample preview data is never sent as a real report.

## API

- `GET /api/places?search=<text>` — list places and their latest non-expired feature reports.
- `POST /api/places/{placeId}/features` — add a reported feature and its first status, for example `{ "type": "LIFT", "label": "Main building lift", "status": "WORKING", "note": "Optional detail" }`.
- `POST /api/places/{placeId}/features/{featureId}/reports` — submit a new status for an existing feature, for example `{ "status": "OUT_OF_SERVICE", "note": "Optional detail" }`.
- `GET /actuator/health` — API health.

Reports currently use a 14-day TTL for lifts/restrooms and a 90-day TTL for entrances/ramps. Expired reports are excluded from the current status; history remains in the database.

Report submissions require a signed-in account. An account can submit up to 10 reports per rolling hour and 50 per rolling day, and can report the same feature only once per 24 hours. Duplicate reports return HTTP 409; exceeded limits return HTTP 429.

## Accounts and report access

The API supports email/password registration and login. Passwords are stored as BCrypt hashes; successful auth returns a short-lived (30 minute by default) JWT. Report creation requires `Authorization: Bearer <token>`. The local development JWT secret is a public placeholder and must never be used in a deployment. For the `production` profile, set `ACCESSMAP_JWT_SECRET` to a randomly generated Base64-encoded key of at least 32 bytes.

The frontend keeps the access token in memory only; it is cleared on refresh/sign-out. Report submissions are limited per account to 10 per rolling hour and 50 per rolling day; the same account cannot report the same feature again within 24 hours. These are basic account-level prototype safeguards, not distributed or IP-based abuse protection. Email verification, password reset, refresh tokens, account deletion/export, and moderator/account controls are not implemented yet. Do not expose public account creation or reporting to the internet until stronger abuse controls, HTTPS, moderation, audit logging, privacy controls, and WCAG 2.2 AA verification are in place. The free-tier provider quotas and OpenStreetMap tile policy must also be checked before launch.

## Before production

Email verification, password recovery, stronger distributed and IP-based abuse controls, account deletion/export, moderation, audit logging, trust/confidence scoring, privacy controls, and WCAG 2.2 AA verification are still required before a public launch. Check free-tier provider quotas and the OpenStreetMap tile policy before launch.
