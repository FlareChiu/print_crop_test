package com.example.testpeprintcrop;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.trello.rxlifecycle.components.RxActivity;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MainActivity extends RxActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String KEY_STATE = "tpc:state";
    private static final String KEY_INTENT = "tpc:intent";
    private static final String KEY_MIN_WIDTH = "tpc:min_width";
    private static final String KEY_MIN_HEIGHT = "tpc:min_height";

    private static final int REQUEST_PICK_IMAGE = 0;
    private static final int REQUEST_CROP = 1;
    private static final int REQUEST_APP_DETAIL_SETTING = 2;

    private static final String[] INTENTS = {
            "com.htc.pe.intent.action.EDIT_PRINT",
            Intent.ACTION_EDIT,
            "com.htc.photoenhancer.action.THEME_CROP",
            "com.android.camera.action.CROP"
    };

    private String mOutputPath = "/mnt/sdcard/Android/data/xxx.jpg";

    private TextView mText;
    private TextView mResultText;
    private EditText mMinWidthText, mMinHeightText;
    private Spinner mIntentSpinner;

    private State mState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View pickerButton = findViewById(R.id.pick);
        pickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                photoPickerIntent.setType("image/*");
                startActivityForResult(photoPickerIntent, REQUEST_PICK_IMAGE);
            }
        });

        mIntentSpinner = (Spinner) findViewById(R.id.intentAction);
        mIntentSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.spinner_item, INTENTS));

        View sendButton = findViewById(R.id.send);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendIntent((String) mIntentSpinner.getSelectedItem());
            }
        });

        mText = (TextView) findViewById(R.id.srcUri);
        mResultText = (TextView) findViewById(R.id.resultInfo);
        mMinWidthText = (EditText) findViewById(R.id.minWidth);
        mMinHeightText = (EditText) findViewById(R.id.minHeight);

        if (savedInstanceState != null) {
            mState = savedInstanceState.getParcelable(KEY_STATE);

            if (mState.data != null) {
                loadBitmapObservable(mState);
            }
        } else {
            mState = new State();
            mState.cropRect = new Rect(0, 0, 0, 0);

            SharedPreferences setting = PreferenceManager.getDefaultSharedPreferences(this);
            String intentAction = setting.getString(KEY_INTENT, INTENTS[0]);
            for (int i = 0; i < INTENTS.length; i++) {
                if (INTENTS[i].equals(intentAction)) {
                    mIntentSpinner.setSelection(i);
                }
            }
            int minWidth = setting.getInt(KEY_MIN_WIDTH, 856);
            int minHeight = setting.getInt(KEY_MIN_HEIGHT, 925);
            mMinWidthText.setText(Integer.toString(minWidth));
            mMinHeightText.setText(Integer.toString(minHeight));

            getFragmentManager().beginTransaction()
                    .replace(R.id.fragment_image, ImageFragment.newInstance(false), ImageFragment.TAG)
                    .commit();
        }
    }

    private void sendIntent(String intentAction) {
        Intent intent = new Intent();
        intent.setDataAndType(mState.data, "image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        intent.setAction(intentAction);

        int minWidth = 0;
        int minHeight = 0;
        try {
            minWidth = Integer.valueOf(mMinWidthText.getText().toString());
            minHeight = Integer.valueOf(mMinHeightText.getText().toString());
        } catch (NumberFormatException ex) {
            // expected
        }

        intent.putExtra("minOutputX", minWidth);
        intent.putExtra("minOutputY", minHeight);
        intent.putExtra("externalGivenOutputPath", mOutputPath);
        // Added in v2
        intent.putExtra("rectIn", mState.cropRect);
        startActivityForResult(intent, REQUEST_CROP);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(KEY_STATE, mState);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor settingsEditor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        boolean hasValues = false;

        String intentAction = (String) mIntentSpinner.getSelectedItem();
        if (!TextUtils.isEmpty(intentAction)) {
            settingsEditor.putString(KEY_INTENT, intentAction);
            hasValues = true;
        }

        try {
            int minWidth = Integer.valueOf(mMinWidthText.getText().toString());
            settingsEditor.putInt(KEY_MIN_WIDTH, minWidth);
            hasValues = true;
        } catch (NumberFormatException ex) {
            notifyError(ex);
        }

        try {
            int minHeight = Integer.valueOf(mMinHeightText.getText().toString());
            settingsEditor.putInt(KEY_MIN_HEIGHT, minHeight);
            hasValues = true;
        } catch (NumberFormatException ex) {
            notifyError(ex);
        }

        if (hasValues) {
            settingsEditor.apply();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode != RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case REQUEST_PICK_IMAGE:
                mState.dataPath = null;
                mState.data = intent.getData();
                mText.setText(mState.data.toString());

                ImageFragment imageFragment = (ImageFragment) getFragmentManager().findFragmentByTag(ImageFragment.TAG);
                if (imageFragment == null) {
                    imageFragment = ImageFragment.newInstance(true);
                    getFragmentManager().beginTransaction()
                            .replace(R.id.fragment_image, imageFragment, ImageFragment.TAG)
                            .commitAllowingStateLoss();
                } else {
                    imageFragment.startLoading();
                }

                loadBitmapObservable(mState);
                break;

            case REQUEST_CROP:
                Rect cropRect = intent.getParcelableExtra("rectOut");
                if (cropRect != null) { // v2
                    mState.cropRect = cropRect;
                    String savedPath = intent.getStringExtra("filePath");
                    if (savedPath != null) {
                        mState.dataPath = savedPath;
                        mState.data = Uri.fromFile(new File(savedPath));
                        mText.setText(mState.data.toString());

                        loadBitmapObservable(mState);
                    }
                    mResultText.setText("Rect: " + cropRect + ", W x H = (" + cropRect.width() + ", " + cropRect.height() + ")");
                } else { // v1
                    String savedPath = intent.getStringExtra("externalGivenOutputPath");
                    if (savedPath != null) {
                        mState.dataPath = savedPath;
                        mState.data = Uri.fromFile(new File(savedPath));
                        mText.setText(mState.data.toString());

                        loadBitmapObservable(mState, true);
                        mResultText.setText("Loading...");
                    } else {
                        mResultText.setText("Null result");
                    }
                }
                break;

            case REQUEST_APP_DETAIL_SETTING:
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
                    if (getFragmentManager().findFragmentByTag(ImageNoPermissionFragment.TAG) != null) {
                        getFragmentManager().beginTransaction()
                                .replace(R.id.fragment_image, ImageFragment.newInstance(true), ImageFragment.TAG)
                                .commit();

                        loadBitmapObservable(mState);
                    }
                }
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.fragment_image, ImageFragment.newInstance(true), ImageFragment.TAG)
                    .commitAllowingStateLoss();

            loadBitmapObservable(mState);
        }
    }

    // Button callback in ImageNoPermissionFragment
    public void onTurnOnInSettingClick(View view) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_APP_DETAIL_SETTING);
    }

    // Button callback in ImageNoPermissionFragment
    public void onTryRequestClick(View view) {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
    }

    public Bitmap loadBitmap(Uri uri, String path) throws IOException, SecurityException {
        if (uri == null) {
            if (path == null) {
                return null;
            }

            uri = Uri.fromFile(new File(path));
        }

        Bitmap bitmap = null;
        InputStream is = null;
        try {
            Context context = this;
            is = context.getContentResolver().openInputStream(uri);
            bitmap = BitmapFactory.decodeStream(is);
            if (bitmap == null) {
                return null;
            }

            if (path == null) {
                path = queryPathFromUri(context, uri);
            }

            if (path != null) {
                int degree = readExifDegree(path);
                if (degree != 0) {
                    Bitmap rotated = createRotatedBitmap(bitmap, degree);
                    if (rotated != null) {
                        return rotated;
                    }
                }
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ex) {
                    // ignore
                }
            }
        }

        return bitmap;
    }

    private static String queryPathFromUri(Context context, Uri uri) {
        String scheme = uri.getScheme();
        if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            return uri.getPath();
        }

        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = context.getContentResolver().query(uri, proj, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }

    private static int readExifDegree(String path) throws IOException {
        ExifInterface exif = new ExifInterface(path);
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    private static Bitmap createRotatedBitmap(Bitmap source, float degree) {
        Matrix matrix = new Matrix();
        matrix.setRotate(degree);

        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private static class State implements Parcelable {
        public Uri data;
        public String dataPath;
        // Added in v2
        public Rect cropRect;

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(data, 0);
            dest.writeString(dataPath);
            dest.writeParcelable(cropRect, 0);
        }

        public static final Creator<State> CREATOR = new Creator<State>() {
            public State createFromParcel(Parcel p) {
                State state = new State();
                state.data = p.readParcelable(Uri.class.getClassLoader());
                state.dataPath = p.readString();
                state.cropRect = p.readParcelable(Rect.class.getClassLoader());
                return state;
            }

            public State[] newArray(int size) {
                return new State[size];
            }
        };
    }

    private void notifyError(Throwable throwable) {
        Log.e(TAG, "print-crop-test", throwable);
        Toast.makeText(getApplicationContext(), throwable.toString(), Toast.LENGTH_LONG).show();
    }

    private void loadBitmapObservable(State state) {
        loadBitmapObservable(state, false);
    }

    private void loadBitmapObservable(final State state, boolean showBitmapDimension) {
        Observable
                .just(state)
                .flatMap(new Func1<State, Observable<Bitmap>>() {
                    @Override
                    public Observable<Bitmap> call(State s) {
                        Uri uri = s.data;
                        String path = s.dataPath;
                        try {
                            Bitmap bitmap = loadBitmap(uri, path);
                            return Observable.just(bitmap);
                        } catch (IOException e) {
                            return Observable.error(e);
                        }
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(this.<Bitmap>bindToLifecycle())
                .subscribe(new BitmapSubscriber(showBitmapDimension));
    }

    private class BitmapSubscriber extends Subscriber<Bitmap> {
        private boolean mShowBitmapDimension;

        public BitmapSubscriber(boolean showBitmapDimension) {
            mShowBitmapDimension = showBitmapDimension;
        }

        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {
            // Permission denied
            if (e instanceof SecurityException) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    if (getFragmentManager().findFragmentByTag(ImageNoPermissionFragment.TAG) == null) {
                        getFragmentManager().beginTransaction()
                                .replace(R.id.fragment_image, ImageNoPermissionFragment.newInstance(),
                                        ImageNoPermissionFragment.TAG)
                                .commitAllowingStateLoss();

                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
                    }

                    return;
                }
            }

            MainActivity.this.notifyError(e);
        }

        @Override
        public void onNext(Bitmap bitmap) {
            ImageFragment imageFragment = (ImageFragment) getFragmentManager().findFragmentByTag(ImageFragment.TAG);
            if (imageFragment != null) {
                imageFragment.setImageBitmap(bitmap);
            }

            if (mShowBitmapDimension) {
                mResultText.setText("W x H = (" + bitmap.getWidth() + ", " + bitmap.getHeight() + ")");
            }
        }
    }
}
