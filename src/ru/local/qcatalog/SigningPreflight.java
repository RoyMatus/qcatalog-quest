package ru.local.qcatalog;

import android.content.Context;
import android.content.pm.*;
import java.io.IOException;

final class SigningPreflight {
    private static byte[][] certificates(SigningInfo info) {
        if (info == null) return null;
        Signature[] signatures = info.hasMultipleSigners() ? info.getApkContentsSigners() : info.getSigningCertificateHistory();
        if (signatures == null) return null;
        byte[][] result = new byte[signatures.length][];
        for (int i=0;i<signatures.length;i++) result[i] = signatures[i].toByteArray();
        return result;
    }
    static void check(Context c, PackageInfo incomingPackage) throws IOException {
        PackageInfo old;
        try { old = c.getPackageManager().getPackageInfo(incomingPackage.packageName,PackageManager.GET_SIGNING_CERTIFICATES); }
        catch (PackageManager.NameNotFoundException absent) { return; }
        SigningInfo previous = old.signingInfo, incoming = incomingPackage.signingInfo;
        if (previous != null && incoming != null && InstallChecks.signaturesConflict(previous.hasMultipleSigners(),certificates(previous),incoming.hasMultipleSigners(),certificates(incoming)))
            throw new IOException(InstallChecks.signatureConflict());
    }
}
