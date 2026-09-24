package com.projection.car;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;

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
    private boolean mirrorPermissionRequested;


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

        checkPermission();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, Utils.TAG);


        mLog = findViewById(R.id.log);
        uiLog("eNS1 Mirror Test v0.7 protocol-probe ready");
        bitTxt = findViewById(R.id.bit);
        frameTxt = findViewById(R.id.frame);
        wTxt = findViewById(R.id.w);
        hTxt = findViewById(R.id.h);
        serialTxt = findViewById(R.id.serial);
        mirrorBtn = findViewById(R.id.mirror);
        mirrorBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestMirror();
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

    protected void onActivityResult(int paramInt1, int paramInt2, Intent paramIntent) {
        super.onActivityResult(paramInt1, paramInt2, paramIntent);
        if (paramInt1 == REQUEST_CODE) {
            boolean ok = mMsgProcess.mediaPermissionOk(this, paramInt2, paramIntent);
            uiLog(ok ? "Screen capture active; waiting for HU VIDEO_START"
                    : "Screen capture cancelled/failed");
        }
    }

    private void requestMirror() {
        if (mMsgProcess == null) {
            uiLog("Mirror unavailable: CarLife engine not ready");
            return;
        }
        uiLog("Starting 1280x720 H.264 mirror...");
        mMsgProcess.requestMirrorPermission();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mContext.unregisterReceiver(mUsbReceiver);
    }


    private void openAccessory(UsbAccessory accessory) {
        log("openAccessory");
        uiLog("Opening CarLife USB accessory...");
        if (accessory == null) {
            log("openAccessory skipped: accessory is null");
            return;
        }
        try {
            mFileDescriptor = mUsbManager.openAccessory(accessory);
        } catch (SecurityException e) {
            log("openAccessory permission error: " + e.getMessage());
            return;
        }

        if (mFileDescriptor != null) {
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            log("now usb fd" + fd);
            if (fd != null) {
                FileInputStream mInputStream = new FileInputStream(fd);
                log("accessory opened DataTranPrepared");
                FileOutputStream mOutputStream = new FileOutputStream(fd);

                mMsgProcess.startProjection(mInputStream, mOutputStream);
                uiLog("USB opened. CarLife session started.");
                uiLog("Handshake first: do not start mirror yet.");
                mWakeLock.acquire();//保持屏幕唤醒


            }

            log("accessory opened");
        } else {
            log("accessory open fail");
            uiLog("USB open failed.");
        }
    }

    private void uiLog(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mLog != null) {
                    mLog.setText(text + "\n" + mLog.getText());
                }
            }
        });
    }

    private void checkUSBDevice() {
        log("checkUSBDevice");
        UsbAccessory[] accessories = mUsbManager.getAccessoryList();

        if (accessories == null) {
            log("accessories list is null");
            uiLog("Waiting for Honda CarLife USB...");
            return;
        }

        log("accessories length " + accessories.length);

        UsbAccessory accessory = accessories[0];
        if (accessory != null) {
            log("accessories not null");
            if (mUsbManager.hasPermission(accessory)) {

                mUsbAccessory = accessory;
                openAccessory(mUsbAccessory);
            } else {
                log("accessories null per");
                int pendingFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0;
                PendingIntent mPermissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), pendingFlags);
                mUsbManager.requestPermission(accessory, mPermissionIntent);
            }
        } else {
            log("accessories null");
        }
    }


    private void checkPermission() {
        if (Build.VERSION.SDK_INT < 21) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
//            builder.setTitle("权限申请");
            builder.setMessage("应用需要android 5.1 版本以上运行");
//            builder.setPositiveButton("退出", new DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialog, int which) {
//                    finish();
//                }
//            });
            builder.setNegativeButton("退出", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });

            builder.setCancelable(false);
            builder.show();
        }

        if (Build.VERSION.SDK_INT < 24) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
//            builder.setTitle("权限申请");
            builder.setMessage("车机反控功能需要android 7.0版本及以上");
            builder.setPositiveButton("了解", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
            builder.setNegativeButton("退出", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });

            builder.setCancelable(false);
            builder.show();
        }
    }

}
