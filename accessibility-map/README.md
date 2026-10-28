# AccessMap

**Know before you go.** AccessMap is a community-powered accessibility map for Delhi. Search nearby places, check current reports about entrances, lifts, ramps, and restrooms, or share an observation to help someone plan with more confidence.

## Live links

- **[Open the AccessMap demo](https://accessmap-demo-38bc.onrender.com/)**
- [API health](https://accessmap-api.onrender.com/actuator/health) · [Places API](https://accessmap-api.onrender.com/api/places)
- [GitHub repository](https://github.com/Asmitcod/accessmap) · [Frontend source](https://github.com/Asmitcod/accessmap/tree/main/accessibility-map/frontend) · [Backend source](https://github.com/Asmitcod/accessmap/tree/main/accessibility-map/backend) · [Render Blueprint](../render.yaml)
- [Render dashboard](https://dashboard.render.com/) · [Neon console](https://console.neon.tech/)
- [OpenStreetMap copyright and attribution](https://www.openstreetmap.org/copyright)

The API is hosted on Render and uses PostgreSQL hosted by Neon. The free API service may sleep when idle, so the first request can take longer while it wakes.
The map displays a bundled OpenStreetMap location snapshot immediately while the API connects; the snapshot does not contain live community reports or verified accessibility information. Live places and reports replace it when the API responds. For reliably fast live data on a first visit, the API needs an always-on host.

## What you can do

- Browse and search 245 OpenStreetMap locations in the Connaught Place pilot area.
- Select places on the map and see the latest approved accessibility reports.
- Filter by feature type and how recently a report was confirmed.
- Create an account and report an entrance, lift, ramp, restroom, or a new accessibility feature.
- Update the status of an existing feature and leave an optional note.
- Sign in as the configured moderator to review pending submissions and see moderation history.
- Use the responsive map and report flow on desktop and mobile.

All accessibility information is community-reported and may change. OpenStreetMap place data does not indicate verified accessibility. Contact a venue to confirm critical access needs before travelling.

## Reporting and moderation

1. Select a place on the map or search for it.
2. Choose **Report an access issue** near the top of the page, or report a new feature from the place details.
3. Sign in or create an account, then describe what you observed.
4. Reports stay off the public map until a moderator reviews them. Approved reports appear with their confirmation date; rejected reports do not appear.

The moderator account is provisioned when the API starts using the Render environment variables `ACCESSMAP_MODERATOR_EMAIL` and `ACCESSMAP_MODERATOR_PASSWORD`. The moderator role is saved in the database, so it persists across restarts. To promote an existing account, configure its exact email and current password. If that email already exists and the configured password does not match, the API intentionally fails startup rather than silently changing the account's credentials.

## Technology

- **Frontend:** React, TypeScript, Vite, Leaflet
- **Backend:** Java 21, Spring Boot, Maven, Spring Security, JWT
- **Database:** Neon PostgreSQL in the hosted deployment; file-backed H2 for local development
- **Deployment:** Render Blueprint, Docker, Render static site
- **Map data and tiles:** OpenStreetMap

## Run locally

Install Java 21, Maven, and Node.js 20.19+ (or 22.12+).

### Start the API

From `accessibility-map/backend`:

```powershell
mvn spring-boot:run
```

By default, the API uses a local file-backed H2 database at `backend/data/accessmap.mv.db` and imports the bundled place data the first time it starts. Keep the database file private and back it up if you need to preserve local changes.

### Start the frontend

In another terminal, from `accessibility-map/frontend`:

```powershell
npm ci
npm run dev
```

Open the local URL printed by Vite. Its `/api` requests are proxied to `http://localhost:8080`.

### Configure a local moderator (optional)

Set both variables in the same PowerShell window before starting the API:

```powershell
$env:ACCESSMAP_MODERATOR_EMAIL = Read-Host "Moderator email"
$moderatorPassword = Read-Host "Moderator password" -AsSecureString
$env:ACCESSMAP_MODERATOR_PASSWORD = ([System.Net.NetworkCredential]::new("", $moderatorPassword)).Password
mvn spring-boot:run
```

The password must be at least 16 characters and no more than 72 UTF-8 bytes. If the configured email is already registered, the password must match that account's existing password.

## Deployment configuration

The repository-root [`render.yaml`](../render.yaml) defines the API and static-site services. Render deploys the frontend at [accessmap-demo-38bc.onrender.com](https://accessmap-demo-38bc.onrender.com/) and the API at [accessmap-api.onrender.com](https://accessmap-api.onrender.com/).

When applying the Blueprint, configure the prompted API values in Render's dashboard. Keep secrets out of Git and chat:

- `DATABASE_URL`: Neon JDBC URL, for example `jdbc:postgresql://YOUR_NEON_HOST/YOUR_DATABASE?sslmode=require`
- `DATABASE_USERNAME` and `DATABASE_PASSWORD`: Neon database credentials
- `ACCESSMAP_JWT_SECRET`: private Base64-encoded random key with at least 32 bytes
- `ACCESSMAP_MODERATOR_EMAIL` and `ACCESSMAP_MODERATOR_PASSWORD`: credentials for the moderator account

For the moderator password, use at least 16 characters and no more than 72 UTF-8 bytes. Generate the JWT secret locally in PowerShell; do not reuse the moderator password:

```powershell
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$random.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

The deployment uses the production Spring profile and Flyway migrations. The backend enforces authentication and authorization independently of frontend controls.

## API overview

- `GET /api/places?search=<text>` — list places and approved, non-expired feature reports.
- `POST /api/auth/register` and `POST /api/auth/login` — create an account or receive a JWT.
- `POST /api/places/{placeId}/features` — submit a new feature and its first status.
- `POST /api/places/{placeId}/features/{featureId}/reports` — report a status update.
- `GET /api/moderation/reports` — list pending reports (moderators only).
- `POST /api/moderation/reports/{reportId}/decision` — approve or reject a report (moderators only).
- `GET /api/moderation/reports/audit` — view recent moderation decisions (moderators only).
- `GET /actuator/health` — service health.

Feature reports expire from the current status view after 14 days for lifts and restrooms, and 90 days for entrances and ramps. Accounts are limited to 10 reports per rolling hour and 50 per rolling day, and cannot report the same feature more than once in 24 hours.

## Data, safety, and limitations

The bundled dataset contains 245 named places from the Connaught Place pilot area. Place data is from OpenStreetMap contributors and is available under the [Open Database License (ODbL)](https://www.openstreetmap.org/copyright). The map uses OpenStreetMap tiles; follow the [tile usage policy](https://operations.osmfoundation.org/policies/tiles/), and use a suitable tile provider before scaling traffic.

AccessMap is a shareable prototype, not an emergency or verified travel-planning service. Reports are not professionally audited. This version does not yet include email verification, password recovery, account deletion/export, photo uploads, or robust distributed/IP-based abuse controls. Use a unique password, and do not enter sensitive personal information in report notes. Review privacy, accessibility, moderation operations, abuse protections, and hosting quotas before any broader public launch.
