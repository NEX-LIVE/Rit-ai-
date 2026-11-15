const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const DB_ID = "ai-studio-android-22246602-0d11-4790-b7b5-04724c8e1bcc";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function ts() {
  return new Date(Date.now() - 1000);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      databaseId: DB_ID,
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated caller cannot read or write user data, memories, or conversations", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get());
});

test("Authenticated owner can create and update their UserProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    profileRef.set({
      userId: ALICE_UID,
      displayName: "Rajan",
      email: "rajan@example.com",
      selectedVoiceName: "Aoede",
      selectedLanguage: "Hinglish",
      cloudBackupEnabled: true,
      allowAutoMemoryExtraction: true,
      askBeforeSensitiveMemory: true,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    profileRef.update({
      selectedVoiceName: "Kore",
      updatedAt: ts(),
    })
  );
});

test("Authenticated owner cannot inject unknown ghost fields into UserProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Rajan",
      selectedVoiceName: "Aoede",
      selectedLanguage: "Hinglish",
      cloudBackupEnabled: true,
      allowAutoMemoryExtraction: true,
      askBeforeSensitiveMemory: true,
      isAdmin: true,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's memories or conversations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      userId: ALICE_UID,
      category: "IDENTITY",
      content: "User's name is Rajan",
      importance: 5,
      isSensitive: false,
      userConfirmed: true,
      localCreatedAtMs: 1700000000000,
      localUpdatedAtMs: 1700000000000,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_2").set({
      userId: ALICE_UID,
      category: "FACT",
      content: "Injected",
      importance: 3,
      isSensitive: false,
      userConfirmed: true,
      localCreatedAtMs: 1700000000000,
      localUpdatedAtMs: 1700000000000,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );
});

test("Owner can create conversation and child messages, orphan messages are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const convRef = aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1");

  await assertFails(
    convRef.collection("messages").doc("msg_orphan").set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      role: "USER",
      transcript: "Hello Rit",
      timestampMs: 1700000001000,
      durationMs: 1200,
      createdAt: ts(),
    })
  );

  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      title: "Evening Chat",
      startedAtMs: 1700000000000,
      language: "Hinglish",
      voiceName: "Aoede",
      messageCount: 1,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  await assertSucceeds(
    convRef.collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      role: "USER",
      transcript: "Hello Rit, mera naam Rajan hai",
      timestampMs: 1700000001000,
      durationMs: 1800,
      createdAt: ts(),
    })
  );
});
