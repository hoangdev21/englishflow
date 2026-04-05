# EnglishFlow Cloud Migration Setup

## 1) Firebase services required
- Firebase Authentication
  - Enable providers: Email/Password, Google.
- Cloud Firestore
  - Create database in production mode.
- (Optional) Firebase Storage for avatar images.

## 2) Firestore collections used by app

### users/{uid}
Main profile + leaderboard data.

Suggested schema:
- uid: string
- email: string
- displayName: string
- photoUrl: string
- role: "user" | "admin"
- status: "active" | "locked"
- totalXp: number
- xpToday: number
- learnedWords: number
- currentStreak: number
- bestStreak: number
- totalWordsScanned: number
- totalStudyMinutes: number
- createdAt: timestamp
- updatedAt: timestamp
- lastLoginAt: timestamp
- lastStudyAt: timestamp

### users/{uid}/learned_words/{wordId}
Realtime learned words per user.

Suggested schema:
- word: string
- ipa: string
- meaning: string
- wordType: string
- example: string
- exampleVi: string
- usage: string
- note: string
- domain: string
- topic: string
- learnedAt: number (epoch millis)
- updatedAt: number (epoch millis)

### users/{uid}/study_sessions/{sessionId}
Realtime study sessions per user.

Suggested schema:
- startTime: number (epoch millis)
- endTime: number (epoch millis)
- wordsLearned: number
- domain: string
- topic: string
- xpEarned: number
- updatedAt: number (epoch millis)

### custom_vocabulary/{word}
Global vocabulary content used by the learning flow.

Suggested schema:
- word: string
- meaning: string
- ipa: string
- example: string
- exampleVi: string
- usage: string
- isLocked: boolean
- source: "seed" | "user" | "admin"
- domain: string
- updatedAt: number (epoch millis)

### map_lessons/{lessonId}
Remote-driven Interactive Journey lessons (Học - Hành - Kiểm tra).

Suggested schema:
- lesson_id: string (e.g. "lesson_01")
- order: number (display order in journey)
- title: string
- emoji: string
- min_level: string (e.g. "A1")
- prompt_key: string
- role_description: string (scenario for roleplay)
- min_exchanges: number
- keywords: array<string>
- status: string ("available" | "draft" | "archived")
- flow_steps: array<object>
  - type: string ("intro" | "vocabulary" | "situational_quiz")
  - content: string (for intro)
  - word: string (for vocabulary)
  - ipa: string
  - meaning: string
  - instruction: string
  - hint: string
  - question: string (for quiz)
  - expected_keyword: string
  - accepted_answers: array<string>

### access_logs/{id}
Track successful logins.

Suggested schema:
- uid: string
- email: string
- displayName: string
- provider: "password" | "google" | "session"
- loginAt: timestamp

## 3) Security Rules
- Rules file: firestore.rules
- Publish rules in Firebase Console -> Firestore Database -> Rules.

## 4) Create first admin account
1. Register a normal account in app.
2. Open Firestore console.
3. Set field role = "admin" for that user document.
4. Re-login: app will route to Admin Dashboard.

## 5) Current migration scope implemented
- Auth moved from LocalAuthStore/SQLite to FirebaseAuth.
- User profile + role stored in Firestore users collection.
- Splash/Login/Register route by role.
- Admin Dashboard in app:
  - View total users, DAU, average XP.
  - Search users.
  - Promote/demote role.
  - Reward +50 XP.
  - Lock/unlock account.
  - View recent access logs.
- Leaderboard moved to cloud (Firestore users).
- Core progress sync (XP/streak/learned words/scanned/study minutes) pushed to Firestore from repository updates.
- Learned words, study sessions and custom vocabulary now have Firestore realtime listeners in AppRepository.
- One-time backfill migrates legacy Room data to Firestore when user signs in.

## 6) Notes
- Room is still used for vocabulary/content cache and learning internals.
- LocalAuthStore class remains in source for backward reference but is no longer used by app flows.
- Room still acts as fallback if Firestore data has not loaded yet.
- custom_vocabulary cloud collection is admin-write in rules; non-admin user-added local entries are kept via Room fallback merge.
- For stricter privacy, you can split public leaderboard fields into a separate public collection later.

## 7) map_lessons import automation (Firebase Admin + Node)
- Tool path: `tools/firestore-import`
- Install: `cd tools/firestore-import && npm install`
- Dry run validation:
  - `npm run import:map-lessons:dry`
- Import/upsert to Firestore:
  - `npm run import:map-lessons`
- Import/upsert with explicit options (recommended when passing flags in PowerShell):
  - `node import-map-lessons.js --seed ..\..\map_lessons_seed.json --service-account ..\..\secrets\service-account.json`
- Import and remove documents not present in seed:
  - `npm run import:map-lessons:delete-missing`
- Import and remove documents not present in seed with explicit options:
  - `node import-map-lessons.js --seed ..\..\map_lessons_seed.json --delete-missing`

Credential options (pick one):
- CLI flag: `--service-account <path_to_service_account_json>`
- Env file path: `FIREBASE_SERVICE_ACCOUNT_PATH` or `GOOGLE_APPLICATION_CREDENTIALS`
- Env raw JSON: `FIREBASE_SERVICE_ACCOUNT_JSON`
