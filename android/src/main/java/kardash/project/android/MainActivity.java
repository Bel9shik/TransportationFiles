package kardash.project.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import kardash.project.transfer.Discovery;
import kardash.project.transfer.TransferClient;
import kardash.project.transfer.TransferNode;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Foreground LAN transfers; document access is granted by the system picker. */
public final class MainActivity extends Activity {
    private static final int OPEN = 1, SAVE = 2;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicLong lastProgress = new AtomicLong();
    private volatile boolean destroyed;
    private volatile TransferNode node;
    private volatile Discovery discovery;
    private TransferClient client;
    private WifiManager.MulticastLock multicast;
    private CompletableFuture<Path> pending;
    private boolean receiving, preparing;
    private Path selected, exporting;
    private String selectedName;
    private TextView status, local, fileLabel;
    private EditText address;
    private ProgressBar progress;
    private Button choose, send;
    private Spinner peers;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        text(content, "TransportationFiles", 26);
        text(content, "Оба устройства должны быть в одной доверенной сети. Оставьте приложение открытым до завершения.", 14);
        local = text(content, "Запуск приёмника…", 16);
        fileLabel = text(content, "Файл не выбран", 16);
        choose = button(content, "Выбрать файл", () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), OPEN));
        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("IP:порт получателя (или выберите ниже)");
        address.setContentDescription("Адрес получателя и порт");
        content.addView(address);
        peers = new Spinner(this);
        peers.setContentDescription("Устройства в локальной сети");
        content.addView(peers);
        send = button(content, "Отправить", this::send);
        button(content, "Отменить передачу", this::cancel);
        button(content, "Сохранить полученный файл…", this::chooseReceived);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        content.addView(progress);
        status = text(content, "Приём требует вашего подтверждения. Передача без шифрования.", 16);
        setBusy(false);
        worker.execute(this::startNetwork);
    }

    private TextView text(LinearLayout parent, String value, int size) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setPadding(0, 8, 0, 12);
        parent.addView(label);
        return label;
    }

    private Button button(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        parent.addView(button);
        return button;
    }

    private void ui(Runnable action) { runOnUiThread(() -> { if (!destroyed) action.run(); }); }
    private void message(String value) { ui(() -> status.setText(value)); }
    private boolean busy() { return preparing || receiving || pending != null || client != null; }
    private void setBusy(boolean value) {
        choose.setEnabled(!value);
        send.setEnabled(!value && selected != null && node != null);
    }

    private void startNetwork() {
        try {
            TransferNode started = new TransferNode(0, new TransferNode.Listener() {
                @Override public CompletableFuture<Path> accept(TransferNode.Offer offer) {
                    CompletableFuture<Path> answer = new CompletableFuture<>();
                    ui(() -> confirm(offer, answer));
                    if (destroyed) answer.complete(null);
                    return answer;
                }
                @Override public void progress(long bytes, long total) { showProgress(bytes, total); }
                @Override public void received(Path file) {
                    ui(() -> {
                        receiving = false;
                        progress.setProgress(1000);
                        status.setText("Получено и проверено: " + file.getFileName() + ". Нажмите «Сохранить полученный файл…» для экспорта.");
                        setBusy(busy());
                    });
                }
                @Override public void failed(String error) {
                    ui(() -> { receiving = false; status.setText("Ошибка: " + error); setBusy(busy()); });
                }
            });
            node = started;
            if (destroyed) { started.close(); return; }
            ui(() -> { local.setText("Ваш IP: " + Discovery.addresses() + "\nПорт: " + started.port()); setBusy(busy()); });
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi != null) {
                multicast = wifi.createMulticastLock("transportfiles-discovery");
                multicast.setReferenceCounted(false);
                multicast.acquire();
            }
            discovery = new Discovery(started.port(), android.os.Build.MODEL, list -> ui(() -> {
                Discovery.Peer selectedPeer = (Discovery.Peer) peers.getSelectedItem();
                ArrayAdapter<Discovery.Peer> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, list);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                peers.setAdapter(adapter);
                int index = list.indexOf(selectedPeer);
                if (index >= 0) peers.setSelection(index);
            }), this::message);
            if (destroyed) closeNetwork();
        } catch (Exception e) {
            if (multicast != null && multicast.isHeld()) multicast.release();
            if (destroyed) closeNetwork();
            else message("Сеть: " + e.getMessage() + ". Можно указать адрес вручную, если приёмник запущен.");
        }
    }

    private void confirm(TransferNode.Offer offer, CompletableFuture<Path> answer) {
        if (busy() || answer.isDone()) { answer.complete(null); return; }
        pending = answer;
        setBusy(true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Принять файл?")
                .setMessage(offer.sender() + "\n" + offer.filename() + "\n" + offer.size() + " байт")
                .setNegativeButton("Отклонить", (d, which) -> answer.complete(null))
                .setPositiveButton("Принять", (d, which) -> {
                    if (answer.isDone()) return;
                    try {
                        Path inbox = getFilesDir().toPath().resolve("received");
                        Files.createDirectories(inbox);
                        Path directory = Files.createDirectory(inbox.resolve(UUID.randomUUID().toString()));
                        receiving = true;
                        lastProgress.set(0);
                        progress.setProgress(0);
                        status.setText("Приём: " + offer.filename());
                        answer.complete(directory.resolve(offer.filename()));
                    } catch (IOException e) { answer.completeExceptionally(e); }
                }).setOnCancelListener(d -> answer.complete(null)).create();
        answer.whenComplete((path, error) -> ui(() -> {
            dialog.dismiss();
            if (pending == answer) pending = null;
            if (path == null) status.setText("Запрос отклонён, отменён или истёк");
            setBusy(busy());
        }));
        dialog.show();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == OPEN) {
            if (busy()) { message("Сначала завершите текущую передачу"); return; }
            preparing = true;
            setBusy(true);
            status.setText("Подготовка выбранного файла…");
            worker.execute(() -> prepare(uri));
        } else if (request == SAVE && exporting != null) {
            Path file = exporting;
            worker.execute(() -> {
                try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                    if (output == null) throw new IOException("Не удалось открыть файл назначения");
                    Files.copy(file, output);
                    message("Экспортирован: " + file.getFileName());
                } catch (Exception e) { message("Ошибка экспорта: " + e.getMessage() + ". Оригинал сохранён в приложении."); }
            });
        }
    }

    private void prepare(Uri uri) {
        Path staged = null;
        try {
            String name = "file";
            try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0);
            }
            TransferNode.validateFilename(name);
            staged = Files.createTempFile(getCacheDir().toPath(), "outgoing-", ".tmp");
            try (InputStream input = getContentResolver().openInputStream(uri);
                 OutputStream output = Files.newOutputStream(staged)) {
                if (input == null) throw new IOException("Не удалось прочитать выбранный документ");
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (destroyed || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Подготовка отменена");
                    output.write(buffer, 0, read);
                }
            }
            Path ready = staged;
            String displayName = name;
            if (destroyed) { Files.deleteIfExists(ready); return; }
            ui(() -> {
                if (selected != null) deleteStaged(selected);
                selected = ready;
                selectedName = displayName;
                preparing = false;
                fileLabel.setText(displayName);
                status.setText("Выберите получателя и нажмите «Отправить»");
                setBusy(busy());
            });
        } catch (Exception e) {
            if (staged != null) deleteStaged(staged);
            ui(() -> { preparing = false; status.setText("Ошибка чтения: " + e.getMessage()); setBusy(busy()); });
        }
    }

    private void send() {
        if (busy() || selected == null) return;
        try {
            Discovery.Peer peer = address.getText().toString().trim().isEmpty()
                    ? (Discovery.Peer) peers.getSelectedItem() : Discovery.endpoint(address.getText().toString());
            if (peer == null) { message("Выберите устройство или укажите IP:порт"); return; }
            TransferClient transfer = new TransferClient(peer.host(), peer.port());
            client = transfer;
            Path file = selected;
            String name = selectedName;
            setBusy(true);
            status.setText("Ожидание подтверждения получателя…");
            lastProgress.set(0);
            progress.setProgress(0);
            worker.execute(() -> {
                String result;
                try (transfer) {
                    transfer.send(file, name, android.os.Build.MODEL,
                            () -> message("Отправка: " + name), this::showProgress);
                    result = "Файл доставлен и проверен: " + name;
                } catch (Exception e) { result = "Ошибка: " + e.getMessage(); }
                String finalResult = result;
                ui(() -> {
                    if (client != transfer) return;
                    client = null;
                    status.setText(finalResult);
                    setBusy(busy());
                });
            });
        } catch (IllegalArgumentException e) { message(e.getMessage()); }
    }

    private void showProgress(long bytes, long total) {
        long now = System.nanoTime(), previous = lastProgress.get();
        if (bytes != total && now - previous < TimeUnit.MILLISECONDS.toNanos(100)) return;
        if (!lastProgress.compareAndSet(previous, now)) return;
        ui(() -> {
            progress.setProgress(total == 0 ? 1000 : (int) (1000.0 * bytes / total));
            status.setText(bytes + " / " + total + " байт; ожидание проверки");
        });
    }

    private void cancel() {
        if (client != null) { client.close(); client = null; }
        if (pending != null) pending.complete(null);
        if (node != null) node.cancelIncoming();
        receiving = false;
        status.setText(preparing ? "Подготовка документа ещё выполняется" : "Передача отменена");
        setBusy(busy());
    }

    private void chooseReceived() {
        if (busy()) { message("Сначала завершите текущую передачу"); return; }
        File root = new File(getFilesDir(), "received");
        List<File> files = new ArrayList<>();
        File[] directories = root.listFiles();
        if (directories != null) for (File directory : directories) {
            File[] saved = directory.listFiles(file -> file.isFile() && !(file.getName().startsWith(".transport-") && file.getName().endsWith(".part")));
            if (saved != null) Collections.addAll(files, saved);
        }
        files.sort(Comparator.comparingLong(File::lastModified).reversed());
        if (files.isEmpty()) { message("Полученных файлов пока нет"); return; }
        String[] names = files.stream().map(File::getName).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("Сохранить копию файла").setItems(names, (dialog, index) -> {
            exporting = files.get(index).toPath();
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, files.get(index).getName()), SAVE);
        }).setNegativeButton("Закрыть", null).show();
    }

    private static void deleteStaged(Path file) {
        try { Files.deleteIfExists(file); }
        catch (IOException ignored) { /* Android may reclaim an unused cache file later. */ }
    }

    private void closeNetwork() {
        if (discovery != null) discovery.close();
        if (node != null) node.close();
        if (multicast != null && multicast.isHeld()) multicast.release();
    }

    @Override public void onDestroy() {
        destroyed = true;
        if (pending != null) pending.complete(null);
        if (client != null) client.close();
        closeNetwork();
        worker.shutdownNow();
        if (selected != null) deleteStaged(selected);
        super.onDestroy();
    }
}
