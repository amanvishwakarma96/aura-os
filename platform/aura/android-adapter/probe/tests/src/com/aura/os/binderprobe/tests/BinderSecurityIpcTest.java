package com.aura.os.binderprobe.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Live multi-process Binder smoke tests. Run only on an isolated AOSP emulator.
 * The separate attacker APK must be installed with a DIFFERENT signing certificate.
 */
@RunWith(AndroidJUnit4.class)
public class BinderSecurityIpcTest {
    private static final int TX = IBinder.FIRST_CALL_TRANSACTION;

    private static final class BoundService implements ServiceConnection, AutoCloseable {
        final Context context;
        final CountDownLatch connected = new CountDownLatch(1);
        IBinder binder;
        boolean bound;

        BoundService(Context context, ComponentName component) {
            this.context = context;
            bound = context.bindService(new Intent().setComponent(component),
                    this, Context.BIND_AUTO_CREATE);
            assertTrue("Could not bind service: " + component, bound);
        }

        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            binder = service;
            connected.countDown();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            binder = null;
        }

        IBinder await() throws InterruptedException {
            assertTrue("Timed out waiting for Binder connection",
                    connected.await(15, TimeUnit.SECONDS));
            assertTrue("Binder unavailable", binder != null && binder.isBinderAlive());
            return binder;
        }

        @Override public void close() {
            if (bound) {
                context.unbindService(this);
                bound = false;
            }
        }
    }

    @Test public void genuineRemoteBinderAcceptsTrustedHostProcess() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ComponentName service = new ComponentName("com.aura.os.binderprobe",
                "com.aura.os.binderprobe.IdentityProbeService");
        try (BoundService connection = new BoundService(context, service)) {
            Parcel request = Parcel.obtain();
            Parcel result = Parcel.obtain();
            try {
                request.writeInterfaceToken("com.aura.os.binderprobe.IDENTITY");
                request.writeString("com.untrusted.forged.claim");
                assertTrue("Binder transaction failed", connection.await().transact(
                        TX, request, result, 0));
                result.readException();
                assertEquals("UID must be derived from Binder, not claim",
                        Process.myUid(), result.readInt());
                assertNotEquals("Endpoint must run in another process",
                        Process.myPid(), result.readInt());
            } finally {
                request.recycle();
                result.recycle();
            }
        }
    }

    @Test public void untrustedSeparateApkCannotUseForgedTrustedPackage() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("Attacker test APK must be signed with a different certificate",
                PackageManager.SIGNATURE_NO_MATCH,
                context.getPackageManager().checkSignatures(
                        "com.aura.os.binderprobe",
                        "com.aura.os.binderprobe.attacker"));
        assertEquals("Attacker APK must not hold the signature-only permission",
                PackageManager.PERMISSION_DENIED,
                context.getPackageManager().checkPermission(
                        "com.aura.os.permission.ACCESS_AGENT_SERVICE",
                        "com.aura.os.binderprobe.attacker"));
        ComponentName attacker = new ComponentName("com.aura.os.binderprobe.attacker",
                "com.aura.os.binderprobe.attacker.AttackerBridgeService");
        try (BoundService connection = new BoundService(context, attacker)) {
            Parcel request = Parcel.obtain();
            Parcel result = Parcel.obtain();
            try {
                request.writeInterfaceToken("com.aura.os.binderprobe.ATTACKER");
                assertTrue(connection.await().transact(TX, request, result, 0));
                result.readException();
                int verdict = result.readInt(); // 1: denied; 0: bypass; 2: inconclusive
                int attackerUid = result.readInt();
                assertNotEquals("Attacker must have a different UID",
                        Process.myUid(), attackerUid);
                assertEquals("Denied required. 0=security bypass; 2=not exercised", 1, verdict);
            } finally {
                request.recycle();
                result.recycle();
            }
        }
    }
}
