package com.projection.car;

import android.app.PendingIntent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.projection.car.Utils.REQUEST_CODE;
import static com.projection.car.Utils.log;

public class MainActivity extends AppCompatActivity {


    private static final String ACTION_USB_PERMISSION = "org.ammlab.android.app.helloadk.action.USB_PERMISSION";


    private Context mContext;

    private UsbManager mUsbManager;
    private UsbAccessory mUsbAccessory;
    private ParcelFileDescriptor mFileDescriptor;


    private int mVideoBit = 0;
    private int mVideoFrame = 0;

    private PowerManager.WakeLock mWakeLock;

    private MsgProcess mMsgProcess;


    private TextView mLog;
    private EditText bitTxt, frameTxt;
    private TextView wTxt, hTxt, serialTxt;
    private Button mirrorBtn;
    private Button shareLogBtn;
    private Button sharePreviousLogBtn;
    private File previousLogFile;
    private boolean mirrorPermissionRequested;

    private final Object logFileLock = new Object();
    private BufferedWriter sessionLogWriter;
    private File sessionLogFile;
    private final SimpleDateFormat logTimeFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);


    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            log("receive accessory_filter connect broadcast:" + action);

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

                    //获取accessory句柄成功
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        log("prepare to open accessory_filter stream");

                        mUsbAccessory = accessory;
                        openAccessory(mUsbAccessory);

                    } else {
                        log("permission denied for accessory " + accessory);

                        mUsbAccessory = null;

                    }
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {

                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }

                UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                log("USB_ACCESSORY_DETACHED " + accessory);
                mUsbAccessory = null;

                mMsgProcess.resetUsb();

                System.exit(0);

            } else if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
                UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                mUsbAccessory = accessory;
                log("USB_ACCESSORY_ATTACHED " + accessory);
                openAccessory(accessory);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;

        previousLogFile = findLatestPreviousLog();
        startSessionLog();
        Utils.setLogSink(new Utils.LogSink() {
            @Override
            public void onLog(String line) {
                appendSessionLog("RAW", line);
            }
        });

        checkPermission();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2208);
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, Utils.TAG);


        mLog = findViewById(R.id.log);
        uiLog("eNS1 Car UI stage 1 ready");
        uiLog("Log file: " + (sessionLogFile == null ? "unavailable" : sessionLogFile.getName()));
        bitTxt = findViewById(R.id.bit);
        frameTxt = findViewById(R.id.frame);
        wTxt = findViewById(R.id.w);
        hTxt = findViewById(R.id.h);
        serialTxt = findViewById(R.id.serial);
        mirrorBtn = findViewById(R.id.mirror);
        shareLogBtn = findViewById(R.id.share_log);
        sharePreviousLogBtn = findViewById(R.id.share_previous_log);
        shareLogBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareSessionLog();
            }
        });
        sharePreviousLogBtn.setEnabled(previousLogFile != null && previousLogFile.exists());
        sharePreviousLogBtn.setText(previousLogFile == null ? "NO PREVIOUS LOG" : "SHARE PREVIOUS LOG");
        sharePreviousLogBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shareLogFile(previousLogFile, "previous"); }
        });
        mirrorBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMsgProcess.startCarUi();
            }
        });

        final SharedPreferences sharedPreferences = getSharedPreferences("set", MODE_PRIVATE);
        mVideoBit = sharedPreferences.getInt("bit", 30);
        mVideoFrame = sharedPreferences.getInt("frame", 3000000);
        bitTxt.setText(mVideoBit + "");
        frameTxt.setText(mVideoFrame + "");

        findViewById(R.id.config).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String bit = bitTxt.getText().toString();
                String frame = frameTxt.getText().toString();
                sharedPreferences.edit().putInt("bit", Integer.parseInt(bit)).commit();
                sharedPreferences.edit().putInt("frame", Integer.parseInt(frame)).commit();
                uiLog("Video settings saved");


            }
        });
        mMsgProcess = new MsgProcess(this, mVideoBit, mVideoFrame, new MsgProcess.InfoListener() {
            @Override
            public void onVISSize(int x, int y) {
                wTxt.setText("Video width: " + x);
                hTxt.setText("Video height: " + y);
                uiLog("Video target: " + x + " x " + y);
            }

            @Override
            public void onVISID(String id) {
                serialTxt.setText("HU id: " + id);
                uiLog("Head unit id: " + id);
            }

            @Override
            public void onProtocolEvent(final String event) {
                uiLog(event);
            }
        });


        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
//        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        mContext.registerReceiver(mUsbReceiver, filter);
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);


        checkUSBDevice();

    }

    // Car UI mode does not use MediaProjection or screen-capture permissions.
    private void requestMirror() {
        if (mMsgProcess != null) {
            uiLog("Starting native Car UI...");
            mMsgProcess.startCarUi();
        }
    }


