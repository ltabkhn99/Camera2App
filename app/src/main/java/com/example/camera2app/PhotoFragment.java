package com.example.camera2app;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.camera2app.databinding.FragmentPhotoBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;

public class PhotoFragment extends Fragment {

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private static Handler backgroundHandler;
    private static HandlerThread backgroundThread;
    private int cameraListIndex;
    private String cameraId;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private CaptureRequest.Builder mPreviewRequestBuilder;

    private static CameraCaptureSession mCameraCaptureSession;
    private static ImageReader imageReader;
    private static File linkImage;

    private FragmentPhotoBinding mFragmentPhotoBinding;
    private AutoFitTextureView textureView;
    private ImageButton btnCapture;
    private ImageButton btnSwitchCamera;
    private ImageButton btnGallery;

    private boolean isCameraSwitching = false; // Biến cờ để kiểm tra xem camera có đang được chuyển đổi không
    // private Semaphore cameraSwitchSemaphore = new Semaphore(1); // Semaphore để đồng bộ hóa việc chuyển đổi camera


//    public static PhotoFragment newInstance() {
//        return new PhotoFragment();
//    }

    private static ArrayList<File> mediaList;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i("PhotoFragment", "onCreateView");
        mFragmentPhotoBinding = FragmentPhotoBinding.inflate(getLayoutInflater());

        initView();

        setOnClickListener();

        mediaList = new ArrayList<>();
        getMediaFiles();
        Log.i("size list", mediaList.size() + "");
        return mFragmentPhotoBinding.getRoot();
    }

    private void setOnClickListener() {

        btnCapture.setOnClickListener(v -> {
            takePicture();
        });
        //------------------
        btnSwitchCamera.setOnClickListener(v -> {
            if (isCameraSwitching) btnSwitchCamera.setClickable(false);
            else {

                isCameraSwitching = true;
                Animation animZoomIn = AnimationUtils.loadAnimation(getContext(), R.anim.zoom_in);
                btnSwitchCamera.startAnimation(animZoomIn);
                switchCamera();
            }

        });
        //------------------
        btnGallery.setOnClickListener(v -> {
            openGallery();
        });

    }

    private void openGallery() {
    }

    private void switchCamera() {
        closeCamera();
        cameraListIndex = cameraListIndex == 0 ? 1 : 0;
        openCamera(cameraListIndex);
        isCameraSwitching = false;
        btnSwitchCamera.setClickable(true);
    }

    private void initView() {
        btnCapture = mFragmentPhotoBinding.captureButton;
        btnSwitchCamera = mFragmentPhotoBinding.switchCameraButton;
        btnGallery = mFragmentPhotoBinding.galleryButton;
        textureView = mFragmentPhotoBinding.surfaceView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.i("PhotoFragment", "onViewCreated");
//        textureView = mFragmentPhotoBinding.surfaceView;
//        linkImage = mediaList.get(0);
        //Glide.with(requireActivity()).load(mediaList.get(0)).into(mFragmentPhotoBinding.galleryButton);
    }

    private void getMediaFiles() {
        Log.i("FolderCamera", "true");
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    100);
        } else {
            File cameraFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
            if (cameraFolder.exists()) {
                File[] files = cameraFolder.listFiles();
                Log.i("list=", files.length + "");
                //Log.i("NAMEFILE:",files[0].getName()+files[1].getName());
                if (files != null) {
                    // Sắp xếp các file theo thời gian sửa đổi gần nhất
                    List<File> sortedFiles = new ArrayList<>(Arrays.asList(files));
                    Log.i("sortedFiles=", sortedFiles.size() + "");
                    sortedFiles.sort((file1, file2) -> Long.compare(file2.lastModified(), file1.lastModified()));
                    Log.i("sortedFiles=", sortedFiles.size() + "");
                    // Thêm đường dẫn của các file đã sắp xếp vào mediaList
                    for (File file : files) {
                        Log.i("file: ", file.getName().endsWith(".jpg") + "");
                        if (file.getName().endsWith(".jpg")) {
                            Log.i("add=", "true");
                            mediaList.add(file);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onResume() {
        Log.i("PhotoFragment", "onResume");
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera(cameraListIndex);
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        Log.i("PhotoFragment", "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }


    // ------------------------Handle background thread-----------------------------//
    private void startBackgroundThread() {
        Log.i("PhotoFragment", "startBackgroundThread");
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    static void stopBackgroundThread() {
        Log.i("PhotoFragment", "stopBackgroundThread");
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while shutting down background thread", e);
        }
    }
    // ------------------------------------------------------------------------------//


    //---------------------------------------------------SurfaceTextureListener-----------------------------------------------//
    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            Log.i("PhotoFragment", "onSurfaceTextureAvailable");
            setTextureViewAspectRatio(width, height);
//            w = width;
//            h = height;
            openCamera(0);

        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            Log.i("PhotoFragment", "onSurfaceTextureSizeChanged");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            Log.i("PhotoFragment", "onSurfaceTextureDestroyed");
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            Log.i("PhotoFragment", "onSurfaceTextureUpdated");
        }
    };

    //---------------------------------------------------------------//
    private void setTextureViewAspectRatio(int width, int height) {
        if (width > 0 && height > 0) {
            Log.i("PhotoFragment", "setTextureViewAspectRatio");
            int newHeight = (int) (width * 1.333);
            textureView.setAspectRatio(width, newHeight);
        }
    }
    // ---------------------------------------------------------------------------------------------------------------------------//


    //--------------------------Open and Close camera---------------------------------//
    private void openCamera(int cameraListIndex) {
        Log.i("PhotoFragment", "openCamera");
        mCameraManager = (CameraManager) requireActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = mCameraManager.getCameraIdList();
            cameraId = cameraIdList[cameraListIndex];

            if (ActivityCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            mCameraManager.openCamera(cameraId, deviceStateCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access the camera", e);
        }
    }

    private void closeCamera() {
        Log.i("PhotoFragment", "closeCamera");
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }
    // ------------------------------------------------------------------------------//


    //-------------------------------------CallBack----start------------------------------------//
    private final CameraDevice.StateCallback deviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.i("PhotoFragment", "deviceStateCallback - onOpened");
            mCameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.i("PhotoFragment", "deviceStateCallback - onDisconnected");
            mCameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.i("PhotoFragment", "deviceStateCallback - onError");
        }
    };
    ///////////////////////////////////////////////////////////////////

    private final CameraCaptureSession.StateCallback captureSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (mCameraDevice == null) {
                return;
            }
            Log.i("PhotoFragment", "captureSessionStateCallback - onConfigured");
            mCameraCaptureSession = session;
            try {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                mCameraCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, backgroundHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Cannot access the camera", e);
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "Failed to configure camera");
        }
    };
    /////////////////////////////////////////////////////////////////////
    //--> Theo dõi tiến trình của CaptureRequest
    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(getActivity(), "Took photo", Toast.LENGTH_SHORT).show();

            // Xử lý ảnh đã chụp
            Image image = imageReader.acquireNextImage(); // --> lấy ảnh mới nhất
            if (image != null) {
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                File pictureFile = getOutputMediaFile();
                if (pictureFile != null) {
                    // lưu ảnh vào file đã lấy
                    try (FileOutputStream fileOutputStream = new FileOutputStream(pictureFile)) {
                        fileOutputStream.write(bytes);
                        linkImage = pictureFile;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Glide.with(requireContext()).load(pictureFile).into(mFragmentPhotoBinding.galleryButton); // Load photo by Glide Library
                        });
                    } catch (IOException e) {
                        Log.e(TAG, "Error saving image", e);
                    }
                }
                image.close();
            }
            // Xử lý ảnh xong tiếp tục phiên preview
            createCameraPreviewSession();
        }
    };
    // -------------------------------------CallBack----end------------------------------------------//


    private File getOutputMediaFile() {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
        if (!mediaStorageDir.exists()) {
            Log.i("FolderNotFound", "DCIM/Camera is not exists ");
            if (!mediaStorageDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory");
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(new Date());

        return new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");
    }


    //-----------------------------------------------------Create session preview-------------------------------------------------//
    private void createCameraPreviewSession() {
        Log.i("PhotoFragment", " createCameraPreviewSession");

        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // Get the supported sizes for the camera
            StreamConfigurationMap map = mCameraManager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP); // --> Các cấu hình stream có sẵn mà thiết bị camera hỗ trợ
            assert map != null;
            Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);

            // Choose the optimal preview size based on the aspect ratio of TextureView
            Size previewSize = chooseOptimalSize(outputSizes, textureView.getWidth(), textureView.getHeight());

            // Set the default buffer size to the chosen preview size
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(Collections.singletonList(surface), captureSessionStateCallback, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access the camera", e);
        }
    }

    ///////////////////////////////////////////////////
    private Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight) {
        Log.i("PhotoFragment", "chooseOptimalSize");
        List<Size> bigEnough = new ArrayList<>();
        List<Size> notBigEnough = new ArrayList<>();
        for (Size option : choices) {
            if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
                bigEnough.add(option);
            } else {
                notBigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    ////////////////////////////////////////////////////
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            Log.i("PhotoFragment", "CompareSizesByArea");
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
    // ---------------------------------------------------------------------------------------------------------------------------//

    private void takePicture() {
        if (mCameraDevice == null) {
            Log.e(TAG, "CameraDevice is null. Cannot take picture.");
            return;
        }

        File pictureFile = getOutputMediaFile();
        if (pictureFile == null) {
            Log.e(TAG, "Error creating media file, check storage permissions");
            return;
        }

        // start take photo
        try {
            // Lưu ảnh vào ImageReader
            imageReader = ImageReader.newInstance(textureView.getWidth(), textureView.getHeight(), ImageFormat.JPEG, 1);

            CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            if (cameraListIndex == 0) {
                captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);
            } else {
                captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 270);
            }
            // Bật flash khi chụp ảnh
//            if (flashMode) {
//                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
//            } else {
//                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
//            }

            mCameraDevice.createCaptureSession(Arrays.asList(new Surface(textureView.getSurfaceTexture()), imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureRequestBuilder.build(), captureCallback, backgroundHandler);
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Cannot access the camera", e);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "Failed to configure camera");
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access the camera", e);
        }
    }
}
