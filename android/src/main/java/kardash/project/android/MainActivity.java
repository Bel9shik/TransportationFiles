package kardash.project.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
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

    private static final int INK = 0xff243b55, MUTED = 0xff596b7d;
    private static final int BLUE = 0xff2869bd, GREEN = 0xff218354, RED = 0xffc43d35;
    private ScrollView scroll;
    private LinearLayout homeScreen, sendScreen, receiveScreen, transferScreen;
    private View currentScreen;
    private TextView notice, homeStatus, receiveAddress, receiveTitle, peerHint, transferTitle, transferSubtitle, transferFile, percent;
    private RadioGroup peers;
    private Discovery.Peer selectedPeer;
    private Button transferAction, exportAction;
    private TransferAnimation animation;
    private ImageView resultIcon, brandIcon;
    private Path lastReceived;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildInterface();
        setBusy(false);
        worker.execute(this::startNetwork);
    }

    private void buildInterface() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(true);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        FrameLayout frame = new FrameLayout(this);
        scroll.addView(frame);
        LinearLayout content = column();
        content.setPadding(0, dp(24), 0, dp(28));
        frame.addView(content, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL));
        frame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (brandIcon != null) brandIcon.setVisibility(scroll.getWidth() > scroll.getHeight() ? View.GONE : View.VISIBLE);
            int width = Math.min(dp(560), r - l - dp(40));
            if (width > 0 && content.getLayoutParams().width != width) {
                content.getLayoutParams().width = width;
                content.requestLayout();
            }
        });
        setContentView(scroll);

        homeScreen = screen(content);
        brandIcon = image(homeScreen, R.mipmap.ic_launcher, 88);
        brandIcon.setContentDescription("TransportationFiles");
        heading(homeScreen, "File Transfer", "Файлы между телефоном и компьютером\nв одной локальной сети");
        homeStatus = text(homeScreen, "Подключаемся к сети…", 14, 0xffe2edf8);
        homeStatus.setGravity(Gravity.CENTER);
        homeStatus.setPadding(dp(12), dp(12), dp(12), dp(12));
        homeStatus.setBackground(rounded(0x20ffffff, 16));
        LinearLayout actions = card(homeScreen);
        text(actions, "Выберите действие", 20, INK).setTypeface(null, Typeface.BOLD);
        Button receive = button(actions, "Принять файл", GREEN, () -> showScreen(receiveScreen));
        buttonIcon(receive, R.drawable.receive_file);
        Button outgoing = button(actions, "Отправить файл", RED, () -> showScreen(sendScreen));
        buttonIcon(outgoing, R.drawable.send_file);
        LinearLayout device = card(homeScreen);
        text(device, "Это устройство", 13, MUTED);
        text(device, android.os.Build.MODEL, 19, INK).setTypeface(null, Typeface.BOLD);
        local = text(device, "Запуск приёмника…", 14, MUTED);
        local.setTag("local-address");
        local.setTextIsSelectable(true);
        button(device, "Полученные файлы", 0xffedf3fa, this::chooseReceived);
        text(homeScreen, "Оставьте приложение открытым. Передача без шифрования — используйте доверенную сеть.", 13, 0xfff0f5fc);

        sendScreen = screen(content);
        heading(sendScreen, "Отправить файл", "Выберите документ и получателя");
        LinearLayout source = card(sendScreen);
        text(source, "1 · Файл", 18, INK).setTypeface(null, Typeface.BOLD);
        fileLabel = text(source, "Пока ничего не выбрано", 16, MUTED);
        choose = button(source, "Выбрать файл", BLUE, () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), OPEN));
        LinearLayout recipients = card(sendScreen);
        text(recipients, "2 · Получатель", 18, INK).setTypeface(null, Typeface.BOLD);
        peerHint = text(recipients, "Ищем устройства в локальной сети…", 14, MUTED);
        peers = new RadioGroup(this);
        peers.setOrientation(LinearLayout.VERTICAL);
        recipients.addView(peers, new LinearLayout.LayoutParams(-1, -2));
        text(recipients, "На другом устройстве откройте экран приёма. Если устройство не появилось, укажите его адрес ниже.", 13, MUTED);
        text(recipients, "Адрес вручную", 14, INK).setTypeface(null, Typeface.BOLD);
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(16);
        address.setTextColor(INK);
        address.setHintTextColor(MUTED);
        address.setHint("192.168.1.10:50051");
        address.setContentDescription("Адрес получателя и порт");
        address.setTag("recipient-address");
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setPadding(dp(14), dp(12), dp(14), dp(12));
        address.setMinHeight(dp(52));
        address.setBackground(rounded(0xffedf3fa, 12));
        recipients.addView(address, new LinearLayout.LayoutParams(-1, -2));
        send = button(sendScreen, "Отправить", RED, this::send);
        button(sendScreen, "На главный экран", 0xffffffff, () -> showScreen(homeScreen));

        receiveScreen = screen(content);
        heading(receiveScreen, "Принять файл", "Выберите этот телефон на отправителе");
        LinearLayout waiting = card(receiveScreen);
        ImageView receiveIcon = image(waiting, R.drawable.receive_file, 64);
        receiveIcon.setColorFilter(GREEN);
        receiveTitle = text(waiting, "Запускаем приёмник…", 22, INK);
        receiveTitle.setTypeface(null, Typeface.BOLD);
        receiveTitle.setGravity(Gravity.CENTER);
        text(waiting, "Когда вам отправят файл, здесь появится запрос. Приём начнётся только после вашего согласия.", 16, MUTED);
        receiveAddress = text(waiting, "Запуск приёмника…", 15, INK);
        receiveAddress.setTextIsSelectable(true);
        text(waiting, "После приёма нажмите «Сохранить файл…», чтобы выбрать место сохранения на телефоне.", 14, MUTED);
        button(receiveScreen, "Полученные файлы", GREEN, this::chooseReceived);
        button(receiveScreen, "На главный экран", 0xffffffff, () -> showScreen(homeScreen));

        transferScreen = screen(content);
        transferTitle = text(transferScreen, "Передача файла", 28, 0xffffffff);
        transferTitle.setTypeface(null, Typeface.BOLD);
        transferTitle.setGravity(Gravity.CENTER);
        transferSubtitle = text(transferScreen, "Не закрывайте приложение до завершения", 14, 0xffe2e8f0);
        transferSubtitle.setGravity(Gravity.CENTER);
        LinearLayout transferCard = card(transferScreen);
        animation = new TransferAnimation(this);
        transferCard.addView(animation, new LinearLayout.LayoutParams(-1, dp(156)));
        resultIcon = image(transferCard, R.drawable.transfer_complete, 80);
        resultIcon.setVisibility(View.GONE);
        percent = text(transferCard, "Ждём получателя…", 24, INK);
        percent.setGravity(Gravity.CENTER);
        percent.setTypeface(null, Typeface.BOLD);
        transferFile = text(transferCard, "", 16, INK);
        transferFile.setGravity(Gravity.CENTER);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(GREEN));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xffe5edf3));
        progress.setIndeterminateTintList(ColorStateList.valueOf(GREEN));
        LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(-1, dp(10));
        bar.setMargins(0, dp(14), 0, dp(14));
        transferCard.addView(progress, bar);
        status = text(transferCard, "", 15, MUTED);
        status.setGravity(Gravity.CENTER);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        exportAction = button(transferScreen, "Сохранить файл…", GREEN, () -> exportFile(lastReceived));
        exportAction.setVisibility(View.GONE);
        transferAction = button(transferScreen, "Отменить передачу", 0xffffffff, () -> {
            if (busy()) cancel(); else showScreen(homeScreen);
        });
        notice = text(content, "", 15, 0xffffffff);
        notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        notice.setVisibility(View.GONE);
        showScreen(homeScreen);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    private LinearLayout screen(LinearLayout parent) {
        LinearLayout view = column();
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        return background;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout view = column();
        view.setPadding(dp(20), dp(18), dp(20), dp(20));
        view.setBackground(rounded(0xffffffff, 24));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(18), 0, 0);
        parent.addView(view, params);
        return view;
    }
    private TextView text(LinearLayout parent, String value, int size, int color) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setPadding(0, dp(4), 0, dp(8));
        parent.addView(label, new LinearLayout.LayoutParams(-1, -2));
        return label;
    }
    private void heading(LinearLayout parent, String title, String description) {
        TextView heading = text(parent, title, 30, 0xffffffff);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        TextView subtitle = text(parent, description, 15, 0xffe2edf8);
        subtitle.setGravity(Gravity.CENTER);
    }
    private ImageView image(LinearLayout parent, int resource, int size) {
        ImageView image = new ImageView(this);
        image.setImageResource(resource);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(size), dp(size));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, dp(8), 0, dp(12));
        parent.addView(image, params);
        return image;
    }
    private Button button(LinearLayout parent, String label, int color, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(color == 0xffffffff || color == 0xffedf3fa ? INK : 0xffffffff);
        button.setMinHeight(dp(56));
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x223498db), rounded(color, 16), null));
        button.setStateListAnimator(null);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(12), 0, 0);
        parent.addView(button, params);
        return button;
    }
    private void buttonIcon(Button button, int resource) {
        android.graphics.drawable.Drawable icon = getDrawable(resource);
        icon.setBounds(0, 0, dp(28), dp(28));
        button.setCompoundDrawablesRelative(icon, null, null, null);
        button.setCompoundDrawablePadding(dp(12));
    }
    private void showScreen(View screen) {
        currentScreen = screen;
        for (View view : new View[]{homeScreen, sendScreen, receiveScreen, transferScreen}) {
            view.setVisibility(view == screen ? View.VISIBLE : View.GONE);
        }
        if (screen != transferScreen) {
            scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff2c3e50, 0xff3498db}));
        }
        notice.setVisibility(View.GONE);
        scroll.post(() -> scroll.smoothScrollTo(0, 0));
    }
    private void beginTransfer(String title, String filename, boolean incoming) {
        showScreen(transferScreen);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                incoming ? new int[]{0xff173b48, 0xff267c75} : new int[]{0xff243b55, 0xffb24552}));
        transferTitle.setText(title);
        transferSubtitle.setText("Не закрывайте приложение до завершения");
        transferFile.setText(filename);
        percent.setText(incoming ? "Принимаем…" : "Ждём получателя…");
        status.setText(incoming ? "Сохраняем и проверяем файл" : "Подтвердите приём на другом устройстве");
        progress.setProgress(0);
        progress.setIndeterminate(true);
        animation.setVisibility(View.VISIBLE);
        resultIcon.setVisibility(View.GONE);
        exportAction.setVisibility(View.GONE);
        transferAction.setText("Отменить передачу");
    }
    private void finishTransfer(boolean success, String title, String detail, Path received) {
        transferTitle.setText(title);
        transferSubtitle.setText(success ? (received == null ? "Можно отправить следующий файл" : "Выберите, куда сохранить файл")
                : "Можно вернуться и попробовать снова");
        percent.setText(success ? "Готово" : "Не завершено");
        status.setText(detail);
        progress.setIndeterminate(false);
        if (success) progress.setProgress(1000);
        animation.setVisibility(View.GONE);
        resultIcon.setImageResource(success ? R.drawable.transfer_complete : R.drawable.transfer_error);
        resultIcon.setColorFilter(success ? GREEN : RED);
        resultIcon.setVisibility(View.VISIBLE);
        lastReceived = received;
        exportAction.setVisibility(received != null ? View.VISIBLE : View.GONE);
        transferAction.setText("На главный экран");
    }
    private void updatePeers(List<Discovery.Peer> list) {
        if (selectedPeer != null && !list.contains(selectedPeer)) selectedPeer = null;
        peers.removeAllViews();
        for (Discovery.Peer peer : list) {
            RadioButton item = new RadioButton(this);
            item.setText(peer.name() + "\n" + peer.host() + ":" + peer.port());
            item.setTextColor(INK);
            item.setTextSize(15);
            item.setMinHeight(dp(64));
            item.setPadding(dp(6), dp(8), dp(6), dp(8));
            item.setButtonTintList(ColorStateList.valueOf(BLUE));
            item.setId(View.generateViewId());
            peers.addView(item, new RadioGroup.LayoutParams(-1, -2));
            item.setChecked(peer.equals(selectedPeer));
            item.setEnabled(!busy());
            item.setOnClickListener(view -> { selectedPeer = peer; address.setText(""); });
        }
        peerHint.setText(list.isEmpty() ? "Пока никого не нашли. Поиск продолжается…" : "Устройств рядом: " + list.size());
    }

    @Override public void onBackPressed() {
        if (busy()) { cancel(); return; }
        if (currentScreen != homeScreen) showScreen(homeScreen); else super.onBackPressed();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && android.os.Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
            getWindow().getInsetsController().setSystemBarsAppearance(0,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
    }

    private void ui(Runnable action) { runOnUiThread(() -> { if (!destroyed) action.run(); }); }
    private void message(String value) { ui(() -> { notice.setText(value); notice.setVisibility(View.VISIBLE); }); }
    private boolean busy() { return preparing || receiving || pending != null || client != null; }
    private void setBusy(boolean value) {
        choose.setEnabled(!value);
        send.setEnabled(!value && selected != null && node != null);
        choose.setAlpha(choose.isEnabled() ? 1f : 0.5f);
        send.setAlpha(send.isEnabled() ? 1f : 0.5f);
        address.setEnabled(!value);
        for (int i = 0; i < peers.getChildCount(); i++) peers.getChildAt(i).setEnabled(!value);
        transferAction.setEnabled(!preparing);
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
                        finishTransfer(true, "Приём завершён", "Файл получен и проверен. Сохраните копию в удобную папку.", file);
                        setBusy(busy());
                    });
                }
                @Override public void failed(String error) {
                    ui(() -> {
                        if (!receiving) return;
                        receiving = false;
                        finishTransfer(false, "Передача не завершена", "Ошибка: " + error, null);
                        setBusy(busy());
                    });
                }
            });
            node = started;
            if (destroyed) { started.close(); return; }
            ui(() -> {
                String endpoint = "IP: " + Discovery.addresses() + "\nПорт: " + started.port();
                local.setText(endpoint);
                receiveAddress.setText(endpoint);
                receiveTitle.setText("Готовы к приёму");
                homeStatus.setText("●  Приёмник готов · локальная сеть");
                setBusy(busy());
            });
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi != null) {
                multicast = wifi.createMulticastLock("transportfiles-discovery");
                multicast.setReferenceCounted(false);
                multicast.acquire();
            }
            discovery = new Discovery(started.port(), android.os.Build.MODEL,
                    list -> ui(() -> updatePeers(list)), error -> ui(() -> peerHint.setText(error)));
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
        LinearLayout request = column();
        request.setPadding(dp(24), dp(24), dp(24), dp(8));
        ImageView icon = image(request, R.drawable.receive_file, 48);
        icon.setColorFilter(GREEN);
        text(request, "Входящий файл", 24, INK).setTypeface(null, Typeface.BOLD);
        text(request, "Отправитель: " + offer.sender(), 15, MUTED);
        text(request, offer.filename(), 18, INK).setTypeface(null, Typeface.BOLD);
        text(request, formatSize(offer.size()), 15, MUTED);
        text(request, "После приёма можно выбрать папку для сохранения копии.", 14, MUTED);
        ScrollView requestScroll = new ScrollView(this);
        requestScroll.addView(request);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(requestScroll)
                .setNegativeButton("Отклонить", (d, which) -> answer.complete(null))
                .setPositiveButton("Принять", (d, which) -> {
                    if (answer.isDone()) return;
                    try {
                        Path inbox = getFilesDir().toPath().resolve("received");
                        Files.createDirectories(inbox);
                        Path directory = Files.createDirectory(inbox.resolve(UUID.randomUUID().toString()));
                        receiving = true;
                        lastProgress.set(0);
                        beginTransfer("Приём файла", offer.filename(), true);
                        answer.complete(directory.resolve(offer.filename()));
                    } catch (IOException e) { answer.completeExceptionally(e); }
                }).setOnCancelListener(d -> answer.complete(null)).create();
        answer.whenComplete((path, error) -> ui(() -> {
            dialog.dismiss();
            if (pending == answer) pending = null;
            if (path == null) message("Запрос отклонён, отменён или истёк");
            setBusy(busy());
        }));
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(rounded(0xffffffff, 24));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(GREEN);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(RED);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == OPEN) {
            if (busy()) { message("Сначала завершите текущую передачу"); return; }
            preparing = true;
            setBusy(true);
            fileLabel.setText("Подготовка выбранного файла…");
            message("Подготавливаем файл. Это может занять немного времени.");
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
                notice.setVisibility(View.GONE);
                setBusy(busy());
            });
        } catch (Exception e) {
            if (staged != null) deleteStaged(staged);
            ui(() -> { preparing = false; fileLabel.setText(selectedName == null ? "Пока ничего не выбрано" : selectedName); message("Ошибка чтения: " + e.getMessage()); setBusy(busy()); });
        }
    }

    private void send() {
        if (busy() || selected == null) return;
        try {
            Discovery.Peer peer = address.getText().toString().trim().isEmpty()
                    ? selectedPeer : Discovery.endpoint(address.getText().toString());
            if (peer == null) { message("Выберите устройство или укажите IP:порт"); return; }
            TransferClient transfer = new TransferClient(peer.host(), peer.port());
            client = transfer;
            Path file = selected;
            String name = selectedName;
            setBusy(true);
            beginTransfer("Отправка файла", name, false);
            lastProgress.set(0);
            progress.setProgress(0);
            worker.execute(() -> {
                String result;
                boolean succeeded = false;
                try (transfer) {
                    transfer.send(file, name, android.os.Build.MODEL,
                            () -> ui(() -> {
                                if (client != transfer) return;
                                percent.setText("Отправляем…");
                                status.setText("Сохраняйте соединение до подтверждения результата");
                            }), this::showProgress);
                    succeeded = true;
                    result = "Файл доставлен и проверен получателем";
                } catch (Exception e) { result = "Ошибка: " + e.getMessage(); }
                String finalResult = result;
                boolean success = succeeded;
                ui(() -> {
                    if (client != transfer) return;
                    client = null;
                    finishTransfer(success, success ? "Отправка завершена" : "Передача не завершена", finalResult, null);
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
            if (!receiving && client == null) return;
            progress.setIndeterminate(false);
            progress.setProgress(total == 0 ? 1000 : (int) (1000.0 * bytes / total));
            percent.setText(bytes == total ? "Проверяем файл…" : (total == 0 ? 100 : (int) (100.0 * bytes / total)) + " %");
            status.setText(formatSize(bytes) + " из " + formatSize(total) + (bytes == total ? " · ждём подтверждения" : ""));
        });
    }

    private void cancel() {
        if (preparing) { message("Подготовка документа ещё выполняется"); return; }
        receiving = false;
        if (client != null) { client.close(); client = null; }
        if (pending != null) pending.complete(null);
        if (node != null) node.cancelIncoming();
        finishTransfer(false, "Передача отменена", "Передача остановлена", null);
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
            exportFile(files.get(index).toPath());
        }).setNegativeButton("Закрыть", null).show();
    }

    private void exportFile(Path file) {
        if (file == null) return;
        exporting = file;
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, file.getFileName().toString()), SAVE);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f КБ", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.1f МБ", bytes / (1024.0 * 1024));
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
