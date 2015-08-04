package com.example.testpeprintcrop;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
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
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements LoaderManager.LoaderCallbacks<Bitmap> {

    private static final String KEY_STATE = "tpc:state";

    private String mOutputPath = "/mnt/sdcard/Android/data/xxx.jpg";

    private TextView mText;
    private ImageView mImageView;
    private TextView mResultText;
    private EditText mMinWidthText, mMinHeightText;

    private State mState;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        View pickerButon = findViewById(R.id.pick);
        pickerButon.setOnClickListener(new View.OnClickListener() {            
            @Override
            public void onClick(View v) {
                Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                photoPickerIntent.setType("image/*");
                startActivityForResult(photoPickerIntent, 0);
            }
        });
        
        View cropButton = findViewById(R.id.crop);
        cropButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startEdit();
            }
        });
        
        mText = (TextView) findViewById(R.id.srcUri);
        mImageView = (ImageView) findViewById(R.id.imageView1);
        mResultText = (TextView) findViewById(R.id.resultInfo);
        mMinWidthText = (EditText) findViewById(R.id.minWidth);
        mMinHeightText = (EditText) findViewById(R.id.minHeight);

        if (savedInstanceState != null) {
            mState = savedInstanceState.getParcelable(KEY_STATE);
//            mState.data = Uri.parse("file:///mnt/sdcard/Pictures/Raph_Action.jpg");

            if (mState.data != null) {
                getLoaderManager().initLoader(0, null, this);
            }

        } else {
            mState = new State();
            mState.cropRect = new Rect(0, 0, 0, 0);
//            mState.data = Uri.parse("file:///mnt/sdcard/Pictures/Raph_Action.jpg");
            mMinWidthText.setText("856");
            mMinHeightText.setText("925");
        }
    }

    private void startEdit() {
        Intent intent = new Intent();
        intent.setDataAndType(mState.data, "image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        intent.setAction("com.htc.pe.intent.action.EDIT_PRINT");
        //intent.setAction(Intent.ACTION_EDIT);
        //intent.setAction("com.htc.photoenhancer.action.THEME_CROP");
        //intent.setAction("com.android.camera.action.CROP");
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
        startActivityForResult(intent, 1);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(KEY_STATE, mState);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode != RESULT_OK) {
            return;
        }
        
        switch (requestCode) {
        case 0:
            mState.dataPath = null;
            mState.data = intent.getData();
            mText.setText(mState.data.toString());

            mImageView.setImageDrawable(null);
            View progressView = findViewById(R.id.loading);
            progressView.setVisibility(View.VISIBLE);

            getLoaderManager().restartLoader(0, null, this);

            break;
        case 1:
            Rect cropRect = intent.getParcelableExtra("rectOut");
            if (cropRect != null) { // v2
                mState.cropRect = cropRect;
                String savedPath = intent.getStringExtra("filePath");
                if (savedPath != null) {
                    mState.dataPath = savedPath;
                    mState.data = Uri.fromFile(new File(savedPath));
                    mText.setText(mState.data.toString());

                    getLoaderManager().restartLoader(0, null, this);
                }
                mResultText.setText("Rect: " + cropRect + ", W x H = (" + cropRect.width() + ", " + cropRect.height() + ")");                               
            } else { // v1
                String savedPath = intent.getStringExtra("externalGivenOutputPath");
                if (savedPath != null) {
                    mState.dataPath = savedPath;
                    mState.data = Uri.fromFile(new File(savedPath));
                    mText.setText(mState.data.toString());

                    Bundle bundle = new Bundle();
                    bundle.putBoolean(BitmapLoader.ARGS_KEY_SHOW_BITMAP_DIMENSION, true);
                    getLoaderManager().restartLoader(0, bundle, this);
                    mResultText.setText("Loading...");
                } else {
                    mResultText.setText("Null result");
                }
            }
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

    @Override
    public Loader<Bitmap> onCreateLoader(int id, Bundle args) {
        BitmapLoader bitmapLoader = new BitmapLoader(this).setUri(mState.data).setPath(mState.dataPath);
        if (args != null) {
            bitmapLoader.setShowBitmapDimension(args.getBoolean(BitmapLoader.ARGS_KEY_SHOW_BITMAP_DIMENSION));
        }
        return bitmapLoader;
    }

    @Override
    public void onLoadFinished(Loader<Bitmap> loader, Bitmap data) {
        mImageView.setImageBitmap(data);
        View progressView = findViewById(R.id.loading);
        progressView.setVisibility(View.INVISIBLE);

        if (((BitmapLoader) loader).getShowBitmapDimension()) {
            mResultText.setText("W x H = (" + data.getWidth() + ", " + data.getHeight() + ")");
        }
    }

    @Override
    public void onLoaderReset(Loader<Bitmap> loader) {

    }

    private static class BitmapLoader extends AsyncTaskLoader<Bitmap> {
        public static final String ARGS_KEY_SHOW_BITMAP_DIMENSION = "show_bitmap_dimension";
        private Uri mUri;
        private String mPath;
        private boolean mShowBitmapDimension;

        public BitmapLoader(Context context) {
            super(context);
        }

        public BitmapLoader setUri(Uri uri) {
            mUri = uri;
            return this;
        }

        public BitmapLoader setPath(String path) {
            mPath = path;
            return this;
        }

        public BitmapLoader setShowBitmapDimension(boolean show) {
            mShowBitmapDimension = show;
            return this;
        }

        public boolean getShowBitmapDimension() {
            return mShowBitmapDimension;
        }

        @Override
        public Bitmap loadInBackground() {
            if (mUri == null) {
                if (mPath == null) {
                    return null;
                }

                mUri = Uri.fromFile(new File(mPath));
            }

            Bitmap bitmap = null;
            InputStream is = null;
            try {
                Context context = getContext();
                is = context.getContentResolver().openInputStream(mUri);
                bitmap = BitmapFactory.decodeStream(is);
                if (bitmap == null) {
                    return null;
                }

                String path = mPath != null ? mPath : BitmapLoader.queryPathFromUri(context, mUri);
                if (path != null) {
                    int degree = BitmapLoader.readExifDegree(path);
                    if (degree != 0) {
                        Bitmap rotated = createRotatedBitmap(bitmap, degree);
                        if (rotated != null) {
                            return rotated;
                        }
                    }
                }
            } catch (IOException ex) {

            } catch (SecurityException ex) {

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

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        private static String queryPathFromUri(Context context, Uri uri) {
            String scheme = uri.getScheme();
            if (ContentResolver.SCHEME_FILE.equals(scheme)) {
                return uri.getPath();
            }

            Cursor cursor = null;
            try {
                String[] proj = { MediaStore.Images.Media.DATA };
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

        public static final Parcelable.Creator<State> CREATOR  = new Parcelable.Creator<State>() {
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
}
