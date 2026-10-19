package ru.local.qcatalog;

public final class InstallChecksTest {
    static int checks;
    static void require(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    public static void main(String[] args) {
        byte[][] a={{1}}, b={{2}}, rotated={{1},{2}}, multi={{1},{2}}, reversed={{2},{1}}, different={{1},{3}};
        require(!InstallChecks.signaturesConflict(false,a,false,a));
        require(InstallChecks.signaturesConflict(false,a,false,b));
        require(!InstallChecks.signaturesConflict(false,a,false,rotated));
        require(!InstallChecks.signaturesConflict(false,rotated,false,b));
        require(!InstallChecks.signaturesConflict(false,null,false,b));
        require(!InstallChecks.signaturesConflict(true,multi,true,reversed));
        require(InstallChecks.signaturesConflict(true,multi,true,different));
        require(InstallChecks.signaturesConflict(true,multi,false,rotated));
        require(InstallChecks.failure(5,"INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match").contains("другой подписью"));
        require(InstallChecks.failure(5,"Conflicting provider").contains("Conflicting provider"));
        require(InstallChecks.failure(6,"").contains("свободного места"));
        require(InstallChecks.failure(3,"").contains("без скачивания"));
        require(!InstallChecks.failure(1,"See https://example.invalid/?secret=hidden").contains("secret"));
        System.out.println("Install checks passed (" + checks + " cases)");
    }
}
