package org.mpi_sws.sddr_userspace.lib;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

public class CryptoLib {

    public static final int keySizeBits = 256;
    
    public static final int keySizeBytes() {
        return (int) keySizeBits / 8;
    }
    
    public static byte[] encrypt(final byte[] key, final byte[] clear) {
        try {
            final SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
            final Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            return cipher.doFinal(clear);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] decrypt(final byte[] key, final byte[] encrypted) {
        try {
            final SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
            final Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] generateDummyKey() {
        final byte[] key = new byte[keySizeBytes()];
        for (int i = 0; i < keySizeBytes(); i++) {
            key[i] = 42;
        }
        return key;
    }
    
    /**
     * Generate a hopefully proper key for use with AES encryption.
     * @return
     */
    public static byte[] generateKey() {
        try {
            final KeyGenerator kgen = KeyGenerator.getInstance("AES");
            kgen.init(keySizeBits, SecureRandom.getInstance("SHA1PRNG"));
            return kgen.generateKey().getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static void encryptFile(final byte[] key, final File input, final File output) {
        try {
            final SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
            final Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            final InputStream inStream = new FileInputStream(input);
            final OutputStream outStream = new CipherOutputStream(new FileOutputStream(output), cipher);
            Utils.streamCopy(inStream, outStream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void decryptFile(final byte[] key, final File input, final File output) {
        try {
            final SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
            final Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            final InputStream inStream = new CipherInputStream(new FileInputStream(input), cipher);
            final OutputStream outStream = new FileOutputStream(output);
            Utils.streamCopy(inStream, outStream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
