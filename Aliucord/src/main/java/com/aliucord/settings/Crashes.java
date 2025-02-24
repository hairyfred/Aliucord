/*
 * This file is part of Aliucord, an Android Discord client mod.
 * Copyright (c) 2021 Juby210 & Vendicated
 * Licensed under the Open Software License version 3.0
 */

package com.aliucord.settings;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.aliucord.Constants;
import com.aliucord.Utils;
import com.aliucord.fragments.SettingsPage;
import com.aliucord.utils.DimenUtils;
import com.aliucord.utils.MDUtils;
import com.aliucord.views.DangerButton;
import com.aliucord.views.ToolbarButton;
import com.lytefast.flexinput.R;

import java.io.*;
import java.util.*;

public class Crashes extends SettingsPage {
    private static final int uniqueId = View.generateViewId();

    private static class CrashLog {
        public String timestamp;
        public String stacktrace;
        public int times;
    }

    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("SetTextI18n")
    public void onViewBound(View view) {
        super.onViewBound(view);
        setActionBarTitle("Crash Logs");

        var context = view.getContext();
        int padding = DimenUtils.getDefaultPadding();
        int p = padding / 2;

        File dir = new File(Constants.CRASHLOGS_PATH);
        File[] files = dir.listFiles();
        boolean hasCrashes = files != null && files.length > 0;

        if (getHeaderBar().findViewById(uniqueId) == null) {
            ToolbarButton crashFolderBtn = new ToolbarButton(context);
            crashFolderBtn.setId(uniqueId);
            ToolbarButton clearLogsBtn = new ToolbarButton(context);

            Toolbar.LayoutParams crashFolderBtnParams = new Toolbar.LayoutParams(Toolbar.LayoutParams.WRAP_CONTENT, Toolbar.LayoutParams.WRAP_CONTENT);
            crashFolderBtnParams.gravity = Gravity.END;
            crashFolderBtnParams.setMarginEnd(p);
            crashFolderBtn.setLayoutParams(crashFolderBtnParams);
            Toolbar.LayoutParams clearLogsParams = new Toolbar.LayoutParams(Toolbar.LayoutParams.WRAP_CONTENT, Toolbar.LayoutParams.WRAP_CONTENT);
            clearLogsParams.gravity = Gravity.END;
            clearLogsBtn.setLayoutParams(clearLogsParams);
            crashFolderBtn.setPadding(p, p, p, p);
            clearLogsBtn.setPadding(p, p, p, p);

            crashFolderBtn.setImageDrawable(ContextCompat.getDrawable(context, R.d.ic_open_in_new_white_24dp));
            //noinspection ConstantConditions
            Drawable clearLogs = ContextCompat.getDrawable(context, R.d.ic_delete_white_24dp).mutate();
            Utils.tintToTheme(clearLogs).setAlpha(hasCrashes ? 185 : 92);
            clearLogsBtn.setImageDrawable(clearLogs, false);
            clearLogsBtn.setClickable(hasCrashes);

            crashFolderBtn.setOnClickListener(e -> {
                if (!dir.exists() && !dir.mkdir()) {
                    Utils.showToast(context, "Failed to create crashlogs directory!", true);
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(Constants.CRASHLOGS_PATH), "resource/folder");
                startActivity(Intent.createChooser(intent, "Open folder"));
            });

            clearLogsBtn.setOnClickListener(e -> {
                for (File file : files) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
                clearLogs.setAlpha(92);
                clearLogsBtn.setImageDrawable(clearLogs);
                clearLogsBtn.setClickable(false);
                reRender();
            });

            addHeaderButton(crashFolderBtn);
            addHeaderButton(clearLogsBtn);
        }

        Map<Integer, CrashLog> crashes = getCrashes();
        if (crashes == null || crashes.size() == 0) {
            TextView header = new TextView(context, null, 0, R.h.UiKit_Settings_Item_Header);
            header.setAllCaps(false);
            header.setText("Woah, no crashes :O");
            header.setTypeface(ResourcesCompat.getFont(context, Constants.Fonts.whitney_semibold));
            header.setGravity(Gravity.CENTER);

            DangerButton crashBtn = new DangerButton(context);
            crashBtn.setText("LET'S CHANGE THAT");
            crashBtn.setPadding(p, p, p, p);
            crashBtn.setOnClickListener(e -> {
                throw new RuntimeException("You fool...");
            });

            addView(header);
            addView(crashBtn);
        } else {
            TextView hint = new TextView(context, null, 0, R.h.UiKit_Settings_Item_SubText);
            hint.setText("Hint: Crashlogs are accesible via your file explorer at Aliucord/crashlogs");
            hint.setTypeface(ResourcesCompat.getFont(context, Constants.Fonts.whitney_medium));
            hint.setGravity(Gravity.CENTER);
            addView(hint);

            for (CrashLog crash : crashes.values()) {
                TextView header = new TextView(context, null, 0, R.h.UiKit_Settings_Item_Header);
                header.setText(crash.timestamp + (crash.times > 1 ? " (" + crash.times + ")" : ""));
                header.setTypeface(ResourcesCompat.getFont(context, Constants.Fonts.whitney_semibold));

                TextView body = new TextView(context);
                body.setText(MDUtils.renderCodeBlock(context, new SpannableStringBuilder(), null, crash.stacktrace));
                body.setOnClickListener(e -> {
                    Utils.setClipboard("CrashLog-" + crash.timestamp, crash.stacktrace);
                    Utils.showToast(context, "Copied to clipboard");
                });

                addView(header);
                addView(body);
            }
        }
    }

    @Nullable
    private Map<Integer, CrashLog> getCrashes() {
        File folder = new File(Constants.BASE_PATH, "crashlogs");
        File[] files = folder.listFiles();
        if (files == null) return null;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());

        Map<Integer, CrashLog> res = new HashMap<>();
        for (File file : files) {
            if (!file.isFile()) continue;
            int length;
            try { length = Math.toIntExact(file.length()); } catch (ArithmeticException ex) { continue; }
            byte[] buffer = new byte[length];
            try (FileInputStream is = new FileInputStream(file)) {
                //noinspection ResultOfMethodCallIgnored
                is.read(buffer);
            } catch (IOException ignored) {}
            String content = new String(buffer);

            int hashCode = content.hashCode();
            CrashLog existing = res.get(hashCode);
            if (existing != null) {
                existing.times += 1;
            } else {
                CrashLog crash = new CrashLog();
                crash.timestamp = file.getName().replace(".txt", "").replaceAll("_", ":");
                crash.stacktrace = content;
                crash.times = 1;
                res.put(hashCode, crash);
            }
        }
        return res;
    }
}
