package com.example.android.notepad.ui;

import android.app.Dialog;
import android.content.Context;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.android.notepad.R;
import com.example.android.notepad.util.MediaStoreUtil;
import com.example.android.notepad.util.TimeUtils;
import com.google.android.material.button.MaterialButton;

import java.io.File;

/**
 * 录音对话框：MediaRecorder 录制 AAC 音频，停止后把文件交给调用方写入附件表。
 * 录音文件直接落在应用私有 media 目录，避免再复制一次。
 */
public class RecordDialog extends Dialog {

    public interface Listener {
        void onRecorded(@NonNull File file, long durationMs);
    }

    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView textTimer;
    private TextView textHint;
    private MaterialButton buttonToggle;

    private MediaRecorder recorder;
    private File outputFile;
    private long startedAt;
    private boolean recording;
    private boolean delivered;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!recording || textTimer == null) {
                return;
            }
            long elapsed = SystemClock.elapsedRealtime() - startedAt;
            textTimer.setText(TimeUtils.formatDuration(elapsed));
            handler.postDelayed(this, 200);
        }
    };

    public RecordDialog(@NonNull Context context, Listener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_record);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.CENTER;
            params.width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.88f);
            window.setAttributes(params);
        }

        textTimer = findViewById(R.id.text_timer);
        textHint = findViewById(R.id.text_hint);
        buttonToggle = findViewById(R.id.button_toggle);
        MaterialButton buttonCancel = findViewById(R.id.button_cancel);

        buttonToggle.setOnClickListener(v -> {
            if (recording) {
                stopRecording();
            } else {
                startRecording();
            }
        });
        buttonCancel.setOnClickListener(v -> dismiss());

        setOnDismissListener(d -> cleanup());
        setCanceledOnTouchOutside(false);
    }

    private void startRecording() {
        outputFile = new File(MediaStoreUtil.mediaDir(getContext()),
                "REC_" + System.currentTimeMillis() + ".m4a");
        try {
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(outputFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();

            recording = true;
            startedAt = SystemClock.elapsedRealtime();
            textHint.setText(R.string.record_hint_recording);
            buttonToggle.setText(R.string.record_stop);
            textTimer.setText("00:00");
            handler.post(ticker);
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.record_permission_denied, Toast.LENGTH_SHORT).show();
            releaseRecorder();
            if (outputFile != null) {
                //noinspection ResultOfMethodCallIgnored
                outputFile.delete();
            }
            outputFile = null;
        }
    }

    private void stopRecording() {
        if (recorder == null) {
            return;
        }
        long elapsed = SystemClock.elapsedRealtime() - startedAt;
        boolean stopped = true;
        try {
            recorder.stop();
        } catch (RuntimeException e) {
            // 录音时间过短时 stop() 会抛异常
            stopped = false;
        }
        releaseRecorder();
        recording = false;
        handler.removeCallbacks(ticker);

        if (!stopped || outputFile == null || outputFile.length() <= 0 || elapsed < 600) {
            if (outputFile != null) {
                //noinspection ResultOfMethodCallIgnored
                outputFile.delete();
            }
            outputFile = null;
            resetUi();
            Toast.makeText(getContext(), R.string.record_too_short, Toast.LENGTH_SHORT).show();
            return;
        }

        long duration = MediaStoreUtil.durationOf(getContext(), outputFile);
        delivered = true;
        listener.onRecorded(outputFile, duration > 0 ? duration : elapsed);
        dismiss();
    }

    private void resetUi() {
        if (textTimer != null) {
            textTimer.setText("00:00");
        }
        if (textHint != null) {
            textHint.setText(R.string.record_hint_idle);
        }
        if (buttonToggle != null) {
            buttonToggle.setText(R.string.record_start);
        }
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try {
                recorder.release();
            } catch (Exception ignored) {
                // 忽略释放异常
            }
            recorder = null;
        }
    }

    private void cleanup() {
        handler.removeCallbacks(ticker);
        if (recording) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
                // 录制时间过短
            }
            releaseRecorder();
            recording = false;
        }
        if (!delivered && outputFile != null && outputFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outputFile.delete();
        }
    }
}
