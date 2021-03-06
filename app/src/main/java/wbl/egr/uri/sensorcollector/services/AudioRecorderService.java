package wbl.egr.uri.sensorcollector.services;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;
import wbl.egr.uri.sensorcollector.activities.SettingsActivity;
import wbl.egr.uri.sensorcollector.models.AudioRecordModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by Matt Constant on 2/23/17.
 *
 * AudioRecorderService
 *
 * This service activates the audio recording feature of the MicrosoftBand, stores the audio
 * into a WAV file, and puts that file into its appropriate folder in the .anEAR folder of the
 * phone's SD card.
 *
 * This also creates and writes to the AudioRecordLog.csv file
 */

public class AudioRecorderService extends Service {
    private static final String HEADER = "Start Date,Start Time,End Date,End Time,File Name,File Size (bytes),Triggered?";

    public static final String INTENT_TEMP_FILE = "intent_temp_file";
    public static final String INTENT_WAV_FILE = "intent_wav_file";
    public static final String INTENT_LOG_FILE = "intent_log_file";
    public static final String INTENT_AUDIO_TRIGGER = "intent_audio_trigger";
    public static final String INTENT_AUDIO_SAMPLE = "intent_audio_sample";
    private static final int NOTIFICATION_ID = 44;

    private boolean mRecording;
    private boolean mSample;

    private int mAudioSource;
    private int mSampleRate;
    private int mChannelConfig;
    private int mAudioEncoding;
    private int mBufferSize;
    private AudioRecord mAudioRecord;
    private File mTempFile;
    private File mWavFile;
    private File mLogFile;
    private CountDownTimer mCountDownTimer;
    private AudioRecordModel mAudioRecordModel;

    // This controls how long the audio should be recorded for
    @Override
    public void onCreate()
    {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /*  This creates both the WAV file and the temporary file, and begins
     *  the recording process.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        if (intent == null || !intent.hasExtra(INTENT_TEMP_FILE) || !intent.hasExtra(INTENT_WAV_FILE) || !intent.hasExtra(INTENT_LOG_FILE) || !intent.hasExtra(INTENT_AUDIO_TRIGGER)) {
            return START_NOT_STICKY;
        }

        if (mAudioRecord != null) {
            //Audio recording already in session
            return START_NOT_STICKY;
        }

        mTempFile = new File(intent.getStringExtra(INTENT_TEMP_FILE));
        mWavFile = new File(intent.getStringExtra(INTENT_WAV_FILE));
        mLogFile = new File(intent.getStringExtra(INTENT_LOG_FILE));
        mSample = intent.getBooleanExtra(INTENT_AUDIO_SAMPLE, false);

        mAudioRecordModel = new AudioRecordModel(new Date());
        mAudioRecordModel.setTriggered(intent.getBooleanExtra(INTENT_AUDIO_TRIGGER, false));
        mAudioRecordModel.setFileName(mWavFile.getName());

        if (mTempFile.exists()) {
            mTempFile.delete();
        }
        if (mWavFile.exists()) {
            mWavFile.delete();
        }
        try {
            Log.d(this.getClass().getSimpleName(), "Creating files (" + mTempFile.getAbsolutePath() + ")");
            if (!mTempFile.createNewFile()) {
                Log.d(this.getClass().getSimpleName(), "Could not create Temp File");
            }
            mWavFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        startRecording();
        int duration;
        if(mSample == false)
            duration = Integer.parseInt(SettingsActivity.getString(this, SettingsActivity.KEY_AUDIO_DURATION, "30")) * 1000;

        else
            duration = 300000;

        mCountDownTimer = new CountDownTimer(duration, duration) {
            @Override
            public void onTick(long l) {

            }

            @Override
            public void onFinish() {
                stopSelf();
            }
        }.start();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopRecording();
        mCountDownTimer.cancel();
    }

    // Starts the MicrosoftBand's audio recording feature
    private void startRecording() {
        prepareAudioRecord();
        if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            return;
        }
        mAudioRecord.startRecording();
        saveRawAudio();
        mRecording = true;
    }

    // Stops the MicrosoftBand's audio recording feature
    private void stopRecording() {
        mRecording = false;
        mAudioRecordModel.setEndDate(new Date());
        releaseAudioRecord();
        processRawAudio();
    }

    //  Creates the parameters in which the audio recorder must follow to create the WAV file
    private void prepareAudioRecord() {
        if (mAudioRecord != null) {
            releaseAudioRecord();
        }

        mAudioSource = MediaRecorder.AudioSource.MIC;
        mSampleRate = 44100;
        mChannelConfig = AudioFormat.CHANNEL_IN_MONO;
        mAudioEncoding = AudioFormat.ENCODING_PCM_16BIT;
        mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelConfig, mAudioEncoding);
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelConfig, mAudioEncoding, mBufferSize);
    }

    //  Releases all of the information that the audio recorder has stored
    private void releaseAudioRecord() {
        if (mAudioRecord == null) {
            return;
        }

        mAudioRecord.stop();
        mAudioRecord.release();
        mAudioRecord = null;
    }

    // Saves the information to a WAV file
    private void saveRawAudio() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FileOutputStream fileOutputStream = new FileOutputStream(mTempFile);
                    byte[] audioData = new byte[mBufferSize];
                    while (mRecording && mAudioRecord.read(audioData, 0, mBufferSize) != -1) {
                        fileOutputStream.write(audioData);
                    }
                    fileOutputStream.flush();
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, "Audio Streaming Thread").start();
    }

    //  Processes the audio data, writes it to a file, and deletes the temporary file
    private void processRawAudio() {
        try {
            FileInputStream fileInputStream = new FileInputStream(mTempFile);
            FileOutputStream fileOutputStream = new FileOutputStream(mWavFile);

            writeHeader(fileInputStream, fileOutputStream);
            byte[] audioData = new byte[2048];
            while (fileInputStream.read(audioData, 0, 2048) != -1) {
                fileOutputStream.write(audioData);
            }
            fileOutputStream.flush();
            fileOutputStream.close();
            fileInputStream.close();

            mTempFile.delete();

            //Log Audio Session
            mAudioRecordModel.setEndDate(Calendar.getInstance().getTime());
            mAudioRecordModel.setFileSize(mWavFile.length());
            DataLogService.log(this, mLogFile, mAudioRecordModel.generateContents(), HEADER);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeHeader(FileInputStream inputStream, FileOutputStream outputStream) {
        byte[] header = new byte[44];
        int channels = 1;
        long longSampleRate = 44100;
        long byteRate = 16 * longSampleRate * channels/8;
        long totalDataLen, totalAudioLen;
        try {
            totalAudioLen = inputStream.getChannel().size();
        } catch (IOException e) {
            totalAudioLen = 0;
            e.printStackTrace();
        }
        totalDataLen = totalAudioLen + 36;

        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = 16; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        try {
            outputStream.write(header);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
