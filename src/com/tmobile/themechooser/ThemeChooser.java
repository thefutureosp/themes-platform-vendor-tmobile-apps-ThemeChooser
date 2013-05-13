/*
 * Copyright (C) 2010, T-Mobile USA, Inc.
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

package com.tmobile.themechooser;

import java.util.HashMap;

import com.tmobile.themes.ThemeManager;
import com.tmobile.themes.provider.ThemeItem;
import com.tmobile.themes.provider.Themes;
import com.tmobile.themes.widget.ThemeAdapter;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CoverFlow;

import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

public class ThemeChooser extends Activity implements OnClickListener,
        OnItemSelectedListener {
    private static final String TAG = ThemeChooser.class.getSimpleName();

    private CoverFlow mFlow;
    private TextView mThemeNameView;
    private Button mUninstall;

    private ThemeChooserAdapter mAdapter;

    private static final int MENU_ABOUT = Menu.FIRST;

    private static final int DIALOG_APPLY = 0;
    private static final int DIALOG_MISSING_HOST_DENSITY = 1;
    private static final int DIALOG_MISSING_THEME_PACKAGE_SCOPE = 2;
    private final ChangeThemeHelper mChangeHelper = new ChangeThemeHelper(this,
            DIALOG_APPLY);

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Uri currentTheme = getIntent().getParcelableExtra(
                ThemeManager.EXTRA_THEME_EXISTING_URI);
        mAdapter = new ThemeChooserAdapter(this);
        mAdapter.setUseAutomaticMarking(true, currentTheme);

        inflateActivity();

        mFlow.setSelection(mAdapter.getMarkedPosition());
        mChangeHelper.dispatchOnCreate();
        setupActionBar();
    }

    private void setupActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP,
                ActionBar.DISPLAY_HOME_AS_UP);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int selectedPos = mFlow.getSelectedItemPosition();
        ThemeItem theme = (ThemeItem)mFlow.getItemAtPosition(selectedPos);
        switch (item.getItemId()) {
            case android.R.id.home:
                // app icon in action bar clicked;
                finish();
                break;
            case MENU_ABOUT:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                String packageName = theme.getPackageName();
                if (packageName.isEmpty()) packageName = "com.android.systemui";
                builder.setMessage(getString(R.string.theme_current_name) + " " + theme.getName()
                        + "\n" + getString(R.string.theme_current_author) + " " + theme.getAuthor()
                        + "\n" + getString(R.string.theme_current_package) + " " + packageName)
                       .setTitle(R.string.menu_about);
                AlertDialog dialog = builder.create();
                dialog.show();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
         menu.add(Menu.NONE, MENU_ABOUT, 0, R.string.menu_about)
                 .setIcon(android.R.drawable.ic_menu_info_details)
                 .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
         return super.onCreateOptionsMenu(menu);
    }

    private void inflateActivity() {
        setContentView(R.layout.theme_chooser);

        mThemeNameView = (TextView) findViewById(R.id.theme_name);

        mFlow = (CoverFlow) findViewById(R.id.theme_content);
        mFlow.setAdapter(mAdapter);
        mFlow.setOnItemSelectedListener(this);
        mFlow.setCoverflowStyle(true);
        mFlow.setRadius(600);
        mFlow.setClipChildren(false);

        Button apply = (Button) findViewById(R.id.apply_button);
        apply.setOnClickListener(this);

        mUninstall = (Button) findViewById(R.id.uninstall_button);
        mUninstall.setOnClickListener(this);

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Handle config changes ourselves, to avoid possible race condition
        // during theme change when app gets torn down and rebuilt due
        // to orientation change.
        boolean finishing = mChangeHelper
                .dispatchOnConfigurationChanged(newConfig);

        // If it's an orientation change and not a theme change,
        // re-inflate ThemeChooser with its new resources
        if (!finishing) {
            // re-inflating will cause our list positions and selections
            // to be lost, so request all Views in the window save their
            // instance state first.
            Bundle state = new Bundle();
            onSaveInstanceState(state);

            // Set the adapter null, so that on reinflating mGallery,
            // the previous mDataSetObserver gets unregistered, and we
            // don't leak a reference to the gallery on each config change.
            mFlow.setAdapter(null);
            inflateActivity();

            // Now have window restore previous instance state... just as
            // though it went through onDestroy/onCreate process.
            onRestoreInstanceState(state);
        }
    }

    @Override
    protected void onResume() {
        mChangeHelper.dispatchOnResume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        mChangeHelper.dispatchOnPause();
        super.onPause();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder builder;
        switch (id) {
        case DIALOG_MISSING_HOST_DENSITY:
            builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.dialog_theme_error_title);
            builder.setMessage(R.string.dialog_missing_host_density_msg);
            builder.setPositiveButton(R.string.dialog_apply_anyway_btn,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            int selectedPos = mFlow.getSelectedItemPosition();
                            ThemeItem item = (ThemeItem) mFlow
                                    .getItemAtPosition(selectedPos);
                            doApply(item);
                        }
                    });
            builder.setNegativeButton(R.string.dialog_bummer_btn, null);
            return builder.create();
        case DIALOG_MISSING_THEME_PACKAGE_SCOPE:
            builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.dialog_theme_error_title);
            builder.setMessage(R.string.dialog_missing_theme_package_scope_msg);
            builder.setPositiveButton(android.R.string.ok, null);
            return builder.create();
        default:
            return mChangeHelper.dispatchOnCreateDialog(id);
        }
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        mChangeHelper.dispatchOnPrepareDialog(id, dialog);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position,
            long id) {

        ThemeItem item = (ThemeItem) parent.getItemAtPosition(position);

        String text = item.getName();
        if (mAdapter.getMarkedPosition() == position) {
            text = getString(R.string.theme_current, text);
        }
        mThemeNameView.setText(text);
        if (text.equals("System") || text.equals("System (current)")
                || text.equals("Paradigm") || text.equals("Paradigm (current)")) {
            mUninstall.setEnabled(false);
        } else {
            mUninstall.setEnabled(true);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public void onClick(View v) {

        int selectedPos = mFlow.getSelectedItemPosition();
        ThemeItem item = (ThemeItem) mFlow.getItemAtPosition(selectedPos);

        switch (v.getId()) {
        case R.id.apply_button:
            if (!item.hasHostDensity()) {
                showDialog(DIALOG_MISSING_HOST_DENSITY);
                return;
            }
            if (!item.hasThemePackageScope()) {
                // showDialog(DIALOG_MISSING_THEME_PACKAGE_SCOPE);
                // return;
            }
            if (Intent.ACTION_PICK.equals(getIntent().getAction())) {
                Intent i = new Intent(null, item.getUri(ThemeChooser.this));
                setResult(Activity.RESULT_OK, i);
                finish();
            } else {
                doApply(item);
            }
            break;
        case R.id.uninstall_button:
            Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
            uninstallIntent.setData(Uri.parse("package:"
                    + item.getPackageName()));
            uninstallIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(uninstallIntent);
            break;
        }

    }

    private void doApply(ThemeItem item) {
        Uri uri = item.getUri(ThemeChooser.this);
        Log.i(TAG, "Sending request to change to '" + item.getName() + "' ("
                + uri + ")");
        mChangeHelper.beginChange(item.getName());
        if (getResources().getBoolean(R.bool.config_change_style_only)) {
            Themes.changeStyle(ThemeChooser.this, uri);
        } else {
            Themes.changeTheme(ThemeChooser.this, uri);
        }
    }

    private float dpToPx(float dp) {
        Resources r = getResources();
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                r.getDisplayMetrics());
        return px;
    }

    private static class ThemeChooserAdapter extends ThemeAdapter {

        private LayoutInflater mInflater;
        private HashMap<String, Bitmap> mBitmaps;

        public ThemeChooserAdapter(Activity context) {
            super(context);
            mInflater = LayoutInflater.from(context);
            mBitmaps = new HashMap<String, Bitmap>();
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View row = mInflater.inflate(R.layout.theme_item, parent, false);
            row.setTag(new ViewHolder(row));
            return row;
        }

        @SuppressWarnings("deprecation")
        @Override
        public void bindView(View view, Context context, Cursor cursor) {

            ThemeItem themeItem = mDAOItem;
            ViewHolder holder = (ViewHolder) view.getTag();

            // int orientation =
            // context.getResources().getConfiguration().orientation;
            // holder.preview.setImageURI(themeItem.getPreviewUri(orientation));
            String hash = themeItem.getPreviewHash();

            if (mBitmaps.containsKey(hash)) {
                holder.preview.setImageBitmap(mBitmaps.get(hash));
            } else {
                Log.w("Theme","Hit else");
                // make sure it is not null to fix bug caused by a shitty theme
                // provider
                if (hash == null || hash.isEmpty()) {// holder.preview.getDrawable()==null){
                    // TODO: find a more suitable image
                    holder.preview
                            .setImageResource(android.R.drawable.ic_delete);
                } else {
                    byte[] bitmap = Base64.decode(hash, 0);
                    holder.preview.setImageBitmap(BitmapFactory
                            .decodeByteArray(bitmap, 0, bitmap.length));
                }
                // The gap we want between the reflection and the original
                // image
                final int reflectionGap = 4;

                Bitmap originalImage = ((BitmapDrawable) holder.preview
                        .getDrawable()).getBitmap();
                int width = originalImage.getWidth();
                int height = originalImage.getHeight();

                // This will not scale but will flip on the Y axis
                Matrix matrix = new Matrix();
                matrix.preScale(1, -1);
                // Create a Bitmap with the flip matrix applied to it.
                // We only want the bottom half of the image
                Bitmap reflectionImage = Bitmap.createBitmap(originalImage, 0,
                        height / 2, width, height / 2, matrix, false);

                // Create a new bitmap with same width but taller to fit
                // reflection
                Bitmap bitmapWithReflection = Bitmap.createBitmap(width,
                        (height + height / 5), Config.ARGB_8888);

                // Create a new Canvas with the bitmap that's big enough for
                // the image plus gap plus reflection
                Canvas canvas = new Canvas(bitmapWithReflection);
                // Draw in the original image
                canvas.drawBitmap(originalImage, 0, 0, null);
                // Draw in the gap
                Paint deafaultPaint = new Paint();
                canvas.drawRect(0, height, width, height + reflectionGap,
                        deafaultPaint);
                // Draw in the reflection
                canvas.drawBitmap(reflectionImage, 0, height + reflectionGap,
                        null);

                // Create a shader that is a linear gradient that covers the
                // reflection
                Paint paint = new Paint();
                LinearGradient shader = new LinearGradient(0,
                        originalImage.getHeight(), 0,
                        bitmapWithReflection.getHeight() + reflectionGap,
                        0x70ffffff, 0x00ffffff, TileMode.CLAMP);
                // Set the paint to use this shader (linear gradient)
                paint.setShader(shader);
                // Set the Transfer mode to be porter duff and destination
                // in
                paint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
                // Draw a rectangle using the paint with our linear gradient
                canvas.drawRect(0, height, width,
                        bitmapWithReflection.getHeight() + reflectionGap, paint);

                holder.preview.setImageBitmap(bitmapWithReflection);
                mBitmaps.put(hash, bitmapWithReflection);

            }

            holder.preview.setScaleType(ScaleType.FIT_CENTER);

        }

        @Override
        public Object getItem(int position) {
            return getDAOItem(position);
        }
    }

    private static class ViewHolder {
        public ImageView preview;

        public ViewHolder(View row) {
            preview = (ImageView) row;
        }
    }

}
