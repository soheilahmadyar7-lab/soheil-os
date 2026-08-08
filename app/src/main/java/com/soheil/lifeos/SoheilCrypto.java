package com.soheil.lifeos;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.StrongBoxUnavailableException;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Central cryptographic boundary for SOHEIL. */
public final class SoheilCrypto {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEK_ALIAS = "soheil_vault_kek_v1";
    private static final String PREFS = "soheil_crypto_meta";
    private static final String WRAPPED_DEK = "wrapped_dek_v1";
    private static final String LEGACY_PREFIX = "S2.";
    private static final String AAD_PREFIX = "S3.";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SharedPreferences meta;
    private final SecureRandom random = new SecureRandom();
    private byte[] sessionDek;
    private boolean strongBoxRequested;

    public SoheilCrypto(Context context) {
        Context app = context.getApplicationContext();
        this.meta = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureKek();
    }

    private void ensureKek() {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE); ks.load(null);
            if (ks.containsAlias(KEK_ALIAS)) return;
            try { generateKek(true); strongBoxRequested = true; }
            catch (StrongBoxUnavailableException unavailable) { generateKek(false); strongBoxRequested = false; }
            catch (Exception first) { generateKek(false); strongBoxRequested = false; }
        } catch (Exception e) { throw new SecurityException("SOHEIL Keystore initialization failed", e); }
    }

    private void generateKek(boolean strongBox) throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(KEK_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            b.setUserAuthenticationParameters(30, KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL);
        } else b.setUserAuthenticationValidityDurationSeconds(30);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            b.setUnlockedDeviceRequired(true);
            if (strongBox) b.setIsStrongBoxBacked(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) b.setInvalidatedByBiometricEnrollment(false);
        kg.init(b.build()); kg.generateKey();
    }

    /** Call immediately after successful biometric/device authentication. */
    public synchronized void unlockVault() {
        try {
            SecretKey kek = getKek(); String wrapped = meta.getString(WRAPPED_DEK, null);
            if (wrapped == null) {
                byte[] dek = new byte[32]; random.nextBytes(dek);
                String encoded = wrapDek(kek, dek);
                if (!meta.edit().putString(WRAPPED_DEK, encoded).commit()) {
                    Arrays.fill(dek, (byte)0); throw new SecurityException("Unable to persist wrapped vault key");
                }
                sessionDek = dek;
            } else sessionDek = unwrapDek(kek, wrapped);
        } catch (Exception e) { lockVault(); throw new SecurityException("SOHEIL vault unlock failed", e); }
    }

    public synchronized void lockVault() { if(sessionDek!=null){Arrays.fill(sessionDek,(byte)0);sessionDek=null;} }
    public synchronized boolean isUnlocked(){return sessionDek!=null;}

    private SecretKey getKek() throws Exception {
        KeyStore ks=KeyStore.getInstance(KEYSTORE);ks.load(null);
        KeyStore.SecretKeyEntry entry=(KeyStore.SecretKeyEntry)ks.getEntry(KEK_ALIAS,null);
        if(entry==null)throw new SecurityException("Vault KEK missing");return entry.getSecretKey();
    }

    private String wrapDek(SecretKey kek,byte[] dek)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,kek);byte[] ct=c.doFinal(dek);return b64(c.getIV())+":"+b64(ct);}
    private byte[] unwrapDek(SecretKey kek,String wrapped)throws Exception{String[]p=wrapped.split(":",-1);if(p.length!=2)throw new SecurityException("Invalid wrapped vault key");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,kek,new GCMParameterSpec(GCM_TAG_BITS,fromB64(p[0])));byte[]dek=c.doFinal(fromB64(p[1]));if(dek.length!=32){Arrays.fill(dek,(byte)0);throw new SecurityException("Invalid vault key length");}return dek;}

    /** Generic encrypted scalar. Prefer encryptFor() for persisted structured records. */
    public synchronized String encrypt(String plaintext){return encryptFor("generic",plaintext);}
    public synchronized String decrypt(String value){return decryptFor("generic",value);}

    /** Encrypt and cryptographically bind ciphertext to its storage context. */
    public synchronized String encryptFor(String aad,String plaintext){
        requireUnlocked();if(plaintext==null)plaintext="";if(aad==null)aad="";
        try{byte[]iv=new byte[IV_BYTES];random.nextBytes(iv);SecretKeySpec key=new SecretKeySpec(sessionDek,"AES");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(GCM_TAG_BITS,iv));c.updateAAD(aad.getBytes(StandardCharsets.UTF_8));byte[]pt=plaintext.getBytes(StandardCharsets.UTF_8);byte[]ct=c.doFinal(pt);Arrays.fill(pt,(byte)0);return AAD_PREFIX+b64(iv)+"."+b64(ct);}catch(Exception e){throw new SecurityException("Vault encryption failed",e);}
    }

    /** Decrypt with context binding. Legacy S2 envelopes are accepted only for one-time migration. */
    public synchronized String decryptFor(String aad,String value){
        requireUnlocked();if(value==null)return"";if(!isEncrypted(value))return value;
        try{
            boolean legacy=value.startsWith(LEGACY_PREFIX);String prefix=legacy?LEGACY_PREFIX:AAD_PREFIX;
            String[]p=value.substring(prefix.length()).split("\\.",-1);if(p.length!=2)throw new SecurityException("Invalid ciphertext envelope");
            SecretKeySpec key=new SecretKeySpec(sessionDek,"AES");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(GCM_TAG_BITS,fromB64(p[0])));
            if(!legacy)c.updateAAD((aad==null?"":aad).getBytes(StandardCharsets.UTF_8));
            byte[]pt=c.doFinal(fromB64(p[1]));String out=new String(pt,StandardCharsets.UTF_8);Arrays.fill(pt,(byte)0);return out;
        }catch(Exception e){throw new SecurityException("Vault integrity/authentication check failed",e);}
    }

    public boolean isEncrypted(String value){return value!=null&&(value.startsWith(LEGACY_PREFIX)||value.startsWith(AAD_PREFIX));}
    public boolean isLegacyEncrypted(String value){return value!=null&&value.startsWith(LEGACY_PREFIX);}
    private void requireUnlocked(){if(sessionDek==null)throw new SecurityException("SOHEIL vault is locked");}

    public boolean isHardwareBacked(){try{SecretKey key=getKek();SecretKeyFactory factory=SecretKeyFactory.getInstance(key.getAlgorithm(),KEYSTORE);KeyInfo info=(KeyInfo)factory.getKeySpec(key,KeyInfo.class);return info.isInsideSecureHardware();}catch(Exception ignored){return false;}}
    public boolean strongBoxWasRequested(){return strongBoxRequested;}
    public String securitySummary(){return"AES-256-GCM/AAD • Vault key wrapped by Android Keystore • "+(isHardwareBacked()?"hardware-backed":"Keystore protected");}

    private static String b64(byte[]b){return Base64.encodeToString(b,Base64.NO_WRAP|Base64.NO_PADDING);}
    private static byte[]fromB64(String s){return Base64.decode(s,Base64.NO_WRAP|Base64.NO_PADDING);}
}
