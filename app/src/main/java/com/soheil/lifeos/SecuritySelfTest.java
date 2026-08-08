package com.soheil.lifeos;

/** Fail-closed cryptographic sanity tests executed after every Vault unlock. */
public final class SecuritySelfTest {
    private SecuritySelfTest() {}

    public static void run(SoheilCrypto crypto) {
        String aad="selftest:record";
        String plain="SOHEIL-CRYPTO-SELFTEST-v1";
        String ct=crypto.encryptFor(aad,plain);
        if(!plain.equals(crypto.decryptFor(aad,ct)))throw new SecurityException("Crypto round-trip self-test failed");

        boolean wrongAadRejected=false;
        try{crypto.decryptFor("selftest:wrong",ct);}catch(SecurityException expected){wrongAadRejected=true;}
        if(!wrongAadRejected)throw new SecurityException("AAD binding self-test failed");

        int p=ct.lastIndexOf('.')+1;
        if(p<=0||p>=ct.length())throw new SecurityException("Ciphertext envelope self-test failed");
        char original=ct.charAt(p);char replacement=original=='A'?'B':'A';
        String tampered=ct.substring(0,p)+replacement+ct.substring(p+1);
        boolean tamperRejected=false;
        try{crypto.decryptFor(aad,tampered);}catch(SecurityException expected){tamperRejected=true;}
        if(!tamperRejected)throw new SecurityException("Tamper rejection self-test failed");

        String a=crypto.blindIndex("selftest","value");
        String b=crypto.blindIndex("selftest","value");
        String c=crypto.blindIndex("different","value");
        if(!a.equals(b)||a.equals(c))throw new SecurityException("Blind index self-test failed");
    }
}
