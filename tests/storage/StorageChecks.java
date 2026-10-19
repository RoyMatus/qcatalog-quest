package ru.local.qcatalog;

import android.app.*;
import android.content.Context;
import android.os.Bundle;
import java.io.*;
import java.nio.file.Files;

/** Runs in a separate app/UID; never starts, stops, reads, or changes the real QCatalog app. */
public final class StorageChecks extends Instrumentation {
    private int checks;
    private void require(boolean ok,String message) { checks++; if (!ok) throw new AssertionError(message); }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); int outcome = Activity.RESULT_OK;
        try {
            Context isolated = getTargetContext();
            require(PrivateStore.loadUrl(isolated).isEmpty(),"Fresh storage has no URL");
            PrivateStore.saveUrl(isolated,"");
            require(PrivateStore.loadUrl(isolated).isEmpty(),"File import accepts an empty encrypted URL");
            File file = new File(isolated.getFilesDir(),"access.bin");
            require(file.length() == 29,"Empty URL has an IV and authentication tag");
            String example = "https://example.invalid/config?value=example";
            PrivateStore.saveUrl(isolated,example);
            require(PrivateStore.loadUrl(isolated).equals(example),"Non-empty URL survives encryption");
            byte[] tampered = Files.readAllBytes(file.toPath()); tampered[tampered.length-1] ^= 1;
            Files.write(file.toPath(),tampered);
            boolean rejected = false; try { PrivateStore.loadUrl(isolated); } catch (Exception expected) { rejected = true; }
            require(rejected,"Modified ciphertext is rejected");
            Files.write(file.toPath(),new byte[0]);
            boolean truncated = false; try { PrivateStore.loadUrl(isolated); } catch (IOException expected) { truncated = true; }
            require(truncated,"Truncated ciphertext returns a controlled error");
            result.putString("result","PASS " + checks + " storage checks in a separate app; QCatalog and its downloads were not touched.");
        } catch (Throwable e) { result.putString("result","FAIL: " + e.getClass().getSimpleName() + ": " + e.getMessage()); outcome = Activity.RESULT_CANCELED; }
        finish(outcome,result);
    }
}
