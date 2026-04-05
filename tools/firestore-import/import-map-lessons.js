#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");

const DEFAULT_COLLECTION = "map_lessons";
const DEFAULT_SEED_PATH = path.resolve(__dirname, "..", "..", "map_lessons_seed.json");
const BATCH_LIMIT = 400;

function parseArgs(argv) {
  const args = {
    seed: DEFAULT_SEED_PATH,
    collection: DEFAULT_COLLECTION,
    dryRun: false,
    deleteMissing: false,
    serviceAccount: ""
  };

  for (let i = 2; i < argv.length; i += 1) {
    const token = argv[i];

    if (token.startsWith("--seed=")) {
      args.seed = token.substring("--seed=".length);
      continue;
    }

    if (token.startsWith("--collection=")) {
      args.collection = token.substring("--collection=".length);
      continue;
    }

    if (token.startsWith("--service-account=")) {
      args.serviceAccount = token.substring("--service-account=".length);
      continue;
    }

    if (token === "--dry-run") {
      args.dryRun = true;
      continue;
    }

    if (token === "--delete-missing") {
      args.deleteMissing = true;
      continue;
    }

    if (token === "--seed") {
      args.seed = requireValue(argv, i, "--seed");
      i += 1;
      continue;
    }

    if (token === "--collection") {
      args.collection = requireValue(argv, i, "--collection");
      i += 1;
      continue;
    }

    if (token === "--service-account") {
      args.serviceAccount = requireValue(argv, i, "--service-account");
      i += 1;
      continue;
    }

    if (token === "--help" || token === "-h") {
      printHelp();
      process.exit(0);
    }

    throw new Error("Unknown argument: " + token);
  }

  args.seed = path.resolve(process.cwd(), args.seed);
  return args;
}

function requireValue(argv, currentIndex, flag) {
  const value = argv[currentIndex + 1];
  if (!value || value.startsWith("--")) {
    throw new Error("Missing value for " + flag);
  }
  return value;
}

function printHelp() {
  console.log("Usage: node import-map-lessons.js [options]");
  console.log("");
  console.log("Options:");
  console.log("  --seed <path>             Path to seed JSON file");
  console.log("  --collection <name>       Firestore collection name (default: map_lessons)");
  console.log("  --service-account <path>  Service account JSON file path");
  console.log("  --dry-run                 Validate and print summary without writing");
  console.log("  --delete-missing          Delete Firestore docs not present in seed");
  console.log("  --help                    Show help");
  console.log("");
  console.log("Auth priority:");
  console.log("  1) --service-account");
  console.log("  2) FIREBASE_SERVICE_ACCOUNT_PATH env");
  console.log("  3) GOOGLE_APPLICATION_CREDENTIALS env");
  console.log("  4) FIREBASE_SERVICE_ACCOUNT_JSON env (raw JSON string)");
}

function readJsonFile(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error("Seed file not found: " + filePath);
  }

  const raw = fs.readFileSync(filePath, "utf8");
  try {
    return JSON.parse(raw);
  } catch (error) {
    throw new Error("Invalid JSON in " + filePath + ": " + error.message);
  }
}

function normalizeLesson(rawLesson, index) {
  if (!rawLesson || typeof rawLesson !== "object" || Array.isArray(rawLesson)) {
    throw new Error("Lesson at index " + index + " must be an object");
  }

  const lessonId = String(rawLesson.lesson_id || "").trim();
  if (!lessonId) {
    throw new Error("Lesson at index " + index + " is missing lesson_id");
  }

  const normalized = {
    lesson_id: lessonId,
    order: toInt(rawLesson.order, index + 1),
    title: toStringSafe(rawLesson.title),
    emoji: toStringSafe(rawLesson.emoji),
    min_level: toStringSafe(rawLesson.min_level || "A1"),
    prompt_key: toStringSafe(rawLesson.prompt_key || lessonId),
    role_description: toStringSafe(rawLesson.role_description),
    min_exchanges: Math.max(2, toInt(rawLesson.min_exchanges, 4)),
    keywords: toStringArray(rawLesson.keywords),
    status: toStringSafe(rawLesson.status || "available"),
    flow_steps: normalizeFlowSteps(rawLesson.flow_steps, lessonId)
  };

  return normalized;
}

function normalizeFlowSteps(flowSteps, lessonId) {
  if (!Array.isArray(flowSteps)) {
    throw new Error("Lesson " + lessonId + " has invalid flow_steps (must be array)");
  }

  return flowSteps.map((step, idx) => {
    if (!step || typeof step !== "object" || Array.isArray(step)) {
      throw new Error("Lesson " + lessonId + " flow_steps[" + idx + "] must be an object");
    }

    const type = toStringSafe(step.type).toLowerCase();
    if (!type) {
      throw new Error("Lesson " + lessonId + " flow_steps[" + idx + "] is missing type");
    }

    const normalized = {
      type,
      content: toStringSafe(step.content),
      word: toStringSafe(step.word),
      ipa: toStringSafe(step.ipa),
      meaning: toStringSafe(step.meaning),
      instruction: toStringSafe(step.instruction),
      question: toStringSafe(step.question),
      expected_keyword: toStringSafe(step.expected_keyword),
      hint: toStringSafe(step.hint),
      accepted_answers: toStringArray(step.accepted_answers)
    };

    return stripEmptyFields(normalized);
  });
}

function toStringSafe(value) {
  if (value === null || value === undefined) {
    return "";
  }
  return String(value).trim();
}

function toInt(value, fallback) {
  const numeric = Number(value);
  if (Number.isFinite(numeric)) {
    return Math.trunc(numeric);
  }
  return fallback;
}

function toStringArray(value) {
  if (!Array.isArray(value)) {
    return [];
  }

  const result = [];
  for (const item of value) {
    const text = toStringSafe(item);
    if (text) {
      result.push(text);
    }
  }
  return result;
}

function stripEmptyFields(obj) {
  const cleaned = {};
  for (const [key, value] of Object.entries(obj)) {
    if (Array.isArray(value)) {
      if (value.length > 0) {
        cleaned[key] = value;
      }
      continue;
    }

    if (typeof value === "string") {
      if (value.trim()) {
        cleaned[key] = value.trim();
      }
      continue;
    }

    if (value !== null && value !== undefined) {
      cleaned[key] = value;
    }
  }
  return cleaned;
}

function getServiceAccountConfig(args) {
  const serviceAccountPath = args.serviceAccount
    ? path.resolve(process.cwd(), args.serviceAccount)
    : (process.env.FIREBASE_SERVICE_ACCOUNT_PATH || process.env.GOOGLE_APPLICATION_CREDENTIALS || "");

  if (serviceAccountPath) {
    if (!fs.existsSync(serviceAccountPath)) {
      throw new Error("Service account file not found: " + serviceAccountPath);
    }

    const content = fs.readFileSync(serviceAccountPath, "utf8");
    try {
      return JSON.parse(content);
    } catch (error) {
      throw new Error("Invalid service account JSON: " + error.message);
    }
  }

  if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
    try {
      return JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON);
    } catch (error) {
      throw new Error("Invalid FIREBASE_SERVICE_ACCOUNT_JSON: " + error.message);
    }
  }

  throw new Error(
    "Missing Firebase credentials. Use --service-account, FIREBASE_SERVICE_ACCOUNT_PATH, GOOGLE_APPLICATION_CREDENTIALS, or FIREBASE_SERVICE_ACCOUNT_JSON."
  );
}

function initFirebaseAdmin(args) {
  const serviceAccount = getServiceAccountConfig(args);

  if (!admin.apps.length) {
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
  }
}

function uniqueLessons(normalizedLessons) {
  const map = new Map();
  for (const lesson of normalizedLessons) {
    if (map.has(lesson.lesson_id)) {
      throw new Error("Duplicate lesson_id found: " + lesson.lesson_id);
    }
    map.set(lesson.lesson_id, lesson);
  }
  return map;
}

function chunk(items, size) {
  const chunks = [];
  for (let i = 0; i < items.length; i += size) {
    chunks.push(items.slice(i, i + size));
  }
  return chunks;
}

async function writeLessons(db, collectionName, lessonMap) {
  const lessons = Array.from(lessonMap.values());
  const collectionRef = db.collection(collectionName);

  let written = 0;
  const groups = chunk(lessons, BATCH_LIMIT);
  for (const group of groups) {
    const batch = db.batch();
    for (const lesson of group) {
      const docRef = collectionRef.doc(lesson.lesson_id);
      batch.set(docRef, lesson, { merge: true });
    }
    await batch.commit();
    written += group.length;
  }

  return written;
}

async function deleteMissingDocs(db, collectionName, lessonIds) {
  const snapshot = await db.collection(collectionName).get();
  const toDelete = [];

  snapshot.forEach((doc) => {
    if (!lessonIds.has(doc.id)) {
      toDelete.push(doc.id);
    }
  });

  if (!toDelete.length) {
    return 0;
  }

  const groups = chunk(toDelete, BATCH_LIMIT);
  for (const group of groups) {
    const batch = db.batch();
    for (const docId of group) {
      batch.delete(db.collection(collectionName).doc(docId));
    }
    await batch.commit();
  }

  return toDelete.length;
}

function summarize(normalizedLessons, args) {
  const lessonIds = normalizedLessons.map((lesson) => lesson.lesson_id);
  console.log("Seed file:", args.seed);
  console.log("Collection:", args.collection);
  console.log("Lessons:", normalizedLessons.length);
  console.log("Lesson IDs:", lessonIds.join(", "));
  console.log("Dry run:", args.dryRun ? "yes" : "no");
  console.log("Delete missing:", args.deleteMissing ? "yes" : "no");
}

async function run() {
  const args = parseArgs(process.argv);
  const seedRaw = readJsonFile(args.seed);

  if (!Array.isArray(seedRaw)) {
    throw new Error("Seed JSON must be an array");
  }

  const normalizedLessons = seedRaw.map((lesson, index) => normalizeLesson(lesson, index));
  const lessonMap = uniqueLessons(normalizedLessons);

  summarize(normalizedLessons, args);

  if (args.dryRun) {
    console.log("Dry run complete. No data was written.");
    return;
  }

  initFirebaseAdmin(args);
  const db = admin.firestore();

  const written = await writeLessons(db, args.collection, lessonMap);
  let deleted = 0;

  if (args.deleteMissing) {
    deleted = await deleteMissingDocs(db, args.collection, new Set(lessonMap.keys()));
  }

  console.log("Import complete.");
  console.log("Written/updated:", written);
  console.log("Deleted:", deleted);
}

run()
  .then(() => {
    process.exitCode = 0;
  })
  .catch((error) => {
    console.error("Import failed:", error.message);
    process.exitCode = 1;
  });
