# AccessMap — Delhi accessibility, together

An accessibility-first map for community reports on entrances, lifts, ramps, and restrooms. The current build is an initial vertical slice: browse and search places, inspect feature status, and submit a report.

## Place data and attribution

The API imports `backend/src/main/resources/osm/delhi-places.geojson` on its first start when the database is empty. It contains 245 named OpenStreetMap places in the Connaught Place pilot area. Imported places start with no accessibility features; community members can report features and their status. OSM place tags are not treated as verified accessibility reports. Place data is from OpenStreetMap contributors, © OpenStreetMap contributors, available under the Open Database License (ODbL). The map also displays OpenStreetMap tiles and links to attribution.

If the GeoJSON resource is absent, the API loads fictional demo places instead. Demo locations and reports are labelled and must not be used for travel planning. Reports submitted to the local prototype only update the local database; email verification, trust scoring, photo uploads, and notifications are not part of this milestone.

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

## Deploy a shareable demo (Render + Neon)

The checked-in `render.yaml` creates a public static frontend and a Docker-based Spring Boot API. The API uses Neon PostgreSQL with Flyway migrations. Public browsing, account registration, login, and reports are enabled on this shareable prototype; moderator review is restricted to the configured moderator account. The prototype does not yet include email verification or password recovery, so public account and report abuse safeguards are limited.

1. Push this repository to your GitHub account. Create a free PostgreSQL project in Neon and note its host, database name, username, and password. Keep the database credentials private.
2. In Render, create a **Blueprint** from your GitHub repository and apply the services defined in `render.yaml`. The names `accessmap-api` and `accessmap-demo` determine the URLs used by the configuration; if you rename either service, update the API URL in `render.yaml` and the frontend origin in the API environment variables.
3. When Render requests unsynced API environment variables, enter:
   - `DATABASE_URL`: `jdbc:postgresql://YOUR_NEON_HOST/YOUR_DATABASE?sslmode=require`
   - `DATABASE_USERNAME`: the Neon database username
   - `DATABASE_PASSWORD`: the Neon database password
   - `ACCESSMAP_JWT_SECRET`: generate a private Base64 key locally with PowerShell:

     ```powershell
     $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
     $bytes = New-Object byte[] 32
     $random.GetBytes($bytes)
     [Convert]::ToBase64String($bytes)
     ```

     Paste the output directly into Render's secret field. Do not commit it or send it in chat.

   - `ACCESSMAP_MODERATOR_EMAIL`: email address for the moderator account
   - `ACCESSMAP_MODERATOR_PASSWORD`: password for that account (at least 16 characters, at most 72 UTF-8 bytes)

   The blueprint sets the production profile, CORS origin, health check, public contributions, and moderator account credentials. Keep all passwords and generated secrets private. On startup, the API creates the moderator account if it does not exist, or promotes the existing account if its password matches the configured password. Its moderator role is stored in Neon and survives restarts. To configure an existing account, use the same email and password that account already uses.
4. Wait for both Render services to finish deploying. Open `https://accessmap-api.onrender.com/actuator/health` and confirm it reports `UP`, then visit `https://accessmap-demo-38bc.onrender.com`. The API imports the bundled 245-place dataset into Neon on first start.
5. Verify the site displays OSM attribution, login/registration work, reports can be submitted, and the configured moderator can review them.

Render's free API instance may sleep while idle, so the first request after a quiet period can be slow; reload the demo once the service wakes. Check current Render and Neon free-tier quotas and retention limits before relying on the deployment. OpenStreetMap's public tile servers have a usage policy and are not a guaranteed production tile service. This deployment is a shareable prototype, not a production community launch.

## Moderator account behavior

New community reports are pending until approved by a moderator, and pending or rejected reports do not appear on the public map. Existing reports are treated as approved during the schema upgrade.

For local development, set the moderator email and enter a password of at least 16 characters (at most 72 UTF-8 bytes) in the same PowerShell window before starting the API:

```powershell
$env:ACCESSMAP_MODERATOR_EMAIL = Read-Host "Moderator email"
$moderatorPassword = Read-Host "Moderator password" -AsSecureString
$env:ACCESSMAP_MODERATOR_PASSWORD = ([System.Net.NetworkCredential]::new("", $moderatorPassword)).Password
mvn spring-boot:run
```

The API creates that account on first start. If the email already belongs to an account, its existing password must match the configured password before it is granted moderator access. In Render, set the same values as the `ACCESSMAP_MODERATOR_EMAIL` and `ACCESSMAP_MODERATOR_PASSWORD` environment variables; the moderator role is saved in Neon. If both settings are omitted, reports remain pending and no account can review them. Local environment variables are not saved after the PowerShell window closes.

## API

- `GET /api/places?search=<text>` — list places and their latest non-expired feature reports.
- `POST /api/places/{placeId}/features` — add a reported feature and its first status, for example `{ "type": "LIFT", "label": "Main building lift", "status": "WORKING", "note": "Optional detail" }`.
- `POST /api/places/{placeId}/features/{featureId}/reports` — submit a new status for an existing feature, for example `{ "status": "OUT_OF_SERVICE", "note": "Optional detail" }`.
- `GET /api/moderation/reports` — list pending reports (moderator role required).
- `POST /api/moderation/reports/{reportId}/decision` — approve or reject a report (moderator role required).
- `GET /api/moderation/reports/audit` — latest 100 moderation decisions (moderator role required).
- `GET /actuator/health` — API health.

Reports currently use a 14-day TTL for lifts/restrooms and a 90-day TTL for entrances/ramps. Expired reports are excluded from the current status; history remains in the database.

Report submissions require a signed-in account. An account can submit up to 10 reports per rolling hour and 50 per rolling day, and can report the same feature only once per 24 hours. Duplicate reports return HTTP 409; exceeded limits return HTTP 429.

New reports stay pending until moderator approval. Moderators can review them from the **Moderation** button after signing in with the configured moderator account. Approval makes the report visible; rejection keeps it off the map. Only configured moderators can read reporter emails in the review queue.

Moderation decisions are recorded with the report, moderator account, decision, and timestamp. Moderators can view the latest 100 decisions in the queue's **Decision history** tab. The audit endpoint and records are restricted to moderators and are not editable through the API.

## Accounts and report access

The API supports email/password registration and login. Passwords are stored as BCrypt hashes; successful auth returns a short-lived (30 minute by default) JWT. Report creation requires `Authorization: Bearer <token>`. The local development JWT secret is a public placeholder and must never be used in a deployment. For the `production` profile, set `ACCESSMAP_JWT_SECRET` to a randomly generated Base64-encoded key of at least 32 bytes.

The frontend keeps the access token in memory only; it is cleared on refresh/sign-out. Report submissions are limited per account to 10 per rolling hour and 50 per rolling day; the same account cannot report the same feature again within 24 hours. These are basic account-level prototype safeguards, not distributed or IP-based abuse protection. Email verification, password reset, refresh tokens, account deletion/export, moderator audit logging, and account controls are not implemented yet. Do not expose public account creation or reporting to the internet until stronger abuse controls, HTTPS, moderation procedures, audit logging, privacy controls, and WCAG 2.2 AA verification are in place. The free-tier provider quotas and OpenStreetMap tile policy must also be checked before launch.

## Before production

Email verification, password recovery, stronger distributed and IP-based abuse controls, account deletion/export, audit-log retention/monitoring and operational procedures, trust/confidence scoring, privacy controls, and WCAG 2.2 AA verification are still required before a public launch. Moderation is a basic configured-account queue. Check free-tier provider quotas and the OpenStreetMap tile policy before launch.
