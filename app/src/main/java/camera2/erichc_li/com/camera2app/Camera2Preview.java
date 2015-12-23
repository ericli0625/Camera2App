package camera2.erichc_li.com.camera2app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import java.util.Arrays;
import java.util.List;

public class Camera2Preview extends TextureView implements TextureView.SurfaceTextureListener {

    private static final String TAG = Camera2Preview.class.getName();
    private final Context mContext;
    private String mCameraId;
    private StreamConfigurationMap mmap;
    private CameraCharacteristics mCameraCharacteristics;

    private Size mPreviewSize;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private CameraCaptureSession mCaptureSession;
    private ImageReader mImageReader;

    public Camera2Preview(Context context) {
        super(context);
        this.setSurfaceTextureListener(this);
        mContext = context;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "onSurfaceTextureAvailable()...");

        openCamera();
        setUpCameraOutputs(width, height);

    }

    private void openCamera() {

        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);

        try {

            for (String cameraId : manager.getCameraIdList()) {

                CameraCharacteristics cameracharacteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facingID = cameracharacteristics.get(CameraCharacteristics.LENS_FACING);
                StreamConfigurationMap map = cameracharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (facingID != null && facingID == CameraCharacteristics.LENS_FACING_BACK && map != null) {
                    mCameraId = cameraId;
                    mCameraCharacteristics = cameracharacteristics;
                    mmap = map;
                    break;
                }
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        try {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            manager.openCamera(mCameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private CameraDevice mCameraDevice;

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.

            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            mCameraDevice = null;
        }

    };

    private void setUpCameraOutputs(int width, int height) {

        setUpPreviewSize(width, height);

    }

    private void setUpPreviewSize(int width, int height){

        // Find out if we need to swap dimension to get the preview size relative to sensor coordinate.

        WindowManager windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);

        int displayRotation = windowManager.getDefaultDisplay().getRotation();
        int sensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        boolean swappedDimensions = false;
        switch (displayRotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    swappedDimensions = true;
                }
                break;
            default:
                Log.e(TAG, "Display rotation is invalid: " + displayRotation);
        }

        //Log.i(TAG, "setUpPreviewSize()...sensorOrientation = "+sensorOrientation+", displayRotation = "+displayRotation+", swappedDimensions = "+swappedDimensions);

        Point displaySize = new Point();
        windowManager.getDefaultDisplay().getSize(displaySize);

        //Log.i(TAG, "setUpPreviewSize()...TextureViewwidth = " + width + ", TextureViewheight = " + height);
        //Log.i(TAG, "setUpPreviewSize()...displaySize.x = " + displaySize.x + ", displaySize.y = " + displaySize.y);

        int rotatedPreviewWidth = width;
        int rotatedPreviewHeight = height;

        if (swappedDimensions) {
            rotatedPreviewWidth = height;
            rotatedPreviewHeight = width;
        }

        mPreviewSize = new Size (rotatedPreviewWidth,rotatedPreviewHeight);

        //Log.i(TAG, "setUpPreviewSize()...rotatedPreviewWidth = "+rotatedPreviewWidth+", rotatedPreviewHeight = "+rotatedPreviewHeight);

    }

    private void createCameraPreviewSession() {

        // 1.
        // get SurfaceTexture
        SurfaceTexture mTextureView = this.getSurfaceTexture();
        // We configure the size of default buffer to be the size of camera preview we want.
        mTextureView.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        // This is the output Surface we need to start preview.
        Surface mSurface = new Surface(mTextureView);

        //Size[] outputSize = mmap.getOutputSizes(ImageFormat.JPEG);
        //Size[] previewSizes = mmap.getOutputSizes(SurfaceTexture.class);

        // For still image captures, we use the largest available size.
        //mImageReader = ImageReader.newInstance(outputSize[0].getWidth(), outputSize[0].getHeight(), ImageFormat.JPEG, /*maxImages*/2);
        //mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);


        // 2.
        // We set up a CaptureRequest.Builder with the output Surface.
        try {
            mPreviewRequestBuilder  = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mSurface);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        // 3.
        // Here, we create a CameraCaptureSession for camera preview.
        try {
            mCameraDevice.createCaptureSession(Arrays.asList(mSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {

                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                // Finally, we start displaying the camera preview.
                                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.i(TAG,"Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e1) {
            e1.printStackTrace();
        }

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.i(TAG, "onSurfaceTextureDestroyed()...");
        mCameraDevice.close();
        mCameraDevice = null;
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }
}

