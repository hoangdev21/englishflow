# Firestore Import Tool

This folder contains a Node.js Firebase Admin script to import journey lessons into Firestore.

## 1) Install dependencies

```bash
cd tools/firestore-import
npm install
```

## 2) Provide Firebase credentials

Use one of these options:

- CLI flag:

```bash
node import-map-lessons.js --service-account ../../secrets/service-account.json
```

- Environment variable (file path):

```bash
set FIREBASE_SERVICE_ACCOUNT_PATH=..\..\secrets\service-account.json
```

- Environment variable (raw JSON):

```bash
set FIREBASE_SERVICE_ACCOUNT_JSON={...}
```

## 3) Run import

- Normal import (upsert by lesson_id, default seed path):

```bash
npm run import:map-lessons
```

- Validate only (no write, default seed path):

```bash
npm run import:map-lessons:dry
```

- Import with explicit options (recommended when passing flags on Windows PowerShell):

```bash
node import-map-lessons.js --seed ..\..\map_lessons_seed.json --service-account ..\..\secrets\service-account.json
```

- Import and remove docs not present in seed (default seed path):

```bash
npm run import:map-lessons:delete-missing
```

- Import and remove docs that are not in seed with explicit options:

```bash
node import-map-lessons.js --seed ..\..\map_lessons_seed.json --delete-missing
```

## Options

- --seed <path> or --seed=<path>
- --collection <name> or --collection=<name>
- --service-account <path> or --service-account=<path>
- --dry-run
- --delete-missing

Default collection: map_lessons

Default seed path: ../../map_lessons_seed.json
