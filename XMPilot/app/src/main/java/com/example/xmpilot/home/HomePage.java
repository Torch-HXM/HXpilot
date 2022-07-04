package com.example.xmpilot.home;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.xmpilot.R;
import com.example.xmpilot.home.camera.CameraProcess;

import java.util.Timer;
import java.util.TimerTask;

public class HomePage extends AppCompatActivity {
    //自定义返回操作部分变量
    private boolean exit_activated = false;
    //相机显示部分变量
    private PreviewView cameraPreview;
    //绘图部分变量
    private ImageView tflite_label;
    //实时显示
    private CameraProcess cameraProcess;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.homepage_whole);
        //打开app时隐藏顶部状态栏
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        //检查程序所需权限，如果不具备则申请
        cameraProcess = new CameraProcess();
        boolean if_permissions_granted = cameraProcess.allPermissionsGranted(HomePage.this);
        if(!if_permissions_granted){
            cameraProcess.requestPermissions(HomePage.this);
        }
        //开启摄像头并实时显示
        cameraPreview = findViewById(R.id.camera_preview);
        cameraPreview.setScaleType(PreviewView.ScaleType.FILL_START);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Log.d("屏幕转角",""+rotation);
        tflite_label = findViewById(R.id.tflite_label);
        cameraProcess.startCamera(HomePage.this, cameraPreview, tflite_label,rotation);
    }
    //自定义返回操作，在该页面两次返回推出app
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode==KeyEvent.KEYCODE_BACK){
            exit();
        }
        return false;
    }
    private void exit(){
        if (!exit_activated){
            exit_activated=true;
            Toast.makeText(this,"再按一次退出",Toast.LENGTH_SHORT).show();
            Timer exit_timer = new Timer();
            exit_timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    exit_activated=false;
                }
            },2000);
        }else {
            //2000ms内按第二次则退出
            finish();
        }
    }
}