/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.latin.debug;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;

import com.android.inputmethod.latin.BinaryDictionaryFileDumper;
import com.android.inputmethod.latin.BinaryDictionaryGetter;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.DictionaryInfoUtils;
import com.android.inputmethod.latin.utils.LocaleUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

/**
 * A class to read a local file as a dictionary for debugging purposes.
 */
public class ExternalDictionaryGetterForDebug {
    public static final String SOURCE_FOLDER = Environment.getExternalStorageDirectory().getPath()
            + "/Nameless/LatinIME";

    private static final String URL_PREFIX
            = "http://sourceforge.net/projects/namelessrom/files/romextras/dictionaries/";
    private static final String URL_SUFFIX = "/download";

    private static String[] findDictionariesInTheDownloadedFolder() {
        final String[] fileNames = new String[]{
                "main_bg.dict", "main_cs.dict", "main_da.dict", "main_de.dict",
                "main_el.dict", "main_en.dict", "main_es.dict", "main_fi.dict",
                "main_fr.dict", "main_hr.dict", "main_hu.dict", "main_it.dict",
                "main_iw.dict", "main_ka.dict", "main_nb.dict", "main_nl.dict",
                "main_pt_br.dict", "main_pt_pt.dict", "main_ru.dict", "main_sv.dict"
        };
        return fileNames;
    }

    public static void chooseAndInstallDictionary(final Context context) {
        final String[] fileNames = findDictionariesInTheDownloadedFolder();
        showChooseFileDialog(context, fileNames);
    }

    private static void showChooseFileDialog(final Context context, final String[] fileNames) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.import_external_dictionary_multiple_files_title)
                .setItems(fileNames, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        final String fileName = fileNames[which];
                        final String url = URL_PREFIX + fileName + URL_SUFFIX;
                        if (!new File(SOURCE_FOLDER, fileName).exists()) {
                            new DownloadTask(context).execute(url, fileName);
                        } else {
                            askInstallFile(context, SOURCE_FOLDER, fileName,
                                    null /* completeRunnable */);
                        }
                    }
                })
                .create().show();
    }

    /**
     * Shows a dialog which offers the user to install the external dictionary.
     */
    public static void askInstallFile(final Context context, final String dirPath,
                                      final String fileName, final Runnable completeRunnable) {
        final File file = new File(dirPath, fileName.toString());
        final FileHeader header = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(file);
        final String locale = header.getLocaleString();
        final String languageName = LocaleUtils.constructLocaleFromString(locale)
                .getDisplayName(Locale.getDefault());
        final String title = String.format(
                context.getString(R.string.import_external_dictionary_confirm_install_title));
        final String message = String.format(
                context.getString(R.string.import_external_dictionary_confirm_install_message),
                languageName);
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                        if (completeRunnable != null) {
                            completeRunnable.run();
                        }
                    }
                }).setPositiveButton(android.R.string.ok, new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                installFile(context, file, header);
                dialog.dismiss();
                if (completeRunnable != null) {
                    completeRunnable.run();
                }
            }
        }).setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                // Canceled by the user by hitting the back key
                if (completeRunnable != null) {
                    completeRunnable.run();
                }
            }
        }).create().show();
    }

    private static void installFile(final Context context, final File file,
                                    final FileHeader header) {
        BufferedOutputStream outputStream = null;
        File tempFile = null;
        try {
            final String locale = header.getLocaleString();
            // Create the id for a main dictionary for this locale
            final String id = BinaryDictionaryGetter.MAIN_DICTIONARY_CATEGORY
                    + BinaryDictionaryGetter.ID_CATEGORY_SEPARATOR + locale;
            final String finalFileName = DictionaryInfoUtils.getCacheFileName(id, locale, context);
            final String tempFileName = BinaryDictionaryGetter.getTempFileName(id, context);
            tempFile = new File(tempFileName);
            tempFile.delete();
            outputStream = new BufferedOutputStream(new FileOutputStream(tempFile));
            final BufferedInputStream bufferedStream = new BufferedInputStream(
                    new FileInputStream(file));
            BinaryDictionaryFileDumper.checkMagicAndCopyFileTo(bufferedStream, outputStream);
            outputStream.flush();
            final File finalFile = new File(finalFileName);
            finalFile.delete();
            if (!tempFile.renameTo(finalFile)) {
                throw new IOException("Can't move the file to its final name");
            }
        } catch (IOException e) {
            // There was an error: show a dialog
            new AlertDialog.Builder(context)
                    .setTitle(R.string.error)
                    .setMessage(e.toString())
                    .setPositiveButton(android.R.string.ok, new OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            dialog.dismiss();
                        }
                    }).create().show();
            return;
        } finally {
            try {
                if (null != outputStream) outputStream.close();
                if (null != tempFile) tempFile.delete();
            } catch (IOException e) {
                // Don't do anything
            }
        }
    }

    private static class DownloadTask extends AsyncTask<String, Integer, String> {

        private String filePath = "";
        private String fileName = "";
        private Context mContext;
        private ProgressDialog mDialog;

        private DownloadTask(Context context) {
            mContext = context;
        }


        @Override
        protected void onPreExecute() {
            mDialog = new ProgressDialog(mContext);
            mDialog.setTitle(R.string.dialog_download_title);
            mDialog.setMessage(mContext.getString(R.string.dialog_download_message));
            mDialog.setCancelable(true);
            mDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mDialog.show();
        }

        @Override
        protected String doInBackground(final String... pParams) {

            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                final URL url = new URL(pParams[0]);
                Log.v("LatinIME", "Url: " + url);
                fileName = pParams[1];
                filePath = SOURCE_FOLDER + File.separator + fileName;
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }

                input = connection.getInputStream();
                output = new FileOutputStream(filePath);

                byte data[] = new byte[4096];
                int count;
                while ((count = input.read(data)) != -1) {
                    if (isCancelled()) {
                        return null;
                    }
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                Log.e("LatinIME", "Error while downloading: " + e.toString());
            } finally {
                try {
                    if (output != null) {
                        output.close();
                    }
                    if (input != null) {
                        input.close();
                    }
                } catch (IOException ignored) {
                }

                if (connection != null) {
                    connection.disconnect();
                }
            }
            return null;
        }

        @Override
        protected void onCancelled(String s) {
            mDialog.dismiss();
        }

        @Override
        protected void onPostExecute(String s) {
            mDialog.dismiss();
            if (!filePath.isEmpty()) {
                Log.v("LatinIME", "Source: " + SOURCE_FOLDER + " | fileName: " + fileName);
                askInstallFile(mContext, SOURCE_FOLDER, fileName, null /* completeRunnable */);
            }
        }
    }
}
