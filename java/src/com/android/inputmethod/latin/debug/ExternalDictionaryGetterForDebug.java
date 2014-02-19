/*
 * Copyright (C) 2013 The Android Open Source Project
 * Modifications (C) 2014 The NamelessRom Project
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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.makedict.FormatSpec.FileHeader;
import com.android.inputmethod.latin.utils.DictionaryInfoUtils;
import com.android.inputmethod.latin.utils.LocaleUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.Locale;

/**
 * A class to read a local file as a dictionary for debugging purposes.
 */
public class ExternalDictionaryGetterForDebug extends Activity {
    public static final String SOURCE_FOLDER = Environment.getExternalStorageDirectory().getPath()
            + "/Nameless/LatinIME";

    private static final String URL_PREFIX
            = "http://sourceforge.net/projects/namelessrom/files/romextras/dictionaries/";
    private static final String URL_SUFFIX = "/download";

    private static final String[] fileNames = new String[]{
            "main_bg.dict", "main_cs.dict", "main_da.dict", "main_de.dict",
            "main_el.dict", "main_en.dict", "main_en_gb.dict", "main_en_us.dict",
            "main_es.dict", "main_fi.dict", "main_fr.dict", "main_hr.dict",
            "main_hu.dict", "main_it.dict", "main_iw.dict", "main_ka.dict",
            "main_lt.dict", "main_lv.dict", "main_nb.dict", "main_nl.dict",
            "main_pl.dict", "main_pt_br.dict", "main_pt_pt.dict", "main_ru.dict",
            "main_sl.dict", "main_sr.dict", "main_sv.dict", "main_tr.dict"
    };

    private static final Integer[] localeNames = new Integer[]{
            R.string.dict_main_bg, R.string.dict_main_cs, R.string.dict_main_da,
            R.string.dict_main_de, R.string.dict_main_el, R.string.dict_main_en,
            R.string.dict_main_en_gb, R.string.dict_main_en_us, R.string.dict_main_es,
            R.string.dict_main_fi, R.string.dict_main_fr, R.string.dict_main_hr,
            R.string.dict_main_hu, R.string.dict_main_it, R.string.dict_main_iw,
            R.string.dict_main_ka, R.string.dict_main_lt, R.string.dict_main_lv,
            R.string.dict_main_nb, R.string.dict_main_nl, R.string.dict_main_pl,
            R.string.dict_main_pt_br, R.string.dict_main_pt_pt, R.string.dict_main_ru,
            R.string.dict_main_sl, R.string.dict_main_sr, R.string.dict_main_sv,
            R.string.dict_main_tr
    };

    private ListView mListView;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_dict_import);

        mListView = (ListView) findViewById(R.id.dict_listview);

        final CustomArrayAdapter mAdapter = new CustomArrayAdapter(this, localeNames);
        mListView.setAdapter(mAdapter);

        final ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {

        final int id = menuItem.getItemId();
        switch (id) {
            case android.R.id.home:
                onBackPressed(); // We need that to get back to our wizards
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    /**
     * Shows a dialog which offers the user to install the external dictionary.
     */
    private void askInstallFile(final Context context, final String dirPath,
                                final String fileName, final Runnable completeRunnable) {
        final File file = new File(dirPath, fileName);
        final FileHeader header = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(file);
        final String locale = header.getLocaleString();
        String languageName;
        if (locale != null && !locale.isEmpty()) {
            languageName = LocaleUtils.constructLocaleFromString(locale)
                    .getDisplayName(Locale.getDefault());
        } else {
            languageName = "UNKNOWN";
        }
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
                installFile(context, file, header, fileName);
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

    private void askUninstallFile(final Context context, final String dirPath,
                                  final String fileName, final String localeName) {
        final String title = String.format(
                context.getString(R.string.import_external_dictionary_confirm_uninstall_title));
        final String message = String.format(
                context.getString(R.string.import_external_dictionary_confirm_uninstall_message),
                localeName);

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                }).setPositiveButton(android.R.string.ok, new OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                final File f = new File(dirPath, fileName);
                if (f.delete()) {
                    Toast.makeText(context, R.string.dict_action_uninstall_success,
                            Toast.LENGTH_SHORT).show();
                }
                refreshListView();
                dialog.dismiss();
            }
        }).create().show();
    }

    private void installFile(final Context context, final File file,
                             final FileHeader header, final String fileName) {
        boolean error = false;

        FileInputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            final String locale = header.getLocaleString().toLowerCase();
            final File finalFile = new File(getFilesDir() + File.separator
                    + "dicts" + File.separator + locale, fileName);
            final File parentDir = new File(getFilesDir() + File.separator
                    + "dicts" + File.separator + locale);

            if (!parentDir.exists() || !parentDir.isDirectory()) {
                if (!parentDir.mkdirs()) {
                    throw new IOException("Could not create directories!");
                }
            }
            finalFile.delete();

            inputStream = new FileInputStream(file);
            outputStream = new FileOutputStream(finalFile);

            FileChannel inChannel = inputStream.getChannel();
            FileChannel outChannel = outputStream.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);

            if (!finalFile.exists()) {
                throw new IOException("Can't move the file to its final name: "
                        + finalFile.getAbsolutePath());
            }
        } catch (IOException e) {
            error = true;
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
        } finally {
            try {
                if (null != outputStream) outputStream.close();
                if (null != inputStream) inputStream.close();
            } catch (IOException e) {
                // Don't do anything
            }
        }

        if (!error) {
            Toast.makeText(context,
                    R.string.dict_action_install_success, Toast.LENGTH_SHORT).show();
        }

        refreshListView();
    }

    private void refreshListView() {
        mListView.invalidateViews();
    }

    private class CustomArrayAdapter extends ArrayAdapter<Integer> {
        private final Context context;

        public CustomArrayAdapter(Context context, Integer[] values) {
            super(context, R.layout.list_item_dict, values);
            this.context = context;
        }

        private class ViewHolder {
            private final TextView dictName;
            private final Button dictAction;

            private ViewHolder(View rootView) {
                dictName = (TextView) rootView.findViewById(R.id.dict_name);
                dictAction = (Button) rootView.findViewById(R.id.dict_action);
            }

        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View v = inflater.inflate(R.layout.list_item_dict, parent, false);

            ViewHolder holder = (ViewHolder) v.getTag();
            if (holder == null) {
                holder = new ViewHolder(v);
                v.setTag(holder);
            }
            final String fileName = fileNames[position];
            final String fileCleanName = getString(localeNames[position]);
            final String url = URL_PREFIX + fileName + URL_SUFFIX;
            final boolean fileExists = new File(SOURCE_FOLDER, fileName).exists();
            final String locale = fileName.replace("main_", "").replace(".dict", "").toLowerCase();
            final String filePath = getFilesDir() + File.separator
                    + "dicts" + File.separator + locale;
            final boolean isInstalled = new File(filePath, fileName).exists();

            holder.dictName.setText(fileCleanName);

            holder.dictAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (isInstalled) {
                        askUninstallFile(ExternalDictionaryGetterForDebug.this, filePath, fileName,
                                fileCleanName);
                    } else if (!fileExists) {
                        askDownloadFile(url, fileName, fileCleanName);
                    } else {
                        askInstallFile(ExternalDictionaryGetterForDebug.this, SOURCE_FOLDER, fileName,
                                null /* completeRunnable */);
                    }
                }
            });
            holder.dictAction.setText(isInstalled
                    ? R.string.dict_action_uninstall
                    : (fileExists
                    ? R.string.dict_action_install
                    : R.string.dict_action_download));

            return v;
        }
    }

    private void askDownloadFile(final String url, final String fileName
            , final String fileCleanName) {
        final Context context = ExternalDictionaryGetterForDebug.this;
        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        dialog.setTitle(getString(R.string.dict_dialog_download_title, fileCleanName));
        dialog.setMessage(getString(R.string.dict_dialog_download_message, fileCleanName));
        dialog.setNegativeButton(android.R.string.no, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        dialog.setPositiveButton(android.R.string.yes, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                new DownloadTask(ExternalDictionaryGetterForDebug.this)
                        .execute(url, fileName);
            }
        });
        dialog.show();
    }

    private class DownloadTask extends AsyncTask<String, Integer, String> {

        private String filePath = "";
        private String fileName = "";
        private Context mContext;
        private ProgressDialog mDialog;
        private boolean mError = false;

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
                    mError = true;
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }

                input = connection.getInputStream();
                output = new FileOutputStream(filePath);

                byte data[] = new byte[4096];
                int count;
                while ((count = input.read(data)) != -1) {
                    if (isCancelled()) {
                        mError = true;
                        return null;
                    }
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                mError = true;
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
            if (!filePath.isEmpty() && !mError) {
                if (mListView != null) {
                    mListView.invalidateViews();
                    askInstallFile(mContext, SOURCE_FOLDER, fileName, null);
                }
            } else {
                Toast.makeText(mContext
                        , mContext.getString(R.string.dialog_download_oops, SOURCE_FOLDER
                        + File.separator + fileName)
                        , Toast.LENGTH_LONG).show();
            }
        }
    }
}
