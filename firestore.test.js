const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const MALLORY_UID = "mallory_uid_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated users cannot read or write any Kel mi collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("directory").doc(ALICE_UID).get());
  await assertFails(unauth.collection("handles").doc("alice_k").get());
  await assertFails(unauth.collection("chats").where("participantUids", "array-contains", ALICE_UID).get());
  await assertFails(unauth.collection("moments").where("visibleToUids", "array-contains", ALICE_UID).get());
});

test("Private user profile PII (email, phone, DOB) isolation and partial update validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      phoneNumber: "+1 555 010 2030",
      displayName: "Alice",
      kelMiHandle: "alice_k",
      qqNumber: "10482910",
      gender: "Female",
      dateOfBirth: "1998-05-14",
      ageYears: 28,
      interests: ["Languages", "Music", "Travel"],
      locationLabel: "San Francisco, US",
      timeZoneId: "America/Los_Angeles",
      preferredLanguage: "English",
      autoTranslateEnabled: true,
      profileCompleted: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Bob cannot read or write Alice's private PII profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" }));

  // Alice can partially update profile details
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "Japanese",
      interests: ["Languages", "Photography"],
      updatedAt: pastDate(1000),
    })
  );

  // Shadow update with ghost field or qqNumber tampering fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      qqNumber: "99999999",
      updatedAt: pastDate(500),
    })
  );
});

test("Public key bundle directory & handles allow exact get, block list scraping, and allow OPK claim", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(10000);

  const pubKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE" + "A".repeat(44);
  const sig = "MEUCIQ" + "B".repeat(56);

  await assertSucceeds(
    bobDb.collection("handles").doc("bob_k").set({
      userId: BOB_UID,
      kelMiHandle: "bob_k",
      qqNumber: "20948172",
      displayName: "Bob",
      avatarColorHex: "#0D9488",
      preferredLanguage: "Spanish",
      gender: "Male",
      ageYears: 30,
      interests: ["Tech", "Cooking"],
      locationLabel: "Madrid, ES",
      timeZoneId: "Europe/Madrid",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    bobDb.collection("directory").doc(BOB_UID).set({
      userId: BOB_UID,
      kelMiHandle: "bob_k",
      qqNumber: "20948172",
      displayName: "Bob",
      avatarColorHex: "#0D9488",
      preferredLanguage: "Spanish",
      gender: "Male",
      ageYears: 30,
      interests: ["Tech", "Cooking"],
      locationLabel: "Madrid, ES",
      timeZoneId: "Europe/Madrid",
      identityPublicKey: pubKey,
      signedPreKeyId: 1,
      signedPreKeyPublic: pubKey,
      signedPreKeySignature: sig,
      safetyFingerprint: "12345678901234567890",
      opkCount: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    bobDb.collection("directory").doc(BOB_UID).collection("oneTimePreKeys").doc("opk_1").set({
      keyId: 1,
      publicKey: pubKey,
      ownerUid: BOB_UID,
      createdAt: created,
    })
  );

  await assertSucceeds(aliceDb.collection("handles").doc("bob_k").get());
  await assertSucceeds(aliceDb.collection("directory").doc(BOB_UID).get());
  await assertSucceeds(aliceDb.collection("directory").doc(BOB_UID).collection("oneTimePreKeys").limit(1).get());
  await assertSucceeds(aliceDb.collection("directory").doc(BOB_UID).collection("oneTimePreKeys").doc("opk_1").delete());

  await assertFails(aliceDb.collection("handles").get());
  await assertFails(aliceDb.collection("directory").get());
  await assertFails(aliceDb.collection("directory").doc(BOB_UID).collection("oneTimePreKeys").get());
});

test("Encrypted chat threads & Signal ciphertext messages (TEXT and LOCATION_TIME) isolate non-participants", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();
  const chatId = `${ALICE_UID}__${BOB_UID}`;
  const created = pastDate(10000);
  const pubKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE" + "A".repeat(44);

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      participantUids: [ALICE_UID, BOB_UID],
      createdBy: ALICE_UID,
      lastSenderId: ALICE_UID,
      lastMessagePreviewTag: "Encrypted message",
      lastMessageReceiptStatus: "SENT",
      lastMessageAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").set({
      senderId: ALICE_UID,
      recipientId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      ciphertext: "dGVzdF9jaXBoZXJ0ZXh0X2Jhc2U2NA==",
      iv: "MTIzNDU2Nzg5MDEy",
      senderIdentityKey: pubKey,
      ephemeralKey: pubKey,
      ratchetKey: pubKey,
      usedOpkId: "opk_1",
      messageNumber: 0,
      previousChainLength: 0,
      preKeyMessage: true,
      messageType: "LOCATION_TIME",
      receiptStatus: "SENT",
      createdAt: pastDate(2000),
    })
  );

  await assertSucceeds(bobDb.collection("chats").where("participantUids", "array-contains", BOB_UID).get());
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").where("memberUids", "array-contains", BOB_UID).get()
  );

  // Recipient (Bob) marks message DELIVERED and then READ
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      receiptStatus: "DELIVERED",
      deliveredAt: pastDate(1500),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      receiptStatus: "READ",
      readAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).update({
      lastMessageReceiptStatus: "READ",
      updatedAt: pastDate(1000),
    })
  );

  // Also test subsequent Double Ratchet message (without optional ephemeralKey / usedOpkId)
  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_2").set({
      senderId: ALICE_UID,
      recipientId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      ciphertext: "cmF0Y2hldF9jaXBoZXJ0ZXh0",
      iv: "MTIzNDU2Nzg5MDEy",
      senderIdentityKey: pubKey,
      ratchetKey: pubKey,
      messageNumber: 1,
      previousChainLength: 0,
      preKeyMessage: false,
      messageType: "TEXT",
      receiptStatus: "SENT",
      createdAt: pastDate(1800),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_2").update({
      receiptStatus: "READ",
      deliveredAt: pastDate(1200),
      readAt: pastDate(900),
    })
  );

  // Bob cannot downgrade READ back to DELIVERED or tamper ciphertext, and Alice cannot spoof READ
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      receiptStatus: "DELIVERED",
    })
  );
  await assertFails(
    bobDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      ciphertext: "dGFtcGVyZWQ=",
    })
  );
  await assertFails(
    aliceDb.collection("chats").doc(chatId).collection("messages").doc("msg_1").update({
      receiptStatus: "READ",
      readAt: pastDate(500),
    })
  );

  await assertFails(malloryDb.collection("chats").doc(chatId).get());
  await assertFails(malloryDb.collection("chats").where("participantUids", "array-contains", ALICE_UID).get());
});

test("Moments feed restricts reads to visibleToUids and supports locationTag & action-based likes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();
  const created = pastDate(8000);

  await assertSucceeds(
    aliceDb.collection("moments").doc("m_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_k",
      authorAvatarColor: "#0D9488",
      content: "Testing encrypted Kel mi Moments!",
      languageTag: "English",
      locationTag: "San Francisco · 21:30 PST",
      visibleToUids: [ALICE_UID, BOB_UID],
      likedByUids: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(bobDb.collection("moments").where("visibleToUids", "array-contains", BOB_UID).get());
  await assertSucceeds(
    bobDb.collection("moments").doc("m_1").update({
      likedByUids: [BOB_UID],
      updatedAt: pastDate(1000),
    })
  );

  await assertFails(malloryDb.collection("moments").doc("m_1").get());
});

test("Voice & Video Call signaling restricts access to chat participants and validates transitions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();
  const chatId = `${ALICE_UID}__${BOB_UID}`;
  const created = pastDate(8000);

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).set({
      participantUids: [ALICE_UID, BOB_UID],
      createdBy: ALICE_UID,
      lastSenderId: ALICE_UID,
      lastMessagePreviewTag: "Signal session ready",
      lastMessageAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc(chatId).collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      calleeId: BOB_UID,
      memberUids: [ALICE_UID, BOB_UID],
      callType: "VIDEO",
      status: "RINGING",
      e2eeFingerprint: "8392019283746510",
      durationSeconds: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    bobDb.collection("chats").doc(chatId).collection("calls").doc("call_1").update({
      status: "ENDED",
      durationSeconds: 45,
      updatedAt: pastDate(1000),
    })
  );

  await assertFails(malloryDb.collection("chats").doc(chatId).collection("calls").doc("call_1").get());
});

test("Reels feed allows posting photos/videos with in-app songs and isolates non-visible users", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();
  const created = pastDate(8000);

  await assertSucceeds(
    aliceDb.collection("reels").doc("reel_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_k",
      authorAvatarColor: "#0D9488",
      caption: "Sunset video from Shanghai!",
      mediaType: "VIDEO",
      mediaBase64: "dGVzdF90aHVtYg==",
      localMediaUri: "content://media/external/video/media/42",
      durationSeconds: 18,
      songId: "song_guqin_lotus",
      songTitle: "Guqin Lotus Breeze",
      songArtist: "Kel mi Studio",
      languageTag: "English",
      locationTag: "Shanghai · 21:30 CST",
      visibility: "CONTACTS",
      visibleToUids: [ALICE_UID, BOB_UID],
      likedByUids: [],
      bookmarkedByUids: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(bobDb.collection("reels").where("visibleToUids", "array-contains", BOB_UID).get());
  await assertSucceeds(
    bobDb.collection("reels").doc("reel_1").update({
      likedByUids: [BOB_UID],
      bookmarkedByUids: [BOB_UID],
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    bobDb.collection("reels").doc("reel_1").collection("comments").doc("c_1").set({
      authorId: BOB_UID,
      authorName: "Bob",
      authorHandle: "bob_k",
      authorAvatarColor: "#0D9488",
      text: "Amazing view!",
      createdAt: pastDate(500),
    })
  );
  await assertFails(malloryDb.collection("reels").doc("reel_1").get());
  await assertFails(malloryDb.collection("reels").doc("reel_1").collection("comments").get());

  await assertSucceeds(
    aliceDb.collection("reels").doc("reel_public").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_k",
      authorAvatarColor: "#0D9488",
      caption: "Public Kel mi Reel!",
      mediaType: "IMAGE",
      mediaBase64: "dGVzdF9pbWc=",
      durationSeconds: 0,
      songId: "song_shanghai_neon",
      songTitle: "Shanghai Neon Pulse",
      songArtist: "Kel mi Studio",
      languageTag: "Chinese (Simplified)",
      locationTag: "Beijing · 13:30 CST",
      filterName: "Emerald Film",
      visibility: "PUBLIC",
      visibleToUids: [ALICE_UID],
      likedByUids: [],
      bookmarkedByUids: [],
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(malloryDb.collection("reels").where("visibility", "==", "PUBLIC").get());

  await assertSucceeds(
    aliceDb.collection("stories").doc("story_1").set({
      authorId: ALICE_UID,
      authorName: "Alice",
      authorHandle: "alice_k",
      authorAvatarColor: "#0D9488",
      caption: "Morning matcha in Tokyo",
      songTitle: "Kyoto Bamboo Dawn",
      locationTag: "Tokyo, JP",
      visibility: "PUBLIC",
      visibleToUids: [ALICE_UID],
      viewedByUids: [],
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    bobDb.collection("stories").doc("story_1").update({
      viewedByUids: [BOB_UID],
      updatedAt: pastDate(500),
    })
  );
});

test("OnlineUsers discovery allows signed-in discovery with limit <= 50 and blocks cross-user writes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(8000);

  await assertSucceeds(
    aliceDb.collection("onlineUsers").doc(ALICE_UID).set({
      userId: ALICE_UID,
      kelMiHandle: "alice_k",
      qqNumber: "10482910",
      displayName: "Alice",
      avatarColorHex: "#0D9488",
      preferredLanguage: "Chinese (Simplified)",
      gender: "Female",
      ageYears: 28,
      interests: ["Music", "Travel"],
      locationLabel: "Shanghai, CN",
      timeZoneId: "Asia/Shanghai",
      onlineStatus: "ONLINE",
      discoverable: true,
      lastActiveAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(bobDb.collection("onlineUsers").where("discoverable", "==", true).limit(50).get());
  await assertFails(bobDb.collection("onlineUsers").get());
  await assertFails(bobDb.collection("onlineUsers").doc(ALICE_UID).update({ onlineStatus: "AWAY", updatedAt: pastDate(1000) }));
});

test("Vetny context-aware messages and generated AI photo cards are owner-isolated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("vetnyMessages").doc("v_1").set({
      userId: ALICE_UID,
      senderRole: "USER",
      originalText: "Book a 3-night boutique hotel in Tokyo for 2 guests",
      targetLanguage: "English",
      messageType: "TEXT",
      agentMode: "BOOKING",
      modelUsed: "gemini-3.8-flash",
      createdAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("vetnyMessages").doc("v_2").set({
      userId: ALICE_UID,
      senderRole: "VETNY",
      originalText: "Rooftop neon skyline photo",
      translatedText: "Generated with Nano Banana 2.1",
      sourceLanguage: "Auto",
      targetLanguage: "English",
      messageType: "IMAGE",
      agentMode: "IMAGE",
      modelUsed: "gemini-nano-banana-2.1",
      mediaBase64: "dGVzdF9haV9pbWFnZQ==",
      createdAt: pastDate(2000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("vetnyMessages").doc("v_3").set({
      userId: ALICE_UID,
      senderRole: "VETNY",
      originalText: "Best ramen near Shibuya",
      translatedText: "Here are top-rated ramen spots near Shibuya Station.",
      sourceLanguage: "Auto",
      targetLanguage: "English",
      messageType: "TEXT",
      agentMode: "MAPS",
      modelUsed: "gemini-4-hydrogen",
      groundingJson: "{\"maps\":[{\"title\":\"Ichiran Shibuya\",\"uri\":\"https://maps.google.com/?cid=123\"}]}",
      createdAt: pastDate(1500),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("vetnyMessages").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("vetnyMessages").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("vetnyMessages").doc("v_1").delete());
});
