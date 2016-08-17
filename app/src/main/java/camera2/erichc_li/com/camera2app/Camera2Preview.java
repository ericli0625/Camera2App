package camera2.erichc_li.com.camera2app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class Camera2Preview extends TextureView implements TextureView.SurfaceTextureListener {

    private static final String TAG = Camera2Preview.class.getName();
    private final Context mContext;
    private final int mFormat;
    private String mCameraId;
    private StreamConfigurationMap mapScalerStreamConig;
    private CameraCharacteristics mCameraCharacteristics;

    private Size mPreviewSize;

    private CaptureResult mCaptureResult_G;

    private CaptureRequest.Builder mCaptureBuilder;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private ImageReader mImageReader;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * Request code for camera permissions.
     */
    private static final int REQUEST_CAMERA_PERMISSIONS = 1;

    public Camera2Preview(Context context,int format) {
        super(context);
        this.setSurfaceTextureListener(this);
        mContext = context;
        mFormat = format;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "onSurfaceTextureAvailable()...");
        startBackgroundThread();
        openCamera(width, height);
    }

    private void openCamera(int width, int height) {

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
                    mapScalerStreamConig = map;
                    break;
                }
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        setUpCameraOutputs(width, height);

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
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
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

        setUpPreviewOutputs(width, height);
        setUpPhotoOutputs();

    }

    private void setUpPreviewOutputs (int width, int height){

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

        //Log.i(TAG, "setUpPreviewOutputs()...sensorOrientation = "+sensorOrientation+", displayRotation = "+displayRotation+", swappedDimensions = "+swappedDimensions);

        Point displaySize = new Point();
        windowManager.getDefaultDisplay().getSize(displaySize);

        //Log.i(TAG, "setUpPreviewOutputs()...TextureViewwidth = " + width + ", TextureViewheight = " + height);
        //Log.i(TAG, "setUpPreviewOutputs()...displaySize.x = " + displaySize.x + ", displaySize.y = " + displaySize.y);

        int rotatedPreviewWidth = width;
        int rotatedPreviewHeight = height;

        if (swappedDimensions) {
            rotatedPreviewWidth = height;
            rotatedPreviewHeight = width;
        }

        mPreviewSize = new Size (rotatedPreviewWidth,rotatedPreviewHeight);

        //Log.i(TAG, "setUpPreviewOutputs()...rotatedPreviewWidth = "+rotatedPreviewWidth+", rotatedPreviewHeight = "+rotatedPreviewHeight);

    }

    private void createCameraPreviewSession() {

        // 1.
        // get SurfaceTexture
        SurfaceTexture mTextureView = this.getSurfaceTexture();
        // We configure the size of default buffer to be the size of camera preview we want.
        mTextureView.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        // This is the output Surface we need to start preview.
        Surface mSurface = new Surface(mTextureView);

        // 2.
        // We set up a CaptureRequest.Builder with the output Surface.
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mSurface);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        // 3.
        // Here, we create a CameraCaptureSession for camera preview.
        try {
            mCameraDevice.createCaptureSession(Arrays.asList(mSurface, mImageReader.getSurface()), mSessionStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e1) {
            e1.printStackTrace();
        }

    }

    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            try {
                updatePreview(session);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    };

    private void updatePreview(CameraCaptureSession session) throws CameraAccessException {

        // When the session is ready, we start displaying the preview.
        mCaptureSession = session;
        try {
            // Auto focus should be continuous for camera preview.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);

            // 4.
            // Finally, we start displaying the camera preview.
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted (CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result){
            Log.i(TAG, "onCaptureCompleted()...");
            mCaptureResult_G = result;
        }

        @Override
        public void onCaptureFailed (CameraCaptureSession session, CaptureRequest request, CaptureFailure failure){
            Log.i(TAG, "onCaptureFailed()...");
        }
    };

    public void setUpPhotoOutputs() {

        int mPicFormat = ImageFormat.UNKNOWN;

        switch (mFormat){
            case ImageFormat.JPEG:

                for (Size mSize : mapScalerStreamConig.getOutputSizes(ImageFormat.JPEG)){
                    Log.i(TAG,"mSize = "+mSize);
                }

                mPicFormat = ImageFormat.JPEG;
                break;
            case ImageFormat.RAW_SENSOR:

                if(contains(mCameraCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {

                    for (Size mSize : mapScalerStreamConig.getOutputSizes(ImageFormat.RAW_SENSOR)) {
                        Log.i(TAG, "mSize = " + mSize);
                    }
                    mPicFormat = ImageFormat.RAW_SENSOR;
                } else {
                    Log.i(TAG,"Not support RAW");
                    Toast.makeText(mContext, "Not support RAW, so use the JPEG Format.", Toast.LENGTH_LONG).show();
                    mPicFormat = ImageFormat.JPEG;
                }
                break;
        }

        // create the photo size
        Size[] outputSize = mapScalerStreamConig.getOutputSizes(mPicFormat);

        // For still image captures, we use the largest available size.
        mImageReader = ImageReader.newInstance(outputSize[0].getWidth(), outputSize[0].getHeight(), mPicFormat, /*maxImages*/2);

        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

    }

    public void takePicture() {

        try {
            // We set up a CaptureRequest.Builder to capture the pic.
            mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureBuilder.addTarget(mImageReader.getSurface());
            mCaptureSession.capture(mCaptureBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private File getOutputMediaFile() {

        File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String photoPath = null;

        switch (mFormat) {
            case ImageFormat.JPEG:
                photoPath = path.getPath() + File.separator + "IMG_" + timeStamp + ".jpg";
                break;
            case ImageFormat.RAW_SENSOR:
                photoPath = path.getPath() + File.separator + "RAW_" + timeStamp + ".dng";
                break;
        }

        Log.i(TAG, photoPath);
        File photo = new File(photoPath);

        return photo;
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        public void onImageAvailable(ImageReader reader) {

            Image mImage = reader.acquireLatestImage();
            File mPicPath = getOutputMediaFile();

            ImageSaver mImageSaver = new ImageSaver(mImage, mPicPath,mCaptureResult_G ,mCameraCharacteristics);

            mBackgroundHandler.post(mImageSaver);

        }

    };

    private class ImageSaver implements Runnable {

        private final Image mImage;
        private final File mPicPath;

        /**
         * The CaptureResult for this image capture.
         */
        private final CaptureResult mCaptureResult;

        /**
         * The CameraCharacteristics for this camera device.
         */
        private final CameraCharacteristics mCharacteristics;

        ImageSaver(Image image,File picPath, CaptureResult result, CameraCharacteristics characteristics){
            mImage = image;
            mPicPath = picPath;
            mCaptureResult = result;
            mCharacteristics = characteristics;
        }

        @Override
        public void run() {
            int format = mImage.getFormat();
            boolean success = false;

            switch (format) {
                case ImageFormat.JPEG: {
                    ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    FileOutputStream output = null;

                    Bitmap pictureTaken = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    Matrix matrix = new Matrix();
                    matrix.preRotate(90);
                    pictureTaken = Bitmap.createBitmap(pictureTaken ,0,0, pictureTaken.getWidth(), pictureTaken.getHeight(),matrix,true);

                    try {
                        output = new FileOutputStream(mPicPath.getPath());
                        pictureTaken.compress(Bitmap.CompressFormat.JPEG, 50, output);
                        pictureTaken.recycle();
                        output.write(bytes);
                        output.close();
                        success = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        mImage.close();
                        if (null != output) {
                            try {
                                output.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    break;
                }
                case ImageFormat.RAW_SENSOR: {
                    DngCreator dngCreator = new DngCreator(mCharacteristics, mCaptureResult);
                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(mPicPath.getPath());
                        dngCreator.writeImage(output, mImage);
                        success = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        mImage.close();
                        closeOutput(output);
                    }
                    break;
                }
                default: {
                    Log.e(TAG, "Cannot save image, unexpected image format:" + format);
                    break;
                }

            }

            if(success){
                galleryAddPic(mPicPath);
            }

        }
    }

    private static void closeOutput(OutputStream outputStream) {
        if (null != outputStream) {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void galleryAddPic(File photoPath) {
        Uri contentUri = Uri.fromFile(photoPath);
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,contentUri);
        mContext.sendBroadcast(mediaScanIntent);
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {

        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {

        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.i(TAG, "onSurfaceTextureDestroyed()...");
        stopBackgroundThread();
        closeCamera();
        return false;
    }

    /**
     * Return true if the given array contains the given integer.
     *
     * @param modes array to check.
     * @param mode  integer to get for.
     * @return true if the array contains the given integer, otherwise false.
     */
    private static boolean contains(int[] modes, int mode) {
        if (modes == null) {
            return false;
        }
        for (int i : modes) {
            if (i == mode) {
                return true;
            }
        }
        return false;
    }

}

