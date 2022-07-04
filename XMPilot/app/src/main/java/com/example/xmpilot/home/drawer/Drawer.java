package com.example.xmpilot.home.drawer;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import android.util.Size;

import androidx.camera.view.PreviewView;

import com.example.xmpilot.home.tflite.utils.ImageProcess;
import com.example.xmpilot.home.tflite.utils.Recognition;

import java.util.ArrayList;
import java.util.Random;

public class Drawer {

    private PreviewView previewView;

    public Drawer(PreviewView previewView){
        this.previewView = previewView;
    }
    public Bitmap drawTFLiteMessage(ArrayList<Recognition> recognitions, Matrix previewToModelTransform) {
        Canvas canvas;
        Paint box_paint;
        Paint text_paint;
        //创建一个与preview同大小的画布
        Bitmap tflite_bitmap = Bitmap.createBitmap(previewView.getWidth(), previewView.getHeight(), Bitmap.Config.ARGB_8888);
        canvas = new Canvas(tflite_bitmap);
        //配置画笔
        box_paint = new Paint();
        box_paint.setStrokeWidth(5);
        box_paint.setStyle(Paint.Style.STROKE);
        box_paint.setColor(Color.RED);

        text_paint = new Paint();
        text_paint.setTextSize(50);
        text_paint.setColor(Color.RED);
        text_paint.setStyle(Paint.Style.FILL);
        //画矩形
        //canvas.drawRect(970,340,1370,740,box_paint);
        Matrix modelToPreviewTransform = new Matrix();
        previewToModelTransform.invert(modelToPreviewTransform);

        for (Recognition res : recognitions) {
            RectF location = res.getLocation();
            String label = res.getLabelName();
            float confidence = res.getConfidence();
            modelToPreviewTransform.mapRect(location);
            canvas.drawRect(location, box_paint);
            canvas.drawText(label + ":" + String.format("%.2f", confidence), location.left+10, location.top+40, text_paint);
        }
        return tflite_bitmap;
    }

    public Bitmap randomDraw(){
        Canvas canvas;
        Paint box_paint;
        //创建一个与preview同大小的画布
        Bitmap tflite_bitmap = Bitmap.createBitmap(previewView.getWidth(), previewView.getHeight(), Bitmap.Config.ARGB_8888);
        canvas = new Canvas(tflite_bitmap);
        //配置画笔
        box_paint = new Paint();
        box_paint.setStrokeWidth(5);
        box_paint.setStyle(Paint.Style.STROKE);
        box_paint.setColor(Color.RED);

        int random_left = (int)Math.random()*2000;
        int random_top = (int)Math.random()*900;

        canvas.drawRect(random_left,random_top,random_left+200,random_top+200,box_paint);

        return tflite_bitmap;
    }
}