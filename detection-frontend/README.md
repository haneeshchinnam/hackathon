# Sentinel AML frontend prototype

A React + TypeScript analyst workspace for the Sentinel AML backend. Includes a monitoring overview, risk-prioritized alert queue, alert evidence and disposition, linked cases, customer profiles, transaction inspection, configurable detection rules, and CSV ingestion.

## Run

Use Node 22.13+ (or Node 24).

```sh
npm install
npm run dev
```

Open http://localhost:5173. Vite uses a strict port to match backend CORS.

```sh
npm run build
npm run lint
```

## Demo and live modes

The default is an explicitly labeled, interactive demo. Alerts and activity charts are illustrative fixtures; customer and account examples reference the supplied CSVs. Other sample customer identities are masked placeholders. No detection scores or currency conversions are calculated in the browser. Demo workflow edits last until reload, and demo imports only preview the selected file.

Click the analyst profile, or connect from Help & documentation, to sign in to the backend using an existing username/password. Backend defaults to http://localhost:8080; copy `.env.example` to `.env` to override `VITE_BACKEND_URL`.

Live API support includes:

- Role-aware navigation for USER, ANALYST, INGESTOR, and ADMIN.
- Session storage tokens, bearer access authentication, and single-flight refresh/retry.
- Page-local summaries, raw array pagination, status filters, and search within the loaded page.
- Alert detail, rule snapshots, evidence inspection, audit events, and reason-required disposition.
- Linked case creation and forward-only status changes.
- Masked customer lists, customer details with accounts and transactions, and transaction details.
- CSV uploads using multipart field `file`, with returned import report.
- Full detection-rule settings sent to the configuration endpoint.

No backend credentials were supplied, so live integration needs verification against a running authenticated backend. Server validation and authorization remain authoritative.

## Prototype limits

This is a frontend prototype, not the complete production acceptance checklist in the handoff. Navigation uses local screen state. Exchange rate/watchlist administration, registration, assignee selection, dedicated account detail routes, evidence pagination, individual data entry, and JSON batch ingestion are not implemented. Lists use page-local filtering and no aggregate endpoint is assumed. Long nested record/report data appears in readable JSON. Rules require explicit Save. Demo audits are scoped to the current detail view. Fonts use Google Fonts with system fallbacks.

## Files

- `src/App.tsx`: screens, interaction flows, charts, drawers, and role-aware navigation.
- `src/api.ts`: backend connection, token storage, refresh, and error handling.
- `src/data.ts`: typed demo fixtures and rule metadata.
- `src/App.css`: responsive workspace layout and styles.
