package com.example.security

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Signal Protocol-compliant End-to-End Encryption Engine for Kel mi.
 *
 * Implements:
 * 1. Identity Key Pair (IK), Signed PreKey (SPK + ECDSA signature), and One-Time PreKeys (OPKs)
 *    using NIST P-256 (secp256r1) ECDH/ECDSA (universally supported on Android API 24+ without native JNI).
 * 2. X3DH (Extended Triple Diffie-Hellman) key agreement:
 *    SK = HKDF-SHA256(DH(IK_A, SPK_B) || DH(EK_A, IK_B) || DH(EK_A, SPK_B) || DH(EK_A, OPK_B))
 * 3. Double Ratchet Algorithm:
 *    - Asymmetric DH Ratchet steps on each peer ratchet key change (break-in recovery).
 *    - Symmetric-key HMAC-SHA256 KDF chains for every message (forward secrecy).
 *    - Bounded skipped-message-key cache for out-of-order message decryption.
 * 4. Authenticated Encryption (AES-256-GCM) with Associated Data binding sender, recipient,
 *    ratchet key, and message counter.
 */
class SignalProtocolManager(context: Context, private val ownerUid: String) {
    private val vault = KeyStoreCryptoBox(context, ownerUid)
    private val secureRandom = SecureRandom()

    data class PublicKeyRegistrationBundle(
        val identityPublicKey: String,
        val signedPreKeyId: Int,
        val signedPreKeyPublic: String,
        val signedPreKeySignature: String,
        val safetyFingerprint: String,
        val oneTimePreKeys: List<OpkPublicRecord>
    )

    data class OpkPublicRecord(
        val keyId: Int,
        val publicKey: String
    )

    data class PeerPreKeyBundle(
        val userId: String,
        val identityPublicKey: String,
        val signedPreKeyId: Int,
        val signedPreKeyPublic: String,
        val signedPreKeySignature: String,
        val oneTimePreKeyId: Int?,
        val oneTimePreKeyPublic: String?
    )

    data class EncryptedEnvelope(
        val ciphertextBase64: String,
        val ivBase64: String,
        val senderIdentityKey: String,
        val ephemeralKey: String?,
        val ratchetKey: String,
        val usedOpkId: String?,
        val messageNumber: Int,
        val previousChainLength: Int,
        val preKeyMessage: Boolean
    )

    data class LocalKeyDiagnostics(
        val safetyFingerprint: String,
        val signedPreKeyId: Int,
        val localOpkCount: Int,
        val activeSessionCount: Int
    )

    private data class RatchetSession(
        var rootKey: ByteArray,
        var dhSendPrivate: ByteArray,
        var dhSendPublic: ByteArray,
        var dhReceivePublic: ByteArray?,
        var sendingChainKey: ByteArray?,
        var receivingChainKey: ByteArray?,
        var sendCount: Int,
        var receiveCount: Int,
        var previousChainLength: Int,
        var peerIdentityKey: String,
        var pendingEphemeralPublic: String?,
        var pendingUsedOpkId: String?,
        var pendingPreKeyHeader: Boolean,
        val skippedKeys: MutableMap<String, ByteArray> = mutableMapOf()
    )

    @Synchronized
    fun ensureKeysInitialized(initialOpkTarget: Int = 15): PublicKeyRegistrationBundle {
        val ikJson = vault.getEncryptedString(KEY_IDENTITY_PAIR)
        val ikPair = if (ikJson == null) {
            generateEcKeyPair().also { saveKeyPair(KEY_IDENTITY_PAIR, it) }
        } else {
            loadKeyPair(ikJson)
        }

        val spkId = vault.getEncryptedString(KEY_SPK_ID)?.toIntOrNull() ?: 1
        val spkJson = vault.getEncryptedString(KEY_SPK_PAIR)
        val spkPair = if (spkJson == null) {
            generateEcKeyPair().also {
                saveKeyPair(KEY_SPK_PAIR, it)
                vault.putEncryptedString(KEY_SPK_ID, spkId.toString())
            }
        } else {
            loadKeyPair(spkJson)
        }

        val spkSignature = signBytes(ikPair.private, spkPair.public.encoded)
        val opks = ensureOpkPool(initialOpkTarget)
        val ikBase64 = encodeBase64(ikPair.public.encoded)

        return PublicKeyRegistrationBundle(
            identityPublicKey = ikBase64,
            signedPreKeyId = spkId,
            signedPreKeyPublic = encodeBase64(spkPair.public.encoded),
            signedPreKeySignature = encodeBase64(spkSignature),
            safetyFingerprint = computeIdentityFingerprint(ikPair.public.encoded),
            oneTimePreKeys = opks
        )
    }

    @Synchronized
    fun rotateSignedPreKey(): PublicKeyRegistrationBundle {
        val ikPair = loadKeyPair(requireNotNull(vault.getEncryptedString(KEY_IDENTITY_PAIR)))
        val nextSpkId = (vault.getEncryptedString(KEY_SPK_ID)?.toIntOrNull() ?: 1) + 1
        val newSpk = generateEcKeyPair()
        saveKeyPair(KEY_SPK_PAIR, newSpk)
        vault.putEncryptedString(KEY_SPK_ID, nextSpkId.toString())
        val sig = signBytes(ikPair.private, newSpk.public.encoded)
        val opks = getStoredOpkPublicList()
        return PublicKeyRegistrationBundle(
            identityPublicKey = encodeBase64(ikPair.public.encoded),
            signedPreKeyId = nextSpkId,
            signedPreKeyPublic = encodeBase64(newSpk.public.encoded),
            signedPreKeySignature = encodeBase64(sig),
            safetyFingerprint = computeIdentityFingerprint(ikPair.public.encoded),
            oneTimePreKeys = opks
        )
    }

    @Synchronized
    fun replenishOneTimePreKeys(targetPoolSize: Int = 15): List<OpkPublicRecord> {
        return ensureOpkPool(targetPoolSize)
    }

    @Synchronized
    fun getDiagnostics(): LocalKeyDiagnostics {
        val bundle = ensureKeysInitialized()
        val sessionsRaw = vault.getEncryptedString(KEY_SESSION_INDEX)
        val sessionCount = if (sessionsRaw.isNullOrBlank()) 0 else JSONArray(sessionsRaw).length()
        return LocalKeyDiagnostics(
            safetyFingerprint = bundle.safetyFingerprint,
            signedPreKeyId = bundle.signedPreKeyId,
            localOpkCount = bundle.oneTimePreKeys.size,
            activeSessionCount = sessionCount
        )
    }

    fun getMyIdentityPublicKey(): String {
        return ensureKeysInitialized().identityPublicKey
    }

    /**
     * Encrypts plaintext for [peerUid] in [chatId] using an existing Double Ratchet session
     * or by performing X3DH with [peerBundleProvider] to establish a fresh session.
     */
    @Synchronized
    fun encryptMessage(
        chatId: String,
        peerUid: String,
        plaintext: String,
        peerBundleProvider: () -> PeerPreKeyBundle?
    ): EncryptedEnvelope {
        val sessionSlot = if (peerUid == ownerUid) "tx_$chatId" else chatId
        val ikPair = loadKeyPair(requireNotNull(vault.getEncryptedString(KEY_IDENTITY_PAIR)))
        var session = loadSession(sessionSlot)

        if (session == null) {
            val bundle = peerBundleProvider()
                ?: throw IllegalStateException("Recipient Signal PreKey bundle unavailable.")
            session = initiateX3dhSession(ikPair, bundle)
            saveSession(sessionSlot, session)
        }

        // Advance Symmetric Sending Chain
        val sendChain = session.sendingChainKey
            ?: throw IllegalStateException("Sending chain uninitialized")
        val (nextChainKey, messageKey) = kdfChainKey(sendChain)
        session.sendingChainKey = nextChainKey

        val msgNum = session.sendCount
        session.sendCount = msgNum + 1

        val ratchetPubBase64 = encodeBase64(session.dhSendPublic)
        val myIkBase64 = encodeBase64(ikPair.public.encoded)
        val associatedData = "$ownerUid|$peerUid|$ratchetPubBase64|$msgNum".toByteArray(Charsets.UTF_8)

        val iv = ByteArray(12).apply { secureRandom.nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(messageKey, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val isPreKey = session.pendingPreKeyHeader && session.receivingChainKey == null
        val ephemeral = session.pendingEphemeralPublic
        val usedOpk = session.pendingUsedOpkId
        saveSession(sessionSlot, session)

        return EncryptedEnvelope(
            ciphertextBase64 = encodeBase64(ciphertext),
            ivBase64 = encodeBase64(iv),
            senderIdentityKey = myIkBase64,
            ephemeralKey = if (isPreKey) ephemeral else null,
            ratchetKey = ratchetPubBase64,
            usedOpkId = if (isPreKey) usedOpk else null,
            messageNumber = msgNum,
            previousChainLength = session.previousChainLength,
            preKeyMessage = isPreKey
        )
    }

    /**
     * Decrypts an incoming Signal Protocol message envelope.
     * Supports X3DH PreKey initialization, Double Ratchet DH steps, and out-of-order skipped keys.
     */
    @Synchronized
    fun decryptMessage(
        chatId: String,
        senderUid: String,
        recipientUid: String,
        envelope: EncryptedEnvelope
    ): String {
        val sessionSlot = if (senderUid == ownerUid && recipientUid == ownerUid) "rx_$chatId" else chatId
        var session = loadSession(sessionSlot)

        if (session == null) {
            if (!envelope.preKeyMessage || envelope.ephemeralKey.isNullOrBlank()) {
                throw IllegalStateException("No Signal session found and message is not an X3DH PreKey message.")
            }
            session = respondX3dhSession(envelope)
            saveSession(sessionSlot, session)
        }

        val skippedId = "${envelope.ratchetKey}:${envelope.messageNumber}"
        val cachedSkippedKey = session.skippedKeys.remove(skippedId)
        val messageKey: ByteArray = if (cachedSkippedKey != null) {
            cachedSkippedKey
        } else {
            val incomingRatchetBytes = decodeBase64(envelope.ratchetKey)
            val currentRecvRatchet = session.dhReceivePublic
            if (currentRecvRatchet == null || !currentRecvRatchet.contentEquals(incomingRatchetBytes)) {
                // Skip any remaining messages in the current receiving chain
                skipMessageKeys(session, envelope.previousChainLength)
                // Perform Double Ratchet DH Step
                performDhRatchetStep(session, incomingRatchetBytes)
            }
            skipMessageKeys(session, envelope.messageNumber)
            val recvChain = session.receivingChainKey
                ?: throw IllegalStateException("Receiving chain unavailable after DH ratchet.")
            val (nextRecvChain, mk) = kdfChainKey(recvChain)
            session.receivingChainKey = nextRecvChain
            session.receiveCount = envelope.messageNumber + 1
            mk
        }

        val associatedData = "$senderUid|$recipientUid|${envelope.ratchetKey}|${envelope.messageNumber}"
            .toByteArray(Charsets.UTF_8)
        val iv = decodeBase64(envelope.ivBase64)
        val ciphertext = decodeBase64(envelope.ciphertextBase64)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(messageKey, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData)
        val plaintextBytes = cipher.doFinal(ciphertext)

        saveSession(sessionSlot, session)
        return String(plaintextBytes, Charsets.UTF_8)
    }

    private fun initiateX3dhSession(aliceIk: KeyPair, bobBundle: PeerPreKeyBundle): RatchetSession {
        val bobIkPub = decodeEcPublicKey(decodeBase64(bobBundle.identityPublicKey))
        val bobSpkPub = decodeEcPublicKey(decodeBase64(bobBundle.signedPreKeyPublic))
        val bobSpkSig = decodeBase64(bobBundle.signedPreKeySignature)

        require(verifySignature(bobIkPub, bobSpkPub.encoded, bobSpkSig)) {
            "Peer Signed PreKey signature verification failed! Possible MITM."
        }

        val aliceEk = generateEcKeyPair()
        val dh1 = ecdh(aliceIk.private, bobSpkPub)
        val dh2 = ecdh(aliceEk.private, bobIkPub)
        val dh3 = ecdh(aliceEk.private, bobSpkPub)
        val dh4 = if (!bobBundle.oneTimePreKeyPublic.isNullOrBlank()) {
            ecdh(aliceEk.private, decodeEcPublicKey(decodeBase64(bobBundle.oneTimePreKeyPublic)))
        } else {
            ByteArray(0)
        }

        val masterSecret = hkdfSha256(
            ikm = dh1 + dh2 + dh3 + dh4,
            salt = ByteArray(32),
            info = X3DH_INFO.toByteArray(Charsets.UTF_8),
            length = 64
        )
        val initialRootKey = masterSecret.copyOfRange(0, 32)
        val initialDhRatchet = generateEcKeyPair()
        val dhRatchetOut = ecdh(initialDhRatchet.private, bobSpkPub)
        val (nextRootKey, sendingChainKey) = kdfRootKey(initialRootKey, dhRatchetOut)

        return RatchetSession(
            rootKey = nextRootKey,
            dhSendPrivate = initialDhRatchet.private.encoded,
            dhSendPublic = initialDhRatchet.public.encoded,
            dhReceivePublic = bobSpkPub.encoded,
            sendingChainKey = sendingChainKey,
            receivingChainKey = null,
            sendCount = 0,
            receiveCount = 0,
            previousChainLength = 0,
            peerIdentityKey = bobBundle.identityPublicKey,
            pendingEphemeralPublic = encodeBase64(aliceEk.public.encoded),
            pendingUsedOpkId = bobBundle.oneTimePreKeyId?.toString(),
            pendingPreKeyHeader = true
        )
    }

    private fun respondX3dhSession(envelope: EncryptedEnvelope): RatchetSession {
        val bobIk = loadKeyPair(requireNotNull(vault.getEncryptedString(KEY_IDENTITY_PAIR)))
        val bobSpk = loadKeyPair(requireNotNull(vault.getEncryptedString(KEY_SPK_PAIR)))

        val aliceIkPub = decodeEcPublicKey(decodeBase64(envelope.senderIdentityKey))
        val aliceEkPub = decodeEcPublicKey(decodeBase64(requireNotNull(envelope.ephemeralKey)))

        val opkId = envelope.usedOpkId?.toIntOrNull()
        val opkPair = if (opkId != null) consumeLocalOpk(opkId) else null

        val dh1 = ecdh(bobSpk.private, aliceIkPub)
        val dh2 = ecdh(bobIk.private, aliceEkPub)
        val dh3 = ecdh(bobSpk.private, aliceEkPub)
        val dh4 = if (opkPair != null) ecdh(opkPair.private, aliceEkPub) else ByteArray(0)

        val masterSecret = hkdfSha256(
            ikm = dh1 + dh2 + dh3 + dh4,
            salt = ByteArray(32),
            info = X3DH_INFO.toByteArray(Charsets.UTF_8),
            length = 64
        )
        val initialRootKey = masterSecret.copyOfRange(0, 32)

        return RatchetSession(
            rootKey = initialRootKey,
            dhSendPrivate = bobSpk.private.encoded,
            dhSendPublic = bobSpk.public.encoded,
            dhReceivePublic = null,
            sendingChainKey = null,
            receivingChainKey = null,
            sendCount = 0,
            receiveCount = 0,
            previousChainLength = 0,
            peerIdentityKey = envelope.senderIdentityKey,
            pendingEphemeralPublic = null,
            pendingUsedOpkId = null,
            pendingPreKeyHeader = false
        )
    }

    private fun performDhRatchetStep(session: RatchetSession, incomingRatchetBytes: ByteArray) {
        session.previousChainLength = session.sendCount
        session.sendCount = 0
        session.receiveCount = 0
        session.dhReceivePublic = incomingRatchetBytes

        val currentDhPriv = decodeEcPrivateKey(session.dhSendPrivate)
        val incomingDhPub = decodeEcPublicKey(incomingRatchetBytes)

        val dhRecv = ecdh(currentDhPriv, incomingDhPub)
        val (rootAfterRecv, recvChain) = kdfRootKey(session.rootKey, dhRecv)
        session.rootKey = rootAfterRecv
        session.receivingChainKey = recvChain

        val newDhSend = generateEcKeyPair()
        session.dhSendPrivate = newDhSend.private.encoded
        session.dhSendPublic = newDhSend.public.encoded

        val dhSend = ecdh(newDhSend.private, incomingDhPub)
        val (rootAfterSend, sendChain) = kdfRootKey(session.rootKey, dhSend)
        session.rootKey = rootAfterSend
        session.sendingChainKey = sendChain
    }

    private fun skipMessageKeys(session: RatchetSession, untilIndex: Int) {
        val recvKey = session.receivingChainKey ?: return
        val ratchetPub = session.dhReceivePublic ?: return
        val ratchetKeyB64 = encodeBase64(ratchetPub)
        if (session.receiveCount + MAX_SKIP < untilIndex) {
            throw IllegalStateException("Too many skipped Signal messages ($untilIndex)")
        }
        var chain = recvKey
        while (session.receiveCount < untilIndex) {
            val (nextChain, mk) = kdfChainKey(chain)
            chain = nextChain
            session.skippedKeys["$ratchetKeyB64:${session.receiveCount}"] = mk
            if (session.skippedKeys.size > MAX_SKIP) {
                val oldest = session.skippedKeys.keys.firstOrNull()
                if (oldest != null) session.skippedKeys.remove(oldest)
            }
            session.receiveCount++
        }
        session.receivingChainKey = chain
    }

    private fun ensureOpkPool(targetCount: Int): List<OpkPublicRecord> {
        val currentMap = loadOpkMap().toMutableMap()
        var nextId = vault.getEncryptedString(KEY_NEXT_OPK_ID)?.toIntOrNull() ?: 1
        while (currentMap.size < targetCount) {
            val kp = generateEcKeyPair()
            currentMap[nextId] = serializeKeyPair(kp)
            nextId++
        }
        vault.putEncryptedString(KEY_NEXT_OPK_ID, nextId.toString())
        saveOpkMap(currentMap)
        return currentMap.entries.sortedBy { it.key }.map { (id, json) ->
            OpkPublicRecord(keyId = id, publicKey = json.getString("pub"))
        }
    }

    private fun getStoredOpkPublicList(): List<OpkPublicRecord> {
        return loadOpkMap().entries.sortedBy { it.key }.map { (id, json) ->
            OpkPublicRecord(keyId = id, publicKey = json.getString("pub"))
        }
    }

    private fun consumeLocalOpk(opkId: Int): KeyPair? {
        val currentMap = loadOpkMap().toMutableMap()
        val entry = currentMap.remove(opkId) ?: return null
        saveOpkMap(currentMap)
        return loadKeyPair(entry.toString())
    }

    private fun loadOpkMap(): Map<Int, JSONObject> {
        val raw = vault.getEncryptedString(KEY_OPK_MAP) ?: return emptyMap()
        val obj = JSONObject(raw)
        val out = mutableMapOf<Int, JSONObject>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val id = k.toIntOrNull() ?: continue
            out[id] = obj.getJSONObject(k)
        }
        return out
    }

    private fun saveOpkMap(map: Map<Int, JSONObject>) {
        val root = JSONObject()
        map.forEach { (id, kpJson) -> root.put(id.toString(), kpJson) }
        vault.putEncryptedString(KEY_OPK_MAP, root.toString())
    }

    private fun saveSession(slot: String, session: RatchetSession) {
        val json = JSONObject().apply {
            put("rootKey", encodeBase64(session.rootKey))
            put("dhSendPrivate", encodeBase64(session.dhSendPrivate))
            put("dhSendPublic", encodeBase64(session.dhSendPublic))
            put("dhReceivePublic", session.dhReceivePublic?.let { encodeBase64(it) })
            put("sendingChainKey", session.sendingChainKey?.let { encodeBase64(it) })
            put("receivingChainKey", session.receivingChainKey?.let { encodeBase64(it) })
            put("sendCount", session.sendCount)
            put("receiveCount", session.receiveCount)
            put("previousChainLength", session.previousChainLength)
            put("peerIdentityKey", session.peerIdentityKey)
            put("pendingEphemeralPublic", session.pendingEphemeralPublic)
            put("pendingUsedOpkId", session.pendingUsedOpkId)
            put("pendingPreKeyHeader", session.pendingPreKeyHeader)
            val skippedObj = JSONObject()
            session.skippedKeys.forEach { (k, v) -> skippedObj.put(k, encodeBase64(v)) }
            put("skippedKeys", skippedObj)
        }
        vault.putEncryptedString("$KEY_SESSION_PREFIX$slot", json.toString())

        val idxRaw = vault.getEncryptedString(KEY_SESSION_INDEX)
        val idxArr = if (idxRaw.isNullOrBlank()) JSONArray() else JSONArray(idxRaw)
        var exists = false
        for (i in 0 until idxArr.length()) {
            if (idxArr.getString(i) == slot) {
                exists = true
                break
            }
        }
        if (!exists) {
            idxArr.put(slot)
            vault.putEncryptedString(KEY_SESSION_INDEX, idxArr.toString())
        }
    }

    private fun loadSession(slot: String): RatchetSession? {
        val raw = vault.getEncryptedString("$KEY_SESSION_PREFIX$slot") ?: return null
        return try {
            val json = JSONObject(raw)
            val skippedMap = mutableMapOf<String, ByteArray>()
            val skippedObj = json.optJSONObject("skippedKeys")
            if (skippedObj != null) {
                val keys = skippedObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    skippedMap[k] = decodeBase64(skippedObj.getString(k))
                }
            }
            RatchetSession(
                rootKey = decodeBase64(json.getString("rootKey")),
                dhSendPrivate = decodeBase64(json.getString("dhSendPrivate")),
                dhSendPublic = decodeBase64(json.getString("dhSendPublic")),
                dhReceivePublic = json.optString("dhReceivePublic").takeIf { it.isNotBlank() && it != "null" }?.let { decodeBase64(it) },
                sendingChainKey = json.optString("sendingChainKey").takeIf { it.isNotBlank() && it != "null" }?.let { decodeBase64(it) },
                receivingChainKey = json.optString("receivingChainKey").takeIf { it.isNotBlank() && it != "null" }?.let { decodeBase64(it) },
                sendCount = json.getInt("sendCount"),
                receiveCount = json.getInt("receiveCount"),
                previousChainLength = json.getInt("previousChainLength"),
                peerIdentityKey = json.getString("peerIdentityKey"),
                pendingEphemeralPublic = json.optString("pendingEphemeralPublic").takeIf { it.isNotBlank() && it != "null" },
                pendingUsedOpkId = json.optString("pendingUsedOpkId").takeIf { it.isNotBlank() && it != "null" },
                pendingPreKeyHeader = json.optBoolean("pendingPreKeyHeader", false),
                skippedKeys = skippedMap
            )
        } catch (_: Exception) {
            null
        }
    }

    // --- CRYPTOGRAPHIC PRIMITIVES ---

    private fun generateEcKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        return kpg.generateKeyPair()
    }

    private fun ecdh(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        return agreement.generateSecret()
    }

    private fun signBytes(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey, secureRandom)
        sig.update(data)
        return sig.sign()
    }

    private fun verifySignature(publicKey: PublicKey, data: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(data)
            sig.verify(signatureBytes)
        } catch (_: Exception) {
            false
        }
    }

    private fun kdfRootKey(rootKey: ByteArray, dhOut: ByteArray): Pair<ByteArray, ByteArray> {
        val derived = hkdfSha256(
            ikm = dhOut,
            salt = rootKey,
            info = RATCHET_ROOT_INFO.toByteArray(Charsets.UTF_8),
            length = 64
        )
        return Pair(derived.copyOfRange(0, 32), derived.copyOfRange(32, 64))
    }

    private fun kdfChainKey(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val messageKey = hmacSha256(chainKey, byteArrayOf(0x01))
        val nextChainKey = hmacSha256(chainKey, byteArrayOf(0x02))
        return Pair(nextChainKey, messageKey)
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = hmacSha256(salt, ikm)
        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            t = hmacSha256(prk, t + info + byteArrayOf(counter.toByte()))
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, copyLen)
            offset += copyLen
            counter++
        }
        return result
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun decodeEcPublicKey(encoded: ByteArray): PublicKey {
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
    }

    private fun decodeEcPrivateKey(encoded: ByteArray): PrivateKey {
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(encoded))
    }

    private fun saveKeyPair(key: String, kp: KeyPair) {
        vault.putEncryptedString(key, serializeKeyPair(kp).toString())
    }

    private fun serializeKeyPair(kp: KeyPair): JSONObject {
        return JSONObject().apply {
            put("pub", encodeBase64(kp.public.encoded))
            put("priv", encodeBase64(kp.private.encoded))
        }
    }

    private fun loadKeyPair(jsonString: String): KeyPair {
        val obj = JSONObject(jsonString)
        val pub = decodeEcPublicKey(decodeBase64(obj.getString("pub")))
        val priv = decodeEcPrivateKey(decodeBase64(obj.getString("priv")))
        return KeyPair(pub, priv)
    }

    private fun encodeBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decodeBase64(str: String): ByteArray = Base64.decode(str, Base64.NO_WRAP)

    companion object {
        private const val KEY_IDENTITY_PAIR = "ik_pair"
        private const val KEY_SPK_PAIR = "spk_pair"
        private const val KEY_SPK_ID = "spk_id"
        private const val KEY_OPK_MAP = "opk_map"
        private const val KEY_NEXT_OPK_ID = "next_opk_id"
        private const val KEY_SESSION_PREFIX = "session_"
        private const val KEY_SESSION_INDEX = "session_index"

        private const val X3DH_INFO = "KelMi_Signal_X3DH_v1"
        private const val RATCHET_ROOT_INFO = "KelMi_DoubleRatchet_Root_v1"
        private const val MAX_SKIP = 50

        fun computeIdentityFingerprint(publicKeyBytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
            return digest.take(12).joinToString("") { "%02X".format(it) }
                .chunked(4).joinToString(" ")
        }

        /**
         * Produces a symmetric 60-digit Signal-style Safety Number from two identity public keys
         * so both participants can verify zero-MITM in person or out-of-band.
         */
        fun computeSafetyNumber(identityPubA: String, identityPubB: String): String {
            val sorted = listOf(identityPubA, identityPubB).sorted()
            val md = MessageDigest.getInstance("SHA-256")
            var hash = (sorted[0] + "|" + sorted[1]).toByteArray(Charsets.UTF_8)
            repeat(5200) {
                hash = md.digest(hash)
            }
            val digits = StringBuilder()
            for (i in 0 until 12) {
                val idx = (i * 2) % hash.size
                val value = ((hash[idx].toInt() and 0xFF) shl 8) or (hash[(idx + 1) % hash.size].toInt() and 0xFF)
                digits.append("%05d".format(value % 100000))
                if (i < 11) digits.append(if ((i + 1) % 4 == 0) "\n" else " ")
            }
            return digits.toString()
        }
    }
}
