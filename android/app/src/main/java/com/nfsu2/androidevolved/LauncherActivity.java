package com.nfsu2.androidevolved;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Launcher in the style of the MW and Carbon ports: game files and JUGAR on the left, options on the
 * right (Vulkan driver, resolution, frame cap, widescreen, compatibility).
 */
public final class LauncherActivity extends Activity {
    private static final int REQUEST_DRIVER_ZIP = 45, REQUEST_DRIVER_PROBE = 46;
    // NFSU2 green.
    private static final int ACCENT = 0xFF8EE03A;
    /** {label, guest language name, LANGUAGES/<file>.bin}. */
    private static final String[][] LANGUAGES = {
        {"Español", "Spanish", "Spanish"}, {"English", "English UK", "English"},
        {"Français", "French", "French"}, {"Deutsch", "German", "German"},
        {"Italiano", "Italian", "Italian"}, {"Nederlands", "Dutch", "Dutch"},
        {"Svenska", "Swedish", "Swedish"}, {"Dansk", "Danish", "Danish"},
        {"日本語", "Japanese", "Japanese"}, {"한국어", "Korean", "Korean"},
        {"繁體中文", "Chinese (Traditional)", "Chinese"}, {"ไทย", "Thai", "Thailand"}
    };

    private LinearLayout checksList, optionsList;
    private TextView status;
    private Button play, access;
    private boolean playAfterProbe, checkingUpdates, resumed;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(buildScreen());
        checkUpdates(false);
        String updated = ReleaseUpdates.pendingWhatsNew(this);
        if (updated != null) showWhatsNew(updated);
    }

    /** First launch after an update: the installed version's release notes from GitHub. */
    private void showWhatsNew(String version) {
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Diperbarui ke versi " + version + "!")
                .setMessage("Memuat berita…")
                .setPositiveButton("Menerima", (d, which) -> ReleaseUpdates.whatsNewSeen(this))
                .setNeutralButton("Lihat di GitHub", (d, which) -> {
                    ReleaseUpdates.whatsNewSeen(this);
                    openUrl(Uri.parse("https://github.com/codepdbh/nfsu2-recompiled/releases/tag/v" + Uri.encode(version)));
                })
                .setOnCancelListener(d -> ReleaseUpdates.whatsNewSeen(this))
                .show();
        ReleaseUpdates.releaseNotes(this, version, notes -> {
            if (!dialog.isShowing()) return;
            // In the app, skip the download/install preamble of the release page.
            int start = notes == null ? -1 : notes.indexOf("Berita");
            if (start > 0) notes = notes.substring(start);
            dialog.setMessage(notes != null ? notes
                    : "Berita tidak dapat dimuat (offline atau belum dipublikasikan). Anda dapat melihatnya di GitHub.");
        });
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        refresh();
        String crash = Diagnostics.unreportedCrash(this);
        if (crash != null) {
            Diagnostics.acknowledgeCrashes(this);
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Pertandingan ditutup").setMessage(crash + ".\n\nApakah Anda ingin memasukkan seluruh log ke GitHub agar dapat diperbaiki?")
                    .setPositiveButton("Laporkan di GitHub", (dialog, which) -> prepareReport(crash))
                    .setNegativeButton("Tidak sekarang", null).show();
        }
    }

    @Override protected void onPause() {
        resumed = false;
        super.onPause();
    }

    // ---- Screen ------------------------------------------------------------------------------------------

    private View buildScreen() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF060D08, 0xFF0B2210, 0xFF060D08}));
        LinearLayout columns = new LinearLayout(this);
        columns.setPadding(dp(24), dp(10), dp(24), dp(10));
        screen.addView(columns, new FrameLayout.LayoutParams(-1, -1));

        // Left: logo, game files and JUGAR.
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        ScrollView leftScroll = new ScrollView(this);
        leftScroll.setFillViewport(true);
        leftScroll.addView(left);
        columns.addView(leftScroll, new LinearLayout.LayoutParams(0, -1, 1.1f));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.icono);
        logo.setAdjustViewBounds(true);
        header.addView(logo, new LinearLayout.LayoutParams(dp(76), dp(76)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        titles.addView(label("Need for Speed Underground 2", 18, Color.WHITE, true));
        titles.addView(label("PC · dikompilasi ulang secara asli untuk Android", 12, 0x99FFFFFF, false));
        header.addView(titles);
        left.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout card = card();
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(8);
        left.addView(card, cardParams);
        card.addView(label("FILE PERMAINAN", 12, ACCENT, true));
        status = label("", 13, 0xDDFFFFFF, false);
        status.setPadding(0, dp(4), 0, dp(6));
        card.addView(status);
        checksList = new LinearLayout(this);
        checksList.setOrientation(LinearLayout.VERTICAL);
        card.addView(checksList);

        play = actionButton("BERMAIN", true);
        play.setOnClickListener(view -> play());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(-1, dp(54));
        playParams.topMargin = dp(8);
        left.addView(play, playParams);

        access = actionButton("Izinkan akses ke folder nfsu2", false);
        access.setOnClickListener(view -> requestAccess());
        LinearLayout.LayoutParams accessParams = new LinearLayout.LayoutParams(-1, dp(46));
        accessParams.topMargin = dp(10);
        left.addView(access, accessParams);

        Button channel = actionButton("Saluran Pengembang Resmi", false);
        channel.setOnClickListener(view -> openChannel());
        LinearLayout.LayoutParams channelParams = new LinearLayout.LayoutParams(-1, dp(46));
        channelParams.topMargin = dp(10);
        left.addView(channel, channelParams);

        // Right: options.
        LinearLayout right = card();
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -1, 1f);
        rightParams.leftMargin = dp(24);
        columns.addView(right, rightParams);
        right.addView(label("OPSI", 12, ACCENT, true));
        TextView note = label("Mereka diterapkan saat Anda menekan MAINKAN.", 12, 0x88FFFFFF, false);
        note.setPadding(0, dp(2), 0, dp(4));
        right.addView(note);
        ScrollView scroll = new ScrollView(this);
        optionsList = new LinearLayout(this);
        optionsList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(optionsList);
        right.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return screen;
    }

    private void refresh() {
        boolean allowed = allowed();
        File root = gameRoot();
        checksList.removeAllViews();
        boolean exe = allowed && new File(root, "SPEED2.EXE").isFile();
        addCheck("SPEED2.EXE", exe, true);
        StringBuilder missing = new StringBuilder();
        for (String folder : new String[] {"GLOBAL", "TRACKS", "CARS", "FRONTEND", "LANGUAGES"})
            if (!(allowed && new File(root, folder).isDirectory())) missing.append(missing.length() == 0 ? "" : ", ").append(folder);
        addCheck(missing.length() == 0 ? "Folder permainan" : "Folder tidak ada: " + missing, missing.length() == 0, true);
        addCheck("HUD panorama, opsional (scripts/…WidescreenFix.dat)",
                allowed && new File(root, "scripts/NFSUnderground2.WidescreenFix.dat").isFile(), false);
        // Without it the game has no text: JUGAR stays off, so say which file is missing.
        boolean language = allowed && !languages().isEmpty();
        if (exe && !language) addCheck("Kekurangan LANGUAGES/<bahasa>.bin", false, true);
        boolean ready = exe && language;
        status.setText(!allowed ? "Memungkinkan akses ke file untuk dibaca storage/nfsu2."
                : ready ? root.getAbsolutePath() : "Salin file game ke storage/nfsu2 atau /storage/<volume>/nfsu2.");
        play.setEnabled(ready);
        play.setAlpha(ready ? 1f : .45f);
        access.setVisibility(allowed ? View.GONE : View.VISIBLE);
        refreshOptions();
    }

    private void refreshOptions() {
        optionsList.removeAllViews();
        GpuDrivers.Driver driver = GpuDrivers.selected(this);
        // Custom drivers (libadrenotools) exist only in the 64-bit build.
        if (android.os.Process.is64Bit())
            optionsList.addView(optionRow("Driver Vulkan", driver == null ? "Del sistema" : driver.name, this::chooseDriver));
        optionsList.addView(optionRow("Probar driver", "GPU y funciones Vulkan", () -> probeDriver(false)));
        optionsList.addView(optionRow("Idioma", languageLabel(), this::chooseLanguage));
        optionsList.addView(optionRow("Reiniciar caché gráfica", "Regenerar shaders", () -> GraphicsCache.prompt(this)));
        for (GameOptions.Option option : GameOptions.ALL)
            optionsList.addView(optionRow(option.title, option.label(GameOptions.get(this, option.key)), () -> chooseOption(option)));
        optionsList.addView(optionRow("Servidor online", GameOptions.onlineServer(this), this::chooseOnlineServer));
        optionsList.addView(optionRow("Buscar actualizaciones",
                checkingUpdates ? "Memeriksa GitHub…" : "Versi " + ReleaseUpdates.installed(this), () -> checkUpdates(true)));
        optionsList.addView(optionRow("Laporkan masalah", "Masuk lengkap ke GitHub", () -> prepareReport(null)));
    }

    // ---- Options ---------------------------------------------------------------------------------------

    private List<String[]> languages() {
        List<String[]> found = new ArrayList<>();
        File root = gameRoot();
        for (String[] language : LANGUAGES)
            if (new File(root, "LANGUAGES/" + language[2] + ".bin").isFile()) found.add(language);
        return found;
    }

    private String languageLabel() {
        String current = GameOptions.language(this);
        for (String[] language : LANGUAGES) if (language[1].equals(current)) return language[0];
        return current;
    }

    private void chooseLanguage() {
        List<String[]> found = allowed() ? languages() : new ArrayList<>();
        if (found.isEmpty()) { message("Bahasa", "Tidak ada bahasa yang ditemukan di folder nfsu2/LANGUAGES."); return; }
        String[] names = new String[found.size()];
        int checked = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = found.get(i)[0];
            if (found.get(i)[1].equals(GameOptions.language(this))) checked = i;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Bahasa")
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    GameOptions.prefs(this).edit().putString(GameOptions.LANGUAGE, found.get(which)[1]).apply();
                    dialog.dismiss(); refreshOptions();
                }).setNegativeButton("Membatalkan", null).show();
    }

    private void chooseOption(GameOptions.Option option) {
        String current = GameOptions.get(this, option.key);
        int checked = 0;
        for (int i = 0; i < option.values.length; i++) if (option.values[i].equals(current)) checked = i;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(option.title)
                .setSingleChoiceItems(option.labels, checked, (dialog, which) -> {
                    GameOptions.set(this, option.key, option.values[which]);
                    dialog.dismiss(); refreshOptions();
                }).setNegativeButton("Membatalkan", null).show();
    }

    private void chooseOnlineServer() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setText(GameOptions.onlineServer(this));
        input.setSelection(input.getText().length());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Server daring")
                .setMessage("Mode online game (EA Nation) terhubung ke server komunitas ini, bukan ke server EA yang sudah ditutup.")
                .setView(box)
                .setPositiveButton("Menyimpan", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!GameOptions.validServer(value)) { message("Server daring", "\"" + value + "\" bukan nama server yang valid."); return; }
                    GameOptions.prefs(this).edit().putString(GameOptions.ONLINE_SERVER, value).apply();
                    refreshOptions();
                })
                .setNeutralButton("Memulihkan", (dialog, which) -> {
                    GameOptions.prefs(this).edit().remove(GameOptions.ONLINE_SERVER).apply();
                    refreshOptions();
                })
                .setNegativeButton("Membatalkan", null).show();
    }

    /** Title of the driver dialog: this phone's GPU and the driver that suits it (GpuAdvice). */
    private android.view.View driverAdviceTitle() {
        String renderer = GpuAdvice.renderer(this);
        float density = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = Math.round(22 * density);
        box.setPadding(padding, padding, padding, Math.round(6 * density));
        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("Driver Vulkan");
        title.setTextSize(20);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        android.widget.TextView advice = new android.widget.TextView(this);
        advice.setText((renderer.isEmpty() ? "" : "GPU: " + renderer + "\n") + GpuAdvice.advice(GpuAdvice.family(renderer)));
        advice.setTextSize(14);
        advice.setTextColor(0xCCFFFFFF);
        advice.setPadding(0, Math.round(8 * density), 0, 0);
        box.addView(title);
        box.addView(advice);
        return box;
    }

    private void chooseDriver() {
        List<GpuDrivers.Driver> drivers = GpuDrivers.list(this);
        String[] names = new String[drivers.size() + 1];
        names[0] = "Driver Sistem";
        int selected = 0;
        for (int i = 0; i < drivers.size(); ++i) {
            names[i + 1] = drivers.get(i).name;
            if (drivers.get(i).id.equals(GpuDrivers.selectedId(this))) selected = i + 1;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setCustomTitle(driverAdviceTitle())
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    GpuDrivers.select(this, which == 0 ? "" : drivers.get(which - 1).id);
                    refreshOptions(); dialog.dismiss();
                    String renderer = GpuAdvice.renderer(this);
                    if (which > 0 && GpuAdvice.mismatched(GpuAdvice.family(renderer), drivers.get(which - 1).name))
                        message("Driver Vulkan", String.format(java.util.Locale.ROOT, "Driver ini bukan untuk GPU Anda (%s): permainan mungkin tidak dapat dimulai. Pilih driver sistem atau yang dibuat untuk GPU Anda.", renderer));
                })
                .setPositiveButton("Impor ZIP", (dialog, which) -> startActivityForResult(
                        new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),
                        REQUEST_DRIVER_ZIP))
                .setNeutralButton("Hapus yang dipilih", (dialog, which) -> {
                    GpuDrivers.Driver driver = GpuDrivers.selected(this);
                    if (driver == null) return;
                    try { GpuDrivers.remove(this, driver); refreshOptions(); }
                    catch (java.io.IOException error) { message("Driver Vulkan", error.getMessage()); }
                })
                .setNegativeButton("Kembali", null).show();
    }

    private void probeDriver(boolean thenPlay) {
        playAfterProbe = thenPlay;
        play.setEnabled(false);
        startActivityForResult(new Intent(this, GpuProbeActivity.class), REQUEST_DRIVER_PROBE);
    }

    private void showDriverReport(String report) {
        play.setEnabled(true);
        try {
            org.json.JSONObject gpu = new org.json.JSONObject(report);
            String probeError = gpu.optString("probeError", "");
            boolean loaderFailed = gpu.optBoolean("driverLoadFailed") || !probeError.isEmpty();
            boolean deviceFailed = gpu.optBoolean("deviceCreateFailed")
                    || (gpu.has("logicalDeviceResult") && gpu.optInt("logicalDeviceResult") != 0);
            boolean failed = loaderFailed || deviceFailed;
            if (playAfterProbe && !failed && gpu.optBoolean("compatible")) { playAfterProbe = false; launch(); return; }
            // The player pressed PLAY: a driver that loads but lacks a feature the
            // probe checks may still run the game (issue #1107), so offer it.
            boolean wantedToPlay = playAfterProbe && !failed;
            playAfterProbe = false;
            String text;
            if (loaderFailed) {
                text = "Vulkan tidak dapat dimulai: "
                        + (probeError.isEmpty() ? "kesalahan memuat driver." : probeError);
            } else {
                StringBuilder details = new StringBuilder("GPU: ").append(gpu.optString("gpu", "desconocida"))
                        .append("\nVulkan: ").append(gpu.optString("vulkan", "desconocido"))
                        .append("\nDriver: ").append(gpu.optString("driverName", "—"))
                        .append(' ').append(gpu.optString("driverInfo", ""));
                if (deviceFailed) details.append("\nGagal membuat perangkat Vulkan (VkResult ")
                        .append(gpu.optInt("logicalDeviceResult")).append(").");
                details.append("\nTexturas BC: ").append(gpu.optBoolean("bcTextures") ? "dan GPU" : "menjadi CPUnya");
                if (!gpu.optString("vertexFormatsMissing").isEmpty())
                    details.append("\nJenis Vertex yang Tidak Didukung: ").append(gpu.optString("vertexFormatsMissing"));
                org.json.JSONArray missing = gpu.optJSONArray("missing");
                details.append("\n").append(gpu.optBoolean("compatible") && !deviceFailed
                        ? "Kompatibel dengan permainan."
                        : "Fitur hilang: " + (missing == null ? "tidak ada detailnya" : missing.toString()));
                text = details.toString();
            }
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Tes pengemudi Vulkan").setMessage(text).setPositiveButton("Menerima", null)
                    .setNeutralButton("Gunakan driver sistem", (dialog, which) -> { GpuDrivers.select(this, ""); refreshOptions(); })
                    .setNegativeButton(wantedToPlay ? "Mainkan juga" : null, wantedToPlay ? (dialog, which) -> launch() : null)
                    .show();
        } catch (Exception error) { message("Tes pengemudi Vulkan", "Diagnosa tidak valid: " + error.getMessage()); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DRIVER_PROBE) {
            showDriverReport(resultCode == RESULT_OK && data != null ? data.getStringExtra("gpu_report")
                    : "{\"probeError\":\"Tes ditutup atau dibatalkan.\",\"driverLoadFailed\":true}");
        } else if (requestCode == REQUEST_DRIVER_ZIP && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri zip = data.getData();
            status.setText("Import the Vulkan driver…");
            worker.execute(() -> {
                try {
                    GpuDrivers.Driver driver = GpuDrivers.importZip(this, zip);
                    main.post(() -> {
                        GpuDrivers.select(this, driver.id); refresh();
                        status.setText("Pengemudi yang diimpor: " + driver.name + ". Gunakan Test Driver untuk memeriksanya.");
                    });
                } catch (Exception error) {
                    main.post(() -> { refresh(); message("Driver Vulkan", "Tidak dapat mengimpor driver: " + error.getMessage()); });
                }
            });
        }
    }

    // ---- Updates and reports ---------------------------------------------------------------------------

    private void checkUpdates(boolean manual) {
        if (checkingUpdates) return;
        checkingUpdates = true;
        if (optionsList != null) refreshOptions();
        ReleaseUpdates.check(this, manual, result -> {
            checkingUpdates = false;
            if (isFinishing() || isDestroyed()) return;
            refreshOptions();
            if (result.error != null) { if (manual) message("Pembaruan", result.error); return; }
            if (!ReleaseVersion.newer(result.tag, ReleaseUpdates.installed(this))) {
                if (manual) message("Pembaruan", "apakah kamu punya versinya " + ReleaseUpdates.installed(this)
                        + ". Tidak ada versi yang lebih baru di GitHub.");
                return;
            }
            android.content.SharedPreferences updates = ReleaseUpdates.prefs(this);
            if (!manual && result.tag.equals(updates.getString("notified_tag", ""))) return;
            updates.edit().putString("notified_tag", result.tag).apply();
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Versi baru tersedia: " + result.tag)
                    .setMessage("Anda dapat mengunduhnya dari GitHub atau terus memainkannya dengan versi ini. Instal APK di atas "
                            + "dari yang sekarang untuk mempertahankan permainan dan pengaturan Anda.")
                    .setPositiveButton("Memperbarui", (dialog, which) -> openUrl(Uri.parse(result.url)))
                    .setNegativeButton("Nanti", null).show();
        });
    }

    /**
     * Builds the report ZIP, saves it to Download/NFSU2 and opens a pre-filled GitHub issue. GitHub
     * cannot take an attachment through the link, so the issue already carries a technical summary
     * and the player is told where the ZIP is to attach it.
     */
    private void prepareReport(String crash) {
        status.setText("Mempersiapkan pendaftaran…");
        worker.execute(() -> {
            try {
                File report = Diagnostics.createReport(this);
                String location = Diagnostics.saveToDownloads(this, report);
                Uri issue = Diagnostics.issueUri(this, crash, location);
                main.post(() -> {
                    refresh();
                    new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("Laporkan di GitHub")
                            .setMessage("Catatan lengkap telah disimpan:\n" + location
                                    + "\n\nMasalah ini akan dibuka di GitHub dengan ringkasan teknis yang sudah ditulis. Jelaskan apa yang terjadi dan, "
                                    + "jika bisa, lampirkan ZIP itu (GitHub tidak mengizinkan lampiran otomatis).")
                            .setPositiveButton("Buka GitHub", (dialog, which) -> openUrl(issue))
                            .setNegativeButton("Membatalkan", null).show();
                });
            } catch (Exception error) {
                main.post(() -> { refresh(); message("Laporkan masalah", "Pendaftaran tidak dapat disiapkan: " + error.getMessage()); });
            }
        });
    }

    private void openUrl(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (android.content.ActivityNotFoundException error) { message("GitHub", "Tidak ada browser untuk dibuka " + uri); }
    }

    // ---- Launch ----------------------------------------------------------------------------------------

    private void play() {
        if (suggestLighterSettings()) return;
        if (android.os.Process.is64Bit() && GpuDrivers.selected(this) != null) probeDriver(true);
        else launch();
    }

    // SoCs whose GPU cannot run the game at 75-100% resolution: PowerVR BXM/GE83xx,
    // Mali-G52/G57 MC1-2, Adreno 610/613/619. Moto g54 reports (MT6855) showed lag and
    // audio cuts at native resolution. Asked once; the options stay the player's.
    private static final String[] WEAK_GPU_SOCS = {"MT6855", "MT6765", "MT6762", "MT6768", "MT6769", "MT6785",
            "MT6789", "MT6833", "MT6835", "T606", "T612", "T616", "T618", "UMS9230", "SC9863", "SM6225", "SM4350",
            "SM4450", "SM6115"};

    private boolean suggestLighterSettings() {
        if (Build.VERSION.SDK_INT < 31 || GameOptions.prefs(this).getBoolean("light_settings_asked", false)) return false;
        String soc = Build.SOC_MODEL == null ? "" : Build.SOC_MODEL.toUpperCase(java.util.Locale.ROOT);
        boolean weak = false;
        for (String model : WEAK_GPU_SOCS) if (soc.contains(model)) weak = true;
        String resolution = GameOptions.get(this, GameOptions.RESOLUTION);
        if (!weak || !(resolution.equals("100") || resolution.equals("75"))) return false;
        GameOptions.prefs(this).edit().putBoolean("light_settings_asked", true).apply();
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Pengaturan untuk ponsel Anda")
                .setMessage("GPU ponsel ini tidak mencapai resolusi asli atau 75%: game berjalan lambat dan suara terputus-putus. Gunakan resolusi pada 50%? Anda dapat mengubahnya nanti di Opsi.")
                .setPositiveButton("Gunakan sedikit penyesuaian", (dialog, which) -> {
                    GameOptions.prefs(this).edit().putString(GameOptions.RESOLUTION, "50").apply();
                    refreshOptions();
                    play();
                })
                .setNegativeButton("bermain seperti ini", (dialog, which) -> play())
                .show();
        return true;
    }

    private void launch() {
        // Each launch uses a fresh guest machine in the dedicated :game process.
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<android.app.ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes != null) for (android.app.ActivityManager.RunningAppProcessInfo process : processes)
            if (process.uid == android.os.Process.myUid() && process.processName.equals(getPackageName() + ":game"))
                android.os.Process.killProcess(process.pid);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int[] size = GameOptions.renderSize(this, Math.max(metrics.widthPixels, metrics.heightPixels),
                Math.min(metrics.widthPixels, metrics.heightPixels));
        String[] loader = GpuDrivers.loaderArguments(this);
        startActivity(new Intent(this, GameActivity.class)
                .putExtra("language", GameOptions.language(this))
                .putExtra("renderWidth", size[0]).putExtra("renderHeight", size[1])
                .putExtra("frameCap", Integer.parseInt(GameOptions.get(this, GameOptions.FPS)))
                .putExtra("widescreen", GameOptions.get(this, GameOptions.WIDESCREEN).equals("on"))
                .putExtra("minimapTop", GameOptions.get(this, GameOptions.MINIMAP).equals("top"))
                .putExtra("backBuffers", Integer.parseInt(GameOptions.get(this, GameOptions.LATENCY)))
                .putExtra("cpuTextures", GameOptions.get(this, GameOptions.TEXTURES).equals("cpu"))
                .putExtra("onlineServer", GameOptions.onlineServer(this))
                .putExtra("gameRoot", gameRoot().getAbsolutePath())
                .putExtra("driverLoader", loader));
    }

    // ---- Storage ---------------------------------------------------------------------------------------

    private File gameRoot() {
        File primary = new File(Environment.getExternalStorageDirectory(), "nfsu2");
        if (completeGameRoot(primary)) return primary;

        File[] appDirectories = getExternalFilesDirs(null);
        if (appDirectories != null) {
            for (File appDirectory : appDirectories) {
                File volumeRoot = externalVolumeRoot(appDirectory);
                if (volumeRoot == null || volumeRoot.equals(Environment.getExternalStorageDirectory())) continue;
                File candidate = new File(volumeRoot, "nfsu2");
                if (completeGameRoot(candidate)) return candidate;
            }
        }

        File storage = new File("/storage");
        File[] volumes = storage.listFiles(File::isDirectory);
        if (volumes != null) {
            java.util.Arrays.sort(volumes, (left, right) -> left.getAbsolutePath().compareTo(right.getAbsolutePath()));
            for (File volume : volumes) {
                if ("emulated".equals(volume.getName())) continue;
                File candidate = new File(volume, "nfsu2");
                if (completeGameRoot(candidate)) return candidate;
            }
        }
        return primary;
    }

    private File externalVolumeRoot(File appDirectory) {
        if (appDirectory == null) return null;
        File root = appDirectory;
        String[] expectedParents = {"files", getPackageName(), "data", "Android"};
        for (String expected : expectedParents) {
            if (root == null || !expected.equals(root.getName())) return null;
            root = root.getParentFile();
        }
        return root;
    }

    private static boolean completeGameRoot(File root) {
        if (!new File(root, "SPEED2.EXE").isFile()) return false;
        for (String folder : new String[] {"CARS", "CREDITS", "FRONTEND", "GLOBAL", "LANGUAGES",
                "MOVIES", "NIS", "SDATA", "SOUND", "SUBTITLES", "TRACKS"}) {
            File directory = new File(root, folder);
            String[] entries = directory.list();
            if (!directory.isDirectory() || entries == null || entries.length == 0) return false;
        }
        return true;
    }

    private boolean allowed() {
        return Build.VERSION.SDK_INT >= 30 ? Environment.isExternalStorageManager()
            : checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAccess() {
        if (Build.VERSION.SDK_INT >= 30)
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
        else requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        refresh();
    }

    @Override protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    // ---- Widgets ---------------------------------------------------------------------------------------

    private void addCheck(String name, boolean ok, boolean required) {
        TextView row = label((ok ? "✓  " : required ? "✗  " : "!  ") + name + (ok || required ? "" : " · opcional"), 13,
                ok ? 0xFF7CD992 : required ? 0xFFFF6B6B : 0xFFFFC857, false);
        row.setPadding(0, dp(2), 0, dp(2));
        checksList.addView(row);
    }

    private View optionRow(String title, String value, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x168EE03A);
        bg.setCornerRadius(dp(10));
        row.setBackground(bg);
        row.setClickable(true);
        row.setOnClickListener(v -> onClick.run());
        row.addView(label(title, 14, Color.WHITE, false), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView current = label(value + "  ›", 13, ACCENT, true);
        current.setGravity(Gravity.END);
        row.addView(current, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(5);
        row.setLayoutParams(lp);
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC0E1A12);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), 0x448EE03A);
        card.setBackground(bg);
        return card;
    }

    // The ports' official developer channel.
    static final String CHANNEL = "https://www.youtube.com/@daniel54bhp";
    private void openChannel() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(CHANNEL))); }
        catch (android.content.ActivityNotFoundException e) { android.widget.Toast.makeText(this, CHANNEL, android.widget.Toast.LENGTH_LONG).show(); }
    }

    private Button actionButton(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(primary ? 0xFF0A1C05 : Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, primary ? 22 : 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setLetterSpacing(primary ? .12f : 0f);
        GradientDrawable bg = primary
                ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xFFB8F25A, 0xFF3FB82A})
                : new GradientDrawable();
        if (!primary) { bg.setColor(0x22FFFFFF); bg.setStroke(dp(1), 0x55FFFFFF); }
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        b.setStateListAnimator(null);
        return b;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) { t.setTypeface(Typeface.DEFAULT_BOLD); t.setLetterSpacing(.06f); }
        return t;
    }

    private void message(String title, String text) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title).setMessage(text).setPositiveButton("Menerima", null).show();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
