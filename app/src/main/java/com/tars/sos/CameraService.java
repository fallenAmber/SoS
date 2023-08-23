package com.tars.sos;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.List;
import java.util.Timer;

public class CameraService extends Service {
    private static final String TAG = "RecordingService";


    private static final String SELECTED_CAMERA_FOR_RECORDING = "cameraForRecording";

    private Camera mCamera;
    private MediaRecorder mMediaRecorder;
    private SurfaceView surfaceView;
    private WindowManager windowManager;
    private String CHANNEL_ID = "RecordServiceChannel";

    private boolean mRecording = false;
    private boolean serviceOn = true;
    private String mRecordingPath = null;

    private Timer mTimer = null;
    Thread streamThread;
    long notify_interval = 1000;
    Intent intent;

    public CameraService() {
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        createNotificationChannel();
        // Start foreground service to avoid unexpected kill
        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Record Service Running")
                .setSmallIcon(R.drawable.button_enabled)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(2, notification);

        Log.i("RecordService", "Service started (onCreate)");

        startRecording();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        createNotificationChannel();
        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Recording Service has stopped")
                .setContentText("Recording Service has stopped")
                .setSmallIcon(R.drawable.button_enabled)
                .setContentIntent(pendingIntent)
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(3, notification);
        Toast.makeText(this, "Service destroyed", Toast.LENGTH_SHORT).show();

        Log.i("LocationService","Service destroyed (onDestroy)");

        stopRecording();

    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startRecording() {
        if (!Util.isCameraExist(this)) {
            throw new IllegalStateException("There is no device, not possible to start recording");
        }
        mRecording = true;

        final int cameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;



        //mCamera = com.tars.cameraapp6.Util.getCameraInstance(cameraId);
        try {
            mCamera = Camera.open(cameraId);
        } catch (Exception e) {
            Log.d("TAG", "Open camera failed: " + e);
        }

        if (mCamera != null) {
            surfaceView = new SurfaceView(this);

            int LAYOUT_FLAG;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
            }
            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    360,
                    640,
                    50,
                    200,
                    LAYOUT_FLAG,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.RIGHT;

            SurfaceHolder surfaceHolder = surfaceView.getHolder();

            surfaceView.setZOrderOnTop(true);
            surfaceHolder.setFormat(PixelFormat.TRANSPARENT);

            surfaceHolder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    Camera.Parameters params = mCamera.getParameters();
                    mCamera.setParameters(params);
                    Camera.Parameters p = mCamera.getParameters();

                    List<Camera.Size> listSize;

                    listSize = p.getSupportedPreviewSizes();
                    Camera.Size mPreviewSize = listSize.get(2);
                    Log.v(TAG, "preview width = " + mPreviewSize.width
                            + " preview height = " + mPreviewSize.height);
                    p.setPreviewSize(mPreviewSize.width, mPreviewSize.height);

                    listSize = p.getSupportedPictureSizes();
                    Camera.Size mPictureSize = listSize.get(2);
                    Log.v(TAG, "capture width = " + mPictureSize.width
                            + " capture height = " + mPictureSize.height);
                    p.setPictureSize(mPictureSize.width, mPictureSize.height);
                    mCamera.setParameters(p);

                    try {
                        mCamera.setPreviewDisplay(holder);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }


                    Camera.CameraInfo info;
                    int result = 0;
                    int numberOfCameras = Camera.getNumberOfCameras();
                    for (int i = 0; i < numberOfCameras; i++) {
                        info = new Camera.CameraInfo();
                        Camera.getCameraInfo(i, info);
                        if (info.facing == cameraId) {
                            int rotation = windowManager.getDefaultDisplay().getRotation();

                            int degrees = 0;

                            switch (rotation)
                            {
                                case Surface.ROTATION_0: degrees = 0; break;
                                case Surface.ROTATION_90: degrees = 90; break;
                                case Surface.ROTATION_180: degrees = 180; break;
                                case Surface.ROTATION_270: degrees = 270; break;
                            }

                            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                            {
                                result = (info.orientation + degrees) % 360;
                                result = (360 - result) % 360;  // compensate the mirror
                            } else {  // back-facing
                                result = (info.orientation - degrees + 360) % 360;
                            }

                            mCamera.setDisplayOrientation(result);
                            break;
                        }
                    }

                    mCamera.startPreview();

                    mCamera.unlock();



                    mMediaRecorder = new MediaRecorder();
                    mMediaRecorder.setCamera(mCamera);

                    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

                    if (cameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_1080P));
                    } else {
                        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_1080P));
                    }

                    mRecordingPath = Util.getOutputMediaFile(Util.MEDIA_TYPE_VIDEO).getPath();
                    mMediaRecorder.setOutputFile(mRecordingPath);

                    mMediaRecorder.setPreviewDisplay(holder.getSurface());
                    mMediaRecorder.setOrientationHint(270);

                    try {
                        mMediaRecorder.prepare();
                    } catch (IllegalStateException e) {
                        Log.d(TAG, "IllegalStateException when preparing MediaRecorder: " + e.getMessage());
                    } catch (IOException e) {
                        Log.d(TAG, "IOException when preparing MediaRecorder: " + e.getMessage());
                    }
                    mMediaRecorder.start();

                    Log.d(TAG, "Recording is started");
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                }
            });


            windowManager.addView(surfaceView, params);

        } else {
            Log.d(TAG, "Get Camera from service failed");
        }
    }


    private void stopRecording() {


        try {
            serviceOn = false;
            mMediaRecorder.stop();
            mMediaRecorder.release();
            windowManager.removeView(surfaceView);
        } catch (RuntimeException e) {
            mMediaRecorder.reset();
            return;
        } finally {
            mMediaRecorder = null;
            mCamera.stopPreview();
            mCamera.release();

            mRecording = false;
        }


        Log.d(TAG, "recording is finished.");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Record Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }





}
