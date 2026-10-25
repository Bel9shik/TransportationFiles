package kardash.project.android;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import kardash.project.transfer.TransferClient;
import kardash.project.transfer.TransferNode;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Exercise the actual screens and incoming confirmation with a real gRPC sender. */
@RunWith(AndroidJUnit4.class)
public class InterfaceInstrumentedTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test public void sendingWaitsForReceiverBeforeShowingSuccess() throws Exception {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 29);
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        var resolver = activity.getContentResolver();
        android.content.ContentValues metadata = new android.content.ContentValues();
        metadata.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "Фото для проверки.bin");
        metadata.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
        android.net.Uri document = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, metadata);
        assertNotNull(document);
        byte[] bytes = new byte[128 * 1024];
        new java.util.Random(19).nextBytes(bytes);
        try (var stream = resolver.openOutputStream(document)) { stream.write(bytes); }
        Path directory = Files.createTempDirectory(activity.getCacheDir().toPath(), "ui-receiver-");
        Path destination = directory.resolve("received.bin");
        CountDownLatch receivedAll = new CountDownLatch(1), verify = new CountDownLatch(1);
        CompletableFuture<Path> delivery = new CompletableFuture<>();
        try (TransferNode receiver = new TransferNode(0, new TransferNode.Listener() {
            @Override public CompletableFuture<Path> accept(TransferNode.Offer offer) {
                return CompletableFuture.completedFuture(destination);
            }
            @Override public void received(Path file) { delivery.complete(file); }
            @Override public void failed(String error) { delivery.completeExceptionally(new AssertionError(error)); }
            @Override public void progress(long received, long total) {
                if (received != total) return;
                receivedAll.countDown();
                try { assertTrue(verify.await(15, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            }
        })) {
            waitForText("●  Приёмник готов · локальная сеть");
            click("Отправить файл");
            instrumentation.runOnMainSync(() -> activity.onActivityResult(1, android.app.Activity.RESULT_OK,
                    new Intent().setData(document)));
            waitForText("Фото для проверки.bin");
            instrumentation.runOnMainSync(() -> {
                android.widget.EditText address = activity.getWindow().getDecorView().findViewWithTag("recipient-address");
                address.setText("127.0.0.1:" + receiver.port());
            });
            screenshot("send-selected");
            click("Отправить");
            assertTrue(receivedAll.await(15, TimeUnit.SECONDS));
            waitForText("Проверяем файл…");
            assertNull(find("Отправка завершена"));
            screenshot("sending");
            verify.countDown();
            waitForText("Отправка завершена");
            assertEquals(destination, delivery.get(10, TimeUnit.SECONDS));
            assertArrayEquals(bytes, Files.readAllBytes(destination));
            screenshot("sent");
        } finally {
            verify.countDown();
            resolver.delete(document, null, null);
            instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();
            Files.deleteIfExists(destination);
            Files.deleteIfExists(directory);
        }
    }

    @Test public void navigationReceiveAndCancelKeepAccurateStatus() throws Exception {
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ExecutorService sender = Executors.newSingleThreadExecutor();
        Path source = Files.createTempFile(activity.getCacheDir().toPath(), "ui-source-", ".bin");
        String filename = "UI-проверка-" + System.nanoTime() + ".bin";
        byte[] bytes = new byte[128 * 1024];
        new java.util.Random(17).nextBytes(bytes);
        Files.write(source, bytes);
        try {
            waitForText("●  Приёмник готов · локальная сеть");
            screenshot("home");
            click("Отправить файл");
            waitForText("1 · Файл");
            assertFalse(find("Отправить").isEnabled());
            screenshot("send");
            click("На главный экран");
            click("Принять файл");
            waitForText("Готовы к приёму");
            screenshot("receive");
            AtomicInteger port = new AtomicInteger();
            instrumentation.runOnMainSync(() -> {
                TextView endpoint = activity.getWindow().getDecorView().findViewWithTag("local-address");
                port.set(Integer.parseInt(endpoint.getText().toString().split("Порт: ")[1]));
            });
            CountDownLatch accepted = new CountDownLatch(1), proceed = new CountDownLatch(1);
            Future<?> transfer = sender.submit(() -> send(source, filename, port.get(), accepted, proceed));
            waitForText("Входящий файл");
            screenshot("incoming");
            click("Принять");
            assertTrue(accepted.await(10, TimeUnit.SECONDS));
            waitForText("Приём файла");
            assertNull(find("Приём завершён"));
            screenshot("receiving");
            proceed.countDown();
            transfer.get(30, TimeUnit.SECONDS);
            waitForText("Приём завершён");
            assertNotNull(find("Сохранить файл…"));
            screenshot("complete");
            try (var files = Files.walk(activity.getFilesDir().toPath().resolve("received"))) {
                Path received = files.filter(path -> path.getFileName().toString().equals(filename)).findFirst().orElseThrow();
                assertArrayEquals(bytes, Files.readAllBytes(received));
                Files.delete(received);
                Files.delete(received.getParent());
            }
            click("На главный экран");
            CountDownLatch acceptedAgain = new CountDownLatch(1), proceedAgain = new CountDownLatch(1);
            Future<?> cancelled = sender.submit(() -> send(source, filename, port.get(), acceptedAgain, proceedAgain));
            waitForText("Входящий файл");
            click("Принять");
            assertTrue(acceptedAgain.await(10, TimeUnit.SECONDS));
            click("Отменить передачу");
            waitForText("Передача отменена");
            proceedAgain.countDown();
            try { cancelled.get(20, TimeUnit.SECONDS); fail("Cancelled receiver accepted the file"); }
            catch (ExecutionException expected) { /* Sender must see receiver cancellation. */ }
            waitForText("Передача отменена");
            assertNull(find("Приём завершён"));
            screenshot("cancelled");
        } finally {
            sender.shutdownNow();
            instrumentation.runOnMainSync(activity::finish);
            instrumentation.waitForIdleSync();
            Files.deleteIfExists(source);
        }
    }

    private void send(Path source, String filename, int port, CountDownLatch accepted, CountDownLatch proceed) {
        try (TransferClient client = new TransferClient("127.0.0.1", port)) {
            client.send(source, filename, "Windows · тест", () -> {
                accepted.countDown();
                try { assertTrue(proceed.await(15, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            }, (bytes, total) -> {});
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private AccessibilityNodeInfo find(String text) {
        AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
        if (root == null) return null;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
            if (node.isVisibleToUser() && text.contentEquals(node.getText() == null ? "" : node.getText())) return node;
        }
        return null;
    }

    private void waitForText(String text) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (find(text) != null) return;
            Thread.sleep(100);
        }
        fail("Visible text not found: " + text);
    }

    private void click(String text) throws Exception {
        waitForText(text);
        AccessibilityNodeInfo node = find(text);
        assertTrue("Not clickable: " + text, node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        instrumentation.waitForIdleSync();
    }

    private void screenshot(String name) throws Exception {
        Thread.sleep(250);
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(instrumentation.getTargetContext().getExternalFilesDir(null), "design-screenshots");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
}
