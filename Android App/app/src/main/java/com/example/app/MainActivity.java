package com.example.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final String LOG_TAG = "SPEECH_RECOG";
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};
//    private MediaRecorder recorder=null;
    private String fileName=null;
    private ProgressBar progressBar=null;
    private TextView textView=null;

    private static final int RECORDER_SAMPLERATE = 8000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private AudioRecord recorder = null;
    private Thread recordingThread = null;
    private boolean isRecording = false;
    private int bufferSize=0;

    private int BufferElements2Rec = 1024;
    private int BytesPerElement = 2;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);

        fileName = Objects.requireNonNull(getExternalCacheDir()).getAbsolutePath();
        fileName += "/temp.pcm";

        ImageButton record=findViewById(R.id.speaker_button);
        progressBar=findViewById(R.id.progress_bar);
        textView=findViewById(R.id.result);

        record.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()){
                    case MotionEvent.ACTION_DOWN: {
                        Toast.makeText(getApplicationContext(), "Started", Toast.LENGTH_SHORT).show();
                        startRecording();
                        return true;
                    }
                    case MotionEvent.ACTION_UP:{
                        Toast.makeText(getApplicationContext(), "Stopped", Toast.LENGTH_SHORT).show();
                        stopRecording();
                        return true;
                    }
                }
                return false;
            }
        });
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);

    }

//    private void startRecording() {
//        recorder = new MediaRecorder();
//        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//        recorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
//        recorder.setOutputFile(fileName);
//        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
//
//        try {
//            recorder.prepare();
//        } catch (IOException e) {
//            Log.e(LOG_TAG, "prepare() failed");
//        }
//
//        recorder.start();
//    }

    private void startRecording(){
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);

        recorder.startRecording();
        isRecording = true;
        recordingThread = new Thread(new Runnable() {
            public void run() {
                writeAudioDataToFile();
            }
        }, "AudioRecorder Thread");
        recordingThread.start();
    }

    private void writeAudioDataToFile() {

        String filePath = fileName;
        short sData[] = new short[BufferElements2Rec];

        FileOutputStream os = null;
        try {
            os = new FileOutputStream(filePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        while (isRecording) {
            recorder.read(sData, 0, BufferElements2Rec);
            try {
                byte bData[] = short2byte(sData);
                os.write(bData, 0, BufferElements2Rec * BytesPerElement);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;
    }

    private void stopRecording(){
        if (null != recorder) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
            RestAPI restAPI=new RestAPI();
            restAPI.execute();
        }
    }


//    private void stopRecording() {
//        recorder.stop();
//        recorder.release();
//        recorder = null;
//        RestAPI restAPI=new RestAPI();
//        restAPI.execute();
//    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }

    public class RestAPI extends AsyncTask<Void,Void,String>{

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressBar.setVisibility(View.VISIBLE);
                    textView.setVisibility(View.INVISIBLE);
                }
            });
        }

        @Override
        protected String doInBackground(Void... voids) {

            File file=new File(fileName);

            OkHttpClient httpClient=new OkHttpClient();

            RequestBody requestBody= new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("recording",fileName,RequestBody.create(MediaType.parse("audio/raw"),file))
                    .build();

            Request request=new Request.Builder().url("http://192.168.1.9:8000/upload")
                    .post(requestBody).build();

            Response response= null;
            try {
                response = httpClient.newCall(request).execute();
                if(response.isSuccessful()){
                    String fetchBody= response.body().string();
                    Log.e(LOG_TAG,fetchBody);
                    return fetchBody;
                }
            } catch (IOException e) {
                return "Network Error";
            }
            return "Oops something went wrong !!!!";
        }

        @Override
        protected void onPostExecute(final String s) {
            super.onPostExecute(s);
            runOnUiThread(new Runnable() {
                @SuppressLint("SetTextI18n")
                @Override
                public void run() {
                    progressBar.setVisibility(View.INVISIBLE);
                    textView.setText("\" "+s+" \"");
                    textView.setVisibility(View.VISIBLE);
                }
            });
        }

    }

}
