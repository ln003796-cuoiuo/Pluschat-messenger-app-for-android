package ru.pluschat.app;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import java.io.File;

/** Records a short AAC/M4A voice note locally. Callers must encrypt bytes before upload. */
public final class VoiceRecorder {
    private MediaRecorder recorder;
    private File output;
    private boolean started;

    public void start(Context context, File target) throws Exception {
        if (started) throw new IllegalStateException("Recording already started");
        output = target;
        recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setMaxDuration(5 * 60 * 1000);
            recorder.setMaxFileSize(10L * 1024L * 1024L);
            recorder.setOutputFile(target.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            started = true;
        } catch (Exception e) {
            release();
            if (target.exists()) target.delete();
            throw e;
        }
    }

    public File stop() {
        if (!started || recorder == null) throw new IllegalStateException("Recording is not active");
        try {
            recorder.stop();
            return output;
        } finally {
            release();
        }
    }

    public void cancel() {
        if (recorder != null && started) {
            try { recorder.stop(); } catch (RuntimeException ignored) {}
        }
        File target = output;
        release();
        if (target != null && target.exists()) target.delete();
    }

    private void release() {
        if (recorder != null) {
            try { recorder.reset(); } catch (RuntimeException ignored) {}
            try { recorder.release(); } catch (RuntimeException ignored) {}
        }
        recorder = null;
        started = false;
    }
}
