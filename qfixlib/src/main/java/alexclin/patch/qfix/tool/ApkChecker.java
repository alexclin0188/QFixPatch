package alexclin.patch.qfix.tool;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


class ApkChecker {

    private static final String TAG = "ApkChecker";

    private static final String CLASSES_DEX = "classes.dex";

    /**
     * @param context Context
     * @param path    Apk file
     * @return true if verify apk success
     */
    static boolean verifyApk(Context context, File path) {
        PublicKey[] publicKeys;
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();

            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            CertificateFactory certFactory = CertificateFactory
                    .getInstance("X.509");
            publicKeys = new PublicKey[packageInfo.signatures.length];
            for(int i = 0;i<packageInfo.signatures.length; i++){
                ByteArrayInputStream stream = new ByteArrayInputStream(packageInfo.signatures[i].toByteArray());
                X509Certificate cert = (X509Certificate) certFactory
                        .generateCertificate(stream);
                publicKeys[i] = cert.getPublicKey();
            }
        } catch (Exception e) {
            Log.e("TAG","verifyApk", e);
            return false;
        }
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(path);

            JarEntry jarEntry = jarFile.getJarEntry(CLASSES_DEX);
            if (null == jarEntry) {// no code
                return false;
            }
            loadDigestes(jarFile,jarEntry);
            Certificate[] certs = jarEntry.getCertificates();
            if (certs == null) {
                return false;
            }
            for(PublicKey key:publicKeys){
                if(!check(path, certs, key)) return false;
            }
            return true;
        } catch (Exception e) {
            Log.e("TAG",path.getAbsolutePath(), e);
            return false;
        } finally {
            try {
                if (jarFile != null) {
                    jarFile.close();
                }
            } catch (Exception e) {
                Log.e("TAG",path.getAbsolutePath(), e);
            }
        }
    }

    private static void loadDigestes(JarFile jarFile, JarEntry je) throws IOException {
        InputStream is = null;
        try {
            is = jarFile.getInputStream(je);
            byte[] bytes = new byte[8192];
            while (is.read(bytes) > 0) {
                //just read all bytes
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // verify the signature of the Apk
    private static boolean check(File path, Certificate[] certs, PublicKey publicKey) {
        if (certs.length > 0) {
            for (int i = certs.length - 1; i >= 0; i--) {
                try {
                    certs[i].verify(publicKey);
                    return true;
                } catch (Exception e) {
                    Log.e("TAG", path.getAbsolutePath(), e);
                }
            }
        }
        return false;
    }
}
