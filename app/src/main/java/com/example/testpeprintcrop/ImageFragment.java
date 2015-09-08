package com.example.testpeprintcrop;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class ImageFragment extends Fragment {
    public static final String TAG = ImageFragment.class.getSimpleName();

    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM_LOADING = "paraLoading";

    private boolean mIsLoadingAtStart;
    private ImageView mImageView;
    private View mProgressView;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment ImageFragment.
     */
    public static ImageFragment newInstance(boolean loading) {
        ImageFragment fragment = new ImageFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_PARAM_LOADING, loading);
        fragment.setArguments(args);
        return fragment;
    }

    public ImageFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mIsLoadingAtStart = getArguments().getBoolean(ARG_PARAM_LOADING);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_image, container, false);
        mImageView = (ImageView) view.findViewById(R.id.image);
        mProgressView = view.findViewById(R.id.loading);
        if (mIsLoadingAtStart) {
            startLoading();
        }
        return view;
    }

    public void startLoading() {
        mImageView.setImageBitmap(null);
        mProgressView.setVisibility(View.VISIBLE);
    }

    public void setImageBitmap(Bitmap bitmap) {
        mImageView.setImageBitmap(bitmap);
        mProgressView.setVisibility(View.INVISIBLE);
    }
}
