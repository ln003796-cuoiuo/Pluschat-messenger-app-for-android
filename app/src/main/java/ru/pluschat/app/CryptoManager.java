package ru.pluschat.app;

import android.content.Context;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Provider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Pluschat pc-e2ee-v1 client-side crypto.
 * Private identity and X25519 prekey material is kept in EncryptedSharedPreferences.
 * The API receives only raw public keys and authenticated ciphertext envelopes.
 */
public final class CryptoManager {
    private static final String VERSION = "pc-e2ee-v1";
    private static final Provider BC = new BouncyCastleProvider();
    private static final byte[] ED25519_X509_PREFIX = hex("302a300506032b6570032100");
    private static final byte[] X25519_X509_PREFIX = hex("302a300506032b656e032100");

    public static final class LocalKeys {
        public final String identityPublic;
        public final String signedPrekey;
        public final String signedPrekeySignature;
        public final String deviceHash;
        LocalKeys(String identityPublic, String signedPrekey, String signedPrekeySignature, String deviceHash) {
            this.identityPublic = identityPublic;
            this.signedPrekey = signedPrekey;
            this.signedPrekeySignature = signedPrekeySignature;
            this.deviceHash = deviceHash;
        }
        public JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("identity_public_key", identityPublic);
            o.put("signed_prekey", signedPrekey);
            o.put("signed_prekey_signature", signedPrekeySignature);
            return o;
        }
    }

    private CryptoManager() {}

    public static synchronized LocalKeys ensureLocalKeys(Context context) throws Exception {
        String identityPrivate = SessionStore.cryptoGet(context, "identity_private");
        String identityPublic = SessionStore.cryptoGet(context, "identity_public");
        String prekeyPrivate = SessionStore.cryptoGet(context, "signed_prekey_private");
        String prekeyPublic = SessionStore.cryptoGet(context, "signed_prekey_public");
        String signature = SessionStore.cryptoGet(context, "signed_prekey_signature");
        if (identityPrivate.isEmpty() || identityPublic.isEmpty() || prekeyPrivate.isEmpty()
                || prekeyPublic.isEmpty() || signature.isEmpty()) {
            KeyPairGenerator edGenerator = KeyPairGenerator.getInstance("Ed25519", BC);
            KeyPair identity = edGenerator.generateKeyPair();
            KeyPairGenerator xGenerator = KeyPairGenerator.getInstance("X25519", BC);
            KeyPair prekey = xGenerator.generateKeyPair();
            byte[] rawIdentity = rawPublic(identity.getPublic().getEncoded());
            byte[] rawPrekey = rawPublic(prekey.getPublic().getEncoded());
            Signature signer = Signature.getInstance("Ed25519", BC);
            signer.initSign(identity.getPrivate());
            signer.update(rawPrekey);
            byte[] sig = signer.sign();
            identityPrivate = b64(identity.getPrivate().getEncoded());
            identityPublic = b64(rawIdentity);
            prekeyPrivate = b64(prekey.getPrivate().getEncoded());
            prekeyPublic = b64(rawPrekey);
            signature = b64(sig);
            SessionStore.cryptoPut(context, "identity_private", identityPrivate);
            SessionStore.cryptoPut(context, "identity_public", identityPublic);
            SessionStore.cryptoPut(context, "signed_prekey_private", prekeyPrivate);
            SessionStore.cryptoPut(context, "signed_prekey_public", prekeyPublic);
            SessionStore.cryptoPut(context, "signed_prekey_signature", signature);
        }
        return new LocalKeys(identityPublic, prekeyPublic, signature, deviceHash(SessionStore.deviceKey(context)));
    }

    public static JSONObject encrypt(Context context, String conversationId, String plaintext,
                                     String recipientDeviceHash, String recipientSignedPrekey) throws Exception {
        LocalKeys local = ensureLocalKeys(context);
        String ownFull = local.deviceHash;
        String recipientFull = recipientDeviceHash.toLowerCase(java.util.Locale.ROOT);
        if (!ownFull.matches("[a-f0-9]{64}") || !recipientFull.matches("[a-f0-9]{64}") || ownFull.equals(recipientFull)) {
            throw new IllegalArgumentException("Invalid device key identifiers");
        }
        byte[] shared = sharedSecret(SessionStore.cryptoGet(context, "signed_prekey_private"), recipientSignedPrekey);
        byte[] key = deriveMessageKey(shared, conversationId, ownFull, recipientFull);
        byte[] nonce = new byte[12];
        new java.security.SecureRandom().nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad(conversationId, ownFull, recipientFull));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        JSONObject envelope = new JSONObject();
        envelope.put("v", VERSION);
        envelope.put("alg", "AES-256-GCM");
        envelope.put("nonce", b64(nonce));
        envelope.put("ciphertext", b64(ciphertext));
        // Compact identifiers keep the envelope inside the API's 128-character key_id limit.
        envelope.put("key_id", shortId(ownFull) + ":" + shortId(recipientFull));
        return envelope;
    }

    public static String decrypt(Context context, String conversationId, String envelopeText,
                                 String peerDeviceHash, String peerSignedPrekey) throws Exception {
        JSONObject envelope = new JSONObject(envelopeText);
        if (!VERSION.equals(envelope.optString("v")) || !"AES-256-GCM".equals(envelope.optString("alg"))) {
            throw new IllegalArgumentException("Unsupported encrypted message format");
        }
        String[] ids = envelope.optString("key_id").split(":", -1);
        if (ids.length != 2) throw new IllegalArgumentException("Invalid message key id");
        LocalKeys local = ensureLocalKeys(context);
        String ownFull = local.deviceHash;
        String peerFull = peerDeviceHash.toLowerCase(java.util.Locale.ROOT);
        if (!shortId(ownFull).equals(ids[0]) && !shortId(ownFull).equals(ids[1])) {
            throw new IllegalArgumentException("Message belongs to another device");
        }
        String expectedPeerShort = shortId(peerFull);
        String otherShort = shortId(ownFull).equals(ids[0]) ? ids[1] : ids[0];
        if (!expectedPeerShort.equals(otherShort)) throw new IllegalArgumentException("Peer key does not match message");
        byte[] shared = sharedSecret(SessionStore.cryptoGet(context, "signed_prekey_private"), peerSignedPrekey);
        byte[] key = deriveMessageKey(shared, conversationId, ownFull, peerFull);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, unb64(envelope.getString("nonce"))));
        cipher.updateAAD(aad(conversationId, ownFull, peerFull));
        byte[] plaintext = cipher.doFinal(unb64(envelope.getString("ciphertext")));
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    public static byte[] encryptFile(Context context, String conversationId, byte[] plaintext,
                                      String recipientDeviceHash, String recipientSignedPrekey) throws Exception {
        LocalKeys local = ensureLocalKeys(context);
        String ownFull = local.deviceHash;
        String recipientFull = recipientDeviceHash.toLowerCase(java.util.Locale.ROOT);
        byte[] shared = sharedSecret(SessionStore.cryptoGet(context, "signed_prekey_private"), recipientSignedPrekey);
        byte[] key = deriveMessageKey(shared, conversationId, ownFull, recipientFull);
        byte[] nonce = new byte[12];
        new java.security.SecureRandom().nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(fileAad(conversationId, ownFull, recipientFull));
        return concat(new byte[] { 0x50, 0x43, 0x46, 0x31 }, concat(nonce, cipher.doFinal(plaintext)));
    }

    public static byte[] decryptFile(Context context, String conversationId, byte[] encrypted,
                                     String peerDeviceHash, String peerSignedPrekey) throws Exception {
        if (encrypted == null || encrypted.length < 32 || encrypted[0] != 0x50 || encrypted[1] != 0x43 || encrypted[2] != 0x46 || encrypted[3] != 0x31) throw new IllegalArgumentException("Invalid encrypted attachment");
        LocalKeys local = ensureLocalKeys(context);
        String ownFull = local.deviceHash;
        String peerFull = peerDeviceHash.toLowerCase(java.util.Locale.ROOT);
        byte[] nonce = java.util.Arrays.copyOfRange(encrypted, 4, 16);
        byte[] ciphertext = java.util.Arrays.copyOfRange(encrypted, 16, encrypted.length);
        byte[] shared = sharedSecret(SessionStore.cryptoGet(context, "signed_prekey_private"), peerSignedPrekey);
        byte[] key = deriveMessageKey(shared, conversationId, ownFull, peerFull);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(fileAad(conversationId, ownFull, peerFull));
        return cipher.doFinal(ciphertext);
    }

    private static byte[] fileAad(String conversationId, String a, String b) {
        String first = a.compareTo(b) <= 0 ? a : b;
        String second = a.compareTo(b) <= 0 ? b : a;
        return ("Pluschat-file|" + VERSION + "|" + conversationId + "|" + first + "|" + second).getBytes(StandardCharsets.UTF_8);
    }

    public static boolean verifyPeerKey(String identityPublicKey, String signedPrekey, String signature) {
        try {
            byte[] identityRaw = unb64(identityPublicKey);
            byte[] prekeyRaw = unb64(signedPrekey);
            byte[] signatureRaw = unb64(signature);
            if (identityRaw.length != 32 || prekeyRaw.length != 32 || signatureRaw.length != 64) return false;
            PublicKey identity = KeyFactory.getInstance("Ed25519", BC).generatePublic(
                    new X509EncodedKeySpec(concat(ED25519_X509_PREFIX, identityRaw)));
            Signature verifier = Signature.getInstance("Ed25519", BC);
            verifier.initVerify(identity);
            verifier.update(prekeyRaw);
            return verifier.verify(signatureRaw);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean verifyAndPinPeerKey(Context context, String peerUserId, JSONObject key) {
        String identity = key.optString("identity_public_key", "");
        String prekey = key.optString("signed_prekey", "");
        String signature = key.optString("signed_prekey_signature", "");
        String deviceId = key.optString("key_id", "");
        if (!deviceId.matches("[a-f0-9]{64}") || !verifyPeerKey(identity, prekey, signature)) return false;
        String pinName = "peer_identity_" + peerUserId + "_" + deviceId;
        String pinned = SessionStore.cryptoGet(context, pinName);
        if (!pinned.isEmpty()) return MessageDigest.isEqual(pinned.getBytes(StandardCharsets.UTF_8), identity.getBytes(StandardCharsets.UTF_8));
        try {
            SessionStore.cryptoPut(context, pinName, identity);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String deviceHash(Context context) {
        return deviceHash(SessionStore.deviceKey(context));
    }

    public static String deviceHash(String deviceKey) {
        try { return toHex(MessageDigest.getInstance("SHA-256").digest(deviceKey.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static byte[] sharedSecret(String privateB64, String peerPublicB64) throws Exception {
        byte[] privateBytes = unb64(privateB64);
        byte[] peerRaw = unb64(peerPublicB64);
        if (peerRaw.length != 32) throw new IllegalArgumentException("Invalid peer prekey");
        PrivateKey privateKey = KeyFactory.getInstance("X25519", BC).generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
        PublicKey publicKey = KeyFactory.getInstance("X25519", BC).generatePublic(new X509EncodedKeySpec(concat(X25519_X509_PREFIX, peerRaw)));
        KeyAgreement agreement = KeyAgreement.getInstance("X25519", BC);
        agreement.init(privateKey);
        agreement.doPhase(publicKey, true);
        return agreement.generateSecret();
    }

    private static byte[] deriveMessageKey(byte[] shared, String conversationId, String a, String b) throws Exception {
        String first = a.compareTo(b) <= 0 ? a : b;
        String second = a.compareTo(b) <= 0 ? b : a;
        byte[] salt = MessageDigest.getInstance("SHA-256").digest(("Pluschat salt|" + conversationId).getBytes(StandardCharsets.UTF_8));
        byte[] prk = hmac(salt, shared);
        byte[] info = ("Pluschat " + VERSION + "|" + conversationId + "|" + first + "|" + second).getBytes(StandardCharsets.UTF_8);
        return hmac(prk, concat(info, new byte[] { 1 }));
    }

    private static byte[] aad(String conversationId, String a, String b) {
        String first = a.compareTo(b) <= 0 ? a : b;
        String second = a.compareTo(b) <= 0 ? b : a;
        return ("Pluschat|" + VERSION + "|" + conversationId + "|" + first + "|" + second).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] rawPublic(byte[] x509) {
        if (x509.length < 32) throw new IllegalArgumentException("Invalid public key");
        return java.util.Arrays.copyOfRange(x509, x509.length - 32, x509.length);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] hex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    private static String b64(byte[] bytes) { return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING); }
    private static byte[] unb64(String value) { return Base64.decode(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING); }
    private static String shortId(String full) { return full.substring(0, 32); }
    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }
}
