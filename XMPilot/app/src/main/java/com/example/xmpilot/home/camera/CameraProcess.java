package com.example.xmpilot.home.camera;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;
import android.util.Size;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.example.xmpilot.home.drawer.Drawer;
import com.example.xmpilot.home.tflite.TFLiteDetector;
import com.example.xmpilot.home.tflite.utils.Recognition;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class CameraProcess {
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE"};
    private Drawer drawer;
    private TFLiteDetector tfLiteDetector;
    //用来储存图像和计算时间数据，用于将子线程数据返回到主线程
    public static class Result{
        public Result(long costTime, Bitmap bitmap) {
            this.costTime = costTime;
            this.bitmap = bitmap;
        }
        long costTime;
        Bitmap bitmap;
    }
    //判断是否具备摄像头权限
    public boolean allPermissionsGranted(Context context){
        for(String permission : REQUIRED_PERMISSIONS){
            if (ContextCompat.checkSelfPermission(context,permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }
    //申请权限
    public void requestPermissions(Activity activity){
        int REQUEST_CODE_PERMISSIONS = 1001;
        ActivityCompat.requestPermissions(activity,REQUIRED_PERMISSIONS,REQUEST_CODE_PERMISSIONS);
    }
    //开启摄像头，提供预览图象,并引入图像分析
    public void startCamera(Context context, PreviewView previewView, ImageView tflite_label, int rotation){
        cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                //将预览图绑定到preview控件
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                //类初始化
                tfLiteDetector = new TFLiteDetector(context,previewView);
                drawer = new Drawer(previewView);
                //为预览图加入分析
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().build();
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context), new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(@NonNull ImageProxy image) {
                        /*
                        * 对图像的处理操作放在这里
                        * */
                        Observable.create( (ObservableEmitter<Result> emitter) -> {
                                    long start = System.currentTimeMillis();

                                    ArrayList<Recognition> recognitions = tfLiteDetector.detect(image);
                                    Bitmap tflite_bitmap = drawer.drawTFLiteMessage(recognitions, tfLiteDetector.getPreviewToModelTransform());
                                    image.close();

                                    long end = System.currentTimeMillis();
                                    long costTime = (end - start);
                                    emitter.onNext(new Result(costTime, tflite_bitmap));
                                }).subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                                .subscribe((Result result)->{
                                                    tflite_label.setImageBitmap(result.bitmap);
                                                });
                    }
                });

                Preview previewBuilder = new Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build();
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();
                previewBuilder.setSurfaceProvider(previewView.createSurfaceProvider());
                cameraProvider.bindToLifecycle((LifecycleOwner) context,cameraSelector,imageAnalysis,previewBuilder);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public void showCameraSupportSize(Activity activity) {
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics cc = manager.getCameraCharacteristics(id);
                if (cc.get(CameraCharacteristics.LENS_FACING) == 1) {
                    Size[] previewSizes = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            .getOutputSizes(SurfaceTexture.class);
                    for (Size s : previewSizes){
                        Log.i("camera", s.getHeight()+"/"+s.getWidth());
                    }
                    break;

                }
            }
        } catch (Exception e) {
            Log.e("image", "can not open camera", e);
        }
    }
}