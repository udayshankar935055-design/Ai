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

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
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

test("Unauthenticated user cannot read or write conversations or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .set({
        userId: ALICE_UID,
        title: "Unauthorized",
        modelMode: "GENERAL",
        lastMessagePreview: "",
        messageCount: 0,
        pinned: false,
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("Authenticated owner: valid Kotlin conversation & message payloads succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const convRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("conversations")
    .doc("conv_1");

  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      title: "Quantum Computing Basics",
      modelMode: "GENERAL",
      lastMessagePreview: "",
      messageCount: 0,
      pinned: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Rename action (action-based partial update)
  await assertSucceeds(
    convRef.update({
      title: "Quantum Entanglement",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Update preview & message count
  await assertSucceeds(
    convRef.update({
      lastMessagePreview: "Superposition allows qubits...",
      messageCount: 2,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Add user message (optional fields omitted)
  await assertSucceeds(
    convRef.collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      role: "user",
      text: "Explain quantum entanglement simply.",
      modelId: "gemini-4-hydrogen",
      sources: [],
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Add model message with optional thoughtText & sources
  await assertSucceeds(
    convRef.collection("messages").doc("msg_2").set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      role: "model",
      text: "Imagine two magic dice...",
      thoughtText: "Simplify using analogy for clarity.",
      modelId: "gemini-4-carbon",
      sources: ["https://example.com/quantum"],
      createdAt: new Date(Date.now() - 500),
    })
  );

  // List queries succeed for owner (even when empty or populated)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertSucceeds(convRef.collection("messages").get());
});

test("Rejects @DocumentId 'id' leak, immutable createdAt change, future timestamp, and shadow fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const convRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("conversations")
    .doc("conv_1");

  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      title: "Valid Chat",
      modelMode: "GENERAL",
      lastMessagePreview: "",
      messageCount: 0,
      pinned: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject ghost field / id field on create
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_2")
      .set({
        id: "conv_2",
        userId: ALICE_UID,
        title: "Invalid",
        modelMode: "GENERAL",
        lastMessagePreview: "",
        messageCount: 0,
        pinned: false,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    convRef.update({
      title: "Tampered",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_future")
      .set({
        userId: ALICE_UID,
        title: "Future Chat",
        modelMode: "GENERAL",
        lastMessagePreview: "",
        messageCount: 0,
        pinned: false,
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // Reject message update (messages are immutable)
  await assertSucceeds(
    convRef.collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      conversationId: "conv_1",
      role: "user",
      text: "Hello",
      modelId: "gemini-4-hydrogen",
      sources: [],
      createdAt: created,
    })
  );
  await assertFails(
    convRef.collection("messages").doc("msg_1").update({
      text: "Tampered",
    })
  );
});

test("Cross-user isolation: Bob cannot read, list, create, update, or delete Alice's chats", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const past = new Date(Date.now() - 10000);
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("alice_conv")
      .set({
        userId: ALICE_UID,
        title: "Alice Secret",
        modelMode: "GENERAL",
        lastMessagePreview: "Secret",
        messageCount: 1,
        pinned: false,
        createdAt: past,
        updatedAt: past,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("alice_conv")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("alice_conv")
      .update({ title: "Hijacked", updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("alice_conv")
      .delete()
  );
});
