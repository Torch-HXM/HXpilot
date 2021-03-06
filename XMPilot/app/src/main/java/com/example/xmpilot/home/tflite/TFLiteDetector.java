package com.example.xmpilot.home.tflite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;

import com.example.xmpilot.home.tflite.utils.ImageProcess;
import com.example.xmpilot.home.tflite.utils.Recognition;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class TFLiteDetector {

    private Context activity;

    private Interpreter tflite;
    private Interpreter.Options options;;
    private List<String> associatedAxisLabels;
    private ImageProcess imageProcess;
    private PreviewView previewView;
    private Matrix previewToModelTransform;

    private final String MODEL_FILE = "yolov5s.tflite";
    private final String LABEL_FILE = "coco_v5s.txt";
    private final Size INPUT_SIZE = new Size(320,320);
    private final int[] OUTPUT_SIZE = new int[]{1, 6300, 85};
    private final float DETECT_THRESHOLD = 0.25f;
    private final float IOU_THRESHOLD = 0.45f;
    private final float IOU_CLASS_DUPLICATED_THRESHOLD = 0.7f;

    public TFLiteDetector(Context activity, PreviewView previewView){
        this.activity = activity;
        this.previewView = previewView;
        this.options  = new Interpreter.Options();
        imageProcess = new ImageProcess();
        //??????????????????????????????????????????????????????????????????analyze????????????????????????
        //????????????????????????analyze??????????????????
        setOptions();
        loadModel();
    }
    //?????????????????????????????????????????????????????????????????????????????????????????????
    private void setOptions(){
        CompatibilityList compatibilityList = new CompatibilityList();
        if(compatibilityList.isDelegateSupportedOnThisDevice()){
            GpuDelegate.Options delegateOptions = compatibilityList.getBestOptionsForThisDevice();
            GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
            options.addDelegate(gpuDelegate);
            Log.i("tfliteSupport", "using gpu delegate.");
        } else {
            options.setNumThreads(4);
        }
    }
    //???????????????????????????
    private void loadModel(){
        try {
            ByteBuffer tfliteModel = FileUtil.loadMappedFile(activity, MODEL_FILE);
            tflite = new Interpreter(tfliteModel, options);
            Log.i("tfliteSupport", "Success reading model: " + MODEL_FILE);

            associatedAxisLabels = FileUtil.loadLabels(activity, LABEL_FILE);
            Log.i("tfliteSupport", "Success reading label: " + LABEL_FILE);

        } catch (IOException e) {
            Log.e("tfliteSupport", "Error reading model or label: ", e);
            Toast.makeText(activity, "load model error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public Bitmap getDetectedBitmap(ImageProxy image){
        Bitmap cropImageBitmap = previewView.getBitmap();
        // ???????????????bitmap
        previewToModelTransform =
                imageProcess.getTransformationMatrix(
                        cropImageBitmap.getWidth(), cropImageBitmap.getHeight(),
                        INPUT_SIZE.getWidth(),
                        INPUT_SIZE.getHeight(),
                        0, false);
        Bitmap modelInputBitmap = Bitmap.createBitmap(cropImageBitmap, 0, 0,
                cropImageBitmap.getWidth(), cropImageBitmap.getHeight(),
                previewToModelTransform, false);
        return modelInputBitmap;
    }

    public Matrix getPreviewToModelTransform() {
        return previewToModelTransform;
    }

    private TensorImage getModelInput(Bitmap bitmap){
        //??????????????????????????????????????????????????????????????????float32
        TensorImage yolov5sTfliteInput;
        yolov5sTfliteInput = new TensorImage(DataType.FLOAT32);
        yolov5sTfliteInput.load(bitmap);
        //???????????????????????????????????????????????????320x320???
        ImageProcessor imageProcessor;
        imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeOp(INPUT_SIZE.getHeight(), INPUT_SIZE.getWidth(), ResizeOp.ResizeMethod.BILINEAR))
                        .add(new NormalizeOp(0, 255))
                        .build();
        //??????????????????????????????????????????????????????
        yolov5sTfliteInput = imageProcessor.process(yolov5sTfliteInput);

        return yolov5sTfliteInput;
    }

    private TensorBuffer getModelOutputContainer(){
        return TensorBuffer.createFixedSize(OUTPUT_SIZE, DataType.FLOAT32);
    }

    public ArrayList<Recognition> detect(ImageProxy image){
        Bitmap bitmap = getDetectedBitmap(image);
        //detect???analyze???????????????????????????analyze?????????????????????????????????
        //???????????????????????????????????????
        TensorImage tfliteInput = getModelInput(bitmap);
        TensorBuffer tfliteOutputContainer = getModelOutputContainer();
        //????????????
        if (null != tflite) {
            // ??????tflite??????????????????batch=1?????????
            tflite.run(tfliteInput.getBuffer(), tfliteOutputContainer.getBuffer());
            //?????????????????????tfliteOutputContainer????????????????????????????????????
        }
        //??????????????????
        float[] recognitionArray = tfliteOutputContainer.getFloatArray();
        // ?????????flatten?????????????????????(xywh,obj,classes).
        ArrayList<Recognition> allRecognitions = new ArrayList<>();
        for (int i = 0; i < OUTPUT_SIZE[1]; i++) {
            int gridStride = i * OUTPUT_SIZE[2];
            // ??????yolov5???????????????tflite???????????????????????????image size, ???????????????????????????
            float x = recognitionArray[0 + gridStride] * INPUT_SIZE.getWidth();
            float y = recognitionArray[1 + gridStride] * INPUT_SIZE.getHeight();
            float w = recognitionArray[2 + gridStride] * INPUT_SIZE.getWidth();
            float h = recognitionArray[3 + gridStride] * INPUT_SIZE.getHeight();
            int xmin = (int) Math.max(0, x - w / 2.);
            int ymin = (int) Math.max(0, y - h / 2.);
            int xmax = (int) Math.min(INPUT_SIZE.getWidth(), x + w / 2.);
            int ymax = (int) Math.min(INPUT_SIZE.getHeight(), y + h / 2.);
            float confidence = recognitionArray[4 + gridStride];
            float[] classScores = Arrays.copyOfRange(recognitionArray, 5 + gridStride, 85 + gridStride);

            int labelId = 0;
            float maxLabelScores = 0.f;
            for (int j = 0; j < classScores.length; j++) {
                if (classScores[j] > maxLabelScores) {
                    maxLabelScores = classScores[j];
                    labelId = j;
                }
            }

            Recognition r = new Recognition(
                    labelId,
                    "",
                    maxLabelScores,
                    confidence,
                    new RectF(xmin, ymin, xmax, ymax));
            allRecognitions.add(
                    r);
        }

        // ?????????????????????
        ArrayList<Recognition> nmsRecognitions = nms(allRecognitions);
        // ????????????????????????, ?????????????????????????????????2???????????????????????????????????????
        ArrayList<Recognition> nmsFilterBoxDuplicationRecognitions = nmsAllClass(nmsRecognitions);

        // ??????label??????
        for(Recognition recognition : nmsFilterBoxDuplicationRecognitions){
            int labelId = recognition.getLabelId();
            String labelName = associatedAxisLabels.get(labelId);
            recognition.setLabelName(labelName);
        }
        return nmsFilterBoxDuplicationRecognitions;
    }

    protected ArrayList<Recognition> nms(ArrayList<Recognition> allRecognitions) {
        ArrayList<Recognition> nmsRecognitions = new ArrayList<Recognition>();

        // ??????????????????, ?????????????????????nms
        for (int i = 0; i < OUTPUT_SIZE[2]-5; i++) {
            // ????????????????????????????????????, ???labelScore???????????????
            PriorityQueue<Recognition> pq =
                    new PriorityQueue<Recognition>(
                            6300,
                            new Comparator<Recognition>() {
                                @Override
                                public int compare(final Recognition l, final Recognition r) {
                                    // Intentionally reversed to put high confidence at the head of the queue.
                                    return Float.compare(r.getConfidence(), l.getConfidence());
                                }
                            });

            // ???????????????????????????, ???obj????????????????????????
            for (int j = 0; j < allRecognitions.size(); ++j) {
//                if (allRecognitions.get(j).getLabelId() == i) {
                if (allRecognitions.get(j).getLabelId() == i && allRecognitions.get(j).getConfidence() > DETECT_THRESHOLD) {
                    pq.add(allRecognitions.get(j));
//                    Log.i("tfliteSupport", allRecognitions.get(j).toString());
                }
            }

            // nms????????????
            while (pq.size() > 0) {
                // ???????????????????????????
                Recognition[] a = new Recognition[pq.size()];
                Recognition[] detections = pq.toArray(a);
                Recognition max = detections[0];
                nmsRecognitions.add(max);
                pq.clear();

                for (int k = 1; k < detections.length; k++) {
                    Recognition detection = detections[k];
                    if (boxIou(max.getLocation(), detection.getLocation()) < IOU_THRESHOLD) {
                        pq.add(detection);
                    }
                }
            }
        }
        return nmsRecognitions;
    }

    protected ArrayList<Recognition> nmsAllClass(ArrayList<Recognition> allRecognitions) {
        ArrayList<Recognition> nmsRecognitions = new ArrayList<Recognition>();

        PriorityQueue<Recognition> pq =
                new PriorityQueue<Recognition>(
                        100,
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(final Recognition l, final Recognition r) {
                                // Intentionally reversed to put high confidence at the head of the queue.
                                return Float.compare(r.getConfidence(), l.getConfidence());
                            }
                        });

        // ???????????????????????????, ???obj????????????????????????
        for (int j = 0; j < allRecognitions.size(); ++j) {
            if (allRecognitions.get(j).getConfidence() > DETECT_THRESHOLD) {
                pq.add(allRecognitions.get(j));
            }
        }

        while (pq.size() > 0) {
            // ???????????????????????????
            Recognition[] a = new Recognition[pq.size()];
            Recognition[] detections = pq.toArray(a);
            Recognition max = detections[0];
            nmsRecognitions.add(max);
            pq.clear();

            for (int k = 1; k < detections.length; k++) {
                Recognition detection = detections[k];
                if (boxIou(max.getLocation(), detection.getLocation()) < IOU_CLASS_DUPLICATED_THRESHOLD) {
                    pq.add(detection);
                }
            }
        }
        return nmsRecognitions;
    }

    protected float boxIou(RectF a, RectF b) {
        float intersection = boxIntersection(a, b);
        float union = boxUnion(a, b);
        if (union <= 0) return 1;
        return intersection / union;
    }

    protected float boxIntersection(RectF a, RectF b) {
        float maxLeft = a.left > b.left ? a.left : b.left;
        float maxTop = a.top > b.top ? a.top : b.top;
        float minRight = a.right < b.right ? a.right : b.right;
        float minBottom = a.bottom < b.bottom ? a.bottom : b.bottom;
        float w = minRight -  maxLeft;
        float h = minBottom - maxTop;

        if (w < 0 || h < 0) return 0;
        float area = w * h;
        return area;
    }

    protected float boxUnion(RectF a, RectF b) {
        float i = boxIntersection(a, b);
        float u = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - i;
        return u;
    }
}
