package org.mpisws.sddrservice.lib;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Utils {

    private static final String TAG = getTAG(Utils.class);

    public static String getTAG(final Class c) {
        return "EbN-Java-" + c.getSimpleName();
    }

    public static byte[] getHash(final byte[] target, final int numBytes) {
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
        md.update(target, 0, target.length);
        final byte[] hash = md.digest();
        if (numBytes > hash.length) {
            throw new IllegalStateException("Requested " + numBytes + "-byte hash, algorithm only produced " + hash.length);
        }
        final byte[] truncatedHash = new byte[numBytes];
        System.arraycopy(hash, 0, truncatedHash, 0, numBytes);
        return truncatedHash;
    }

    public static String collectionToStringV2(final Collection<?> list, final String separator) {
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object s : list) {
            if (!first) {
                sb.append(separator);
            }
            sb.append(s.toString());
            first = false;
        }
        return sb.toString();
    }

    public static void myAssert(final boolean p) {
        if (!p) {
            throw new AssertionError();
        }
    }

    public static void myAssert(final boolean p, final String msg) {
        if (!p) {
            throw new AssertionError(msg);
        }
    }

    public static String getHexString(final byte[] b) {
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            result.append(Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString().toUpperCase();
    }

    public static byte[] hexStringToByteArray(final String s) {
        myAssert(s.length() % 2 == 0);
        final byte[] bytes = new byte[s.length() / 2];
        for (int i = 0; i < s.length() / 2; i++) {
            bytes[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }

    public static int getMyDeviceID() {
        try {
            final File idFile = new File(Environment.getExternalStorageDirectory(), "id.txt");
            final BufferedReader br = new BufferedReader(new FileReader(idFile));
            final int deviceID = Integer.parseInt(br.readLine());
            br.close();
            return deviceID;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void printIntent(final String tag, final Intent intent) {
        Log.d(tag, "Intent received: " + intent);
        if (intent != null) {
            if (intent.getExtras() != null) {
                for (String key : intent.getExtras().keySet()) {
                    Log.d(tag, "\tIntent extras: [" + key + "] => [" + intent.getExtras().get(key) + "]");
                }
            } else {
                Log.d(tag, "\t NO EXTRAS");
                return;
            }
        }
    }

    public static double median(final List<Long> collection) {
        Collections.sort(collection);
        final int middle = collection.size() / 2;
        if (collection.size() % 2 == 1) {
            // Odd number of elements -- return the middle one
            return collection.get(middle);
        } else {
            // Even number -- return average of middle two
            return ((double) (collection.get(middle - 1) + collection.get(middle))) / 2d;
        }
    }

    public static AlertDialog getBasicAlertDialog(final Context context, final String title, final String message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false); // This blocks the 'BACK' button
        builder.setMessage(message);
        builder.setTitle(title);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        return builder.create();
    }

    public static void showBasicAlertDialog(final Context context, final String title, final String message) {
        getBasicAlertDialog(context, title, message).show();
    }

    public static int bytesToInt(final byte[] bytes) {
        int i = 0;
        i += (int) bytes[0] & 0xFF;
        i += ((int) bytes[1] & 0xFF) << 8;
        i += ((int) bytes[2] & 0xFF) << 16;
        i += ((int) bytes[3] & 0xFF) << 24;
        return i;
    }

    public static byte[] intToBytes(final int i) {
        final byte[] bytes = new byte[4];
        bytes[0] = (byte) (i & 0xFF);
        bytes[1] = (byte) ((i >> 8) & 0xFF);
        bytes[2] = (byte) ((i >> 16) & 0xFF);
        bytes[3] = (byte) ((i >> 24) & 0xFF);
        return bytes;
    }

    public static <SRC, TGT> List<TGT> applyTransformator(final List<SRC> srcList, final Transformator<SRC, TGT> transformator) {
        final List<TGT> targetList = new LinkedList<TGT>();
        for (SRC src : srcList) {
            targetList.add(transformator.transform(src));
        }
        return targetList;
    }
    
    public static void streamCopy(final InputStream inStream, final OutputStream outStream) throws IOException {
        final byte[] buffer = new byte[4096];
        int count;
        while ((count = inStream.read(buffer)) >= 0) {
            outStream.write(buffer, 0, count);
        }
        inStream.close();
        outStream.close();
    }
    
    public static String serializeObjectToString(Object object) throws IOException {
        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(arrayOutputStream);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(gzipOutputStream);
        Log.d(TAG, "Serializing Object 0");
        objectOutputStream.writeObject(object);
        Log.d(TAG, "Serializing Object 0.5");
        objectOutputStream.flush();
        Log.d(TAG, "Serializing Object 1");
        String str = Base64.encode(arrayOutputStream.toByteArray(), Base64.DEFAULT).toString();
        Log.d(TAG, "Serializing Object 2");
        gzipOutputStream.close();
        arrayOutputStream.close();
        objectOutputStream.close();
        return str;
    }

    public static Object deserializeObjectFromString(String objectString) throws IOException, ClassNotFoundException {
        ByteArrayInputStream arrayInputStream = new ByteArrayInputStream(Base64.decode(objectString, Base64.DEFAULT));
        GZIPInputStream gzipInputStream = new GZIPInputStream(arrayInputStream);
        ObjectInputStream objectInputStream = new ObjectInputStream(gzipInputStream);
        Object obj = objectInputStream.readObject();
        objectInputStream.close();
        arrayInputStream.close();
        gzipInputStream.close();
        return obj;
    }

    public static BigInteger ack(BigInteger m, BigInteger n) {
        return m.equals(BigInteger.ZERO)
                ? n.add(BigInteger.ONE)
                : ack(m.subtract(BigInteger.ONE),
                n.equals(BigInteger.ZERO) ? BigInteger.ONE : ack(m, n.subtract(BigInteger.ONE)));
    }

    public static String encrypt(String strToEncrypt, String secret) {
        if (secret == null) {
            return strToEncrypt;
        }
        try
        {
            SecretKeySpec secretKey;
            byte[] key;
            MessageDigest sha = null;
            key = secret.getBytes("UTF-8");
            sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            secretKey = new SecretKeySpec(key, "AES");

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.encodeToString(cipher.doFinal(strToEncrypt.getBytes("UTF-8")), Base64.DEFAULT);
        }
        catch (Exception e)
        {
            System.out.println("Error while encrypting: " + e.toString());
        }
        return null;
    }

    public static String decrypt(String strToDecrypt, String secret)
    {
        Log.d("DEBUG", "Decrypting " + strToDecrypt + " with " + secret);
        if (secret == null)
            return strToDecrypt;
        try
        {
            SecretKeySpec secretKey;
            byte[] key;
            MessageDigest sha = null;
            key = secret.getBytes("UTF-8");
            sha = MessageDigest.getInstance("SHA-1");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            secretKey = new SecretKeySpec(key, "AES");

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.decode(strToDecrypt, Base64.DEFAULT)));
        }
        catch (Exception e)
        {
            System.out.println("Error while decrypting: " + e.toString());
        }
        return null;
    }

    public static byte[] SHA1(byte[] toHash) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-1");
        }
        catch(NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return md.digest(toHash);
    }
}
