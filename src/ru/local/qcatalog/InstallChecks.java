package ru.local.qcatalog;

import java.util.Arrays;
import java.util.Locale;

/** Conservative preflight only; Android remains the final authority for updates. */
final class InstallChecks {
    static boolean signaturesConflict(boolean oldMultiple, byte[][] oldCertificates, boolean newMultiple, byte[][] newCertificates) {
        if (oldCertificates == null || newCertificates == null || oldCertificates.length == 0 || newCertificates.length == 0) return false;
        if (oldMultiple || newMultiple) {
            if (oldMultiple != newMultiple || oldCertificates.length != newCertificates.length) return true;
            for (byte[] old : oldCertificates) {
                boolean found = false;
                for (byte[] incoming : newCertificates) if (Arrays.equals(old,incoming)) found = true;
                if (!found) return true;
            }
            return false;
        }
        // Include verified rotation histories; a shared ancestor must be left to Android.
        for (byte[] old : oldCertificates) for (byte[] incoming : newCertificates) if (Arrays.equals(old,incoming)) return false;
        return true;
    }
    static String signatureConflict() {
        return "На шлеме уже установлено приложение с другой подписью. Android не разрешает обновить его этим APK. Для замены нужно отдельно удалить установленную версию; это может стереть сохранения и моды. Повторная установка того же файла не поможет.";
    }
    static String failure(int status, String detail) {
        String text = detail == null ? "" : detail.replaceAll("https?://\\S+","[адрес скрыт]").replaceAll("[\\r\\n]+"," ").trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("install_failed_update_incompatible") || lower.contains("signatures do not match") || lower.contains("inconsistent certificates")) return signatureConflict();
        String message;
        switch (status) {
            case 2: message = "Android заблокировал установку. Проверь системное окно и разрешение установки приложений."; break;
            case 3: message = "Установка отменена. Можно повторить без скачивания заново."; break;
            case 4: message = "Android отклонил APK как некорректный."; break;
            case 5: message = "Android обнаружил конфликт с установленным приложением или его данными."; break;
            case 6: message = "Android не хватило свободного места для установки."; break;
            case 7: message = "Этот APK несовместим с устройством или версией Android."; break;
            default: message = "Установка не завершена. Код Android: " + status + ".";
        }
        if (text.length() > 450) text = text.substring(0,450) + "…";
        return message + (text.isEmpty() ? "" : "\nПодробности Android: " + text);
    }
}
