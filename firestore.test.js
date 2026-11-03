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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validZonePayload(userId, overrides = {}) {
  const now = new Date();
  return {
    userId,
    name: "Plymouth County Fire",
    region: "Plymouth County, MA",
    category: "Fire / EMS",
    safetyProfile: "RX_ONLY_INHIBIT",
    targetRadioModel: "Baofeng UV-5R Mini",
    shareCode: "PLYM-FIRE-26",
    version: 1,
    channelSpecs: [
      "1|PLYM FD DISP|154265000|154265000|OFF|0|131.8|131.8|FM|HIGH|RX_ONLY"
    ],
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validSharedPayload(userId, shareCode = "PLYM-FIRE-26", overrides = {}) {
  const now = new Date();
  return {
    userId,
    authorCallsign: "W1PLY-EOC",
    shareCode,
    name: "Plymouth County Fire",
    region: "Plymouth County, MA",
    category: "Fire / EMS",
    safetyProfile: "RX_ONLY_INHIBIT",
    targetRadioModel: "Baofeng UV-5R Mini",
    version: 1,
    releaseNotes: "Initial Plymouth County Fire dispatch & tactical channels.",
    channelSpecs: [
      "1|PLYM FD DISP|154265000|154265000|OFF|0|131.8|131.8|FM|HIGH|RX_ONLY"
    ],
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("1. Unauthenticated user: cannot read or write zones", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("zones").get());
  await assertFails(unauthDb.collection("zones").doc("zone_1").set(validZonePayload(ALICE_UID)));
});

test("2. Cross-user read: Alice cannot read Bob's zone", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("zones").doc("bob_zone").set(validZonePayload(BOB_UID));
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("zones").doc("bob_zone").get());
});

test("3. Client query alignment: Alice can list her own zones with userId filter, fails without filter", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("zones").doc("alice_zone").set(validZonePayload(ALICE_UID));
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("zones").where("userId", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("zones").get());
});

test("4. Identity spoofing: Alice cannot create a zone owned by Bob", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("zones").doc("spoof_zone").set(validZonePayload(BOB_UID)));
});

test("5. Shadow update / ghost field injection: rejected on create and update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("zones").doc("alice_zone").set(validZonePayload(ALICE_UID)));
  await assertFails(
    aliceDb.collection("zones").doc("alice_zone").update({
      name: "Updated Name",
      ghostField: "hacked",
      updatedAt: new Date(),
    })
  );
});

test("6. Value poisoning: invalid safetyProfile or oversized name rejected on update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("zones").doc("alice_zone").set(validZonePayload(ALICE_UID)));
  await assertFails(
    aliceDb.collection("zones").doc("alice_zone").update({
      safetyProfile: "INVALID_MODE",
      updatedAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.collection("zones").doc("alice_zone").update({
      name: "X".repeat(120),
      updatedAt: new Date(),
    })
  );
});

test("7. Shared codeplugs: Alice can publish, Bob can get by shareCode, Bob cannot overwrite or delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("shared_codeplugs").doc("PLYM-FIRE-26").set(validSharedPayload(ALICE_UID, "PLYM-FIRE-26"))
  );

  // Bob can pull the shared codeplug by shareCode
  await assertSucceeds(bobDb.collection("shared_codeplugs").doc("PLYM-FIRE-26").get());

  // Bob cannot modify or delete Alice's shared codeplug
  await assertFails(
    bobDb.collection("shared_codeplugs").doc("PLYM-FIRE-26").update({
      version: 2,
      updatedAt: new Date(),
    })
  );
  await assertFails(bobDb.collection("shared_codeplugs").doc("PLYM-FIRE-26").delete());
});
