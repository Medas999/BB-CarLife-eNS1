package com.projection.car;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import androidx.annotation.NonNull;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.baidu.carlife.protobuf.CarlifeCarHardKeyCodeProto;
import com.baidu.carlife.protobuf.CarlifeFeatureConfigListProto;
import com.baidu.carlife.protobuf.CarlifeVehicleInfoListProto;
import com.baidu.carlife.protobuf.CarlifeMusicInitProto;
import com.baidu.carlife.protobuf.CarlifeModuleStatusListProto;
import com.baidu.carlife.protobuf.CarlifeModuleStatusProto;
import com.baidu.carlife.protobuf.CarlifeSubscribeMobileCarLifeInfoListProto;
import com.baidu.carlife.protobuf.CarlifeTouchActionProto;
import com.example.car.CarlifeAuthenResultProto;
import com.example.car.CarlifeDeviceInfoProto;
import com.example.car.CarlifeProtocolVersionMatchStatusProto;
import com.example.car.CarlifeProtocolVersionProto;
import com.example.car.CarlifeStatisticsInfoProto;
import com.example.car.CarlifeVideoEncoderInfoProto;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static android.content.Context.MODE_PRIVATE;
import static com.projection.car.Utils.ACTION_DOWN;
import static com.projection.car.Utils.ACTION_MOVE;
import static com.projection.car.Utils.ACTION_UP;
import static com.projection.car.Utils.CMD;
import static com.projection.car.Utils.KEYCODE_SEEK_ADD;
import static com.projection.car.Utils.KEYCODE_SEEK_SUB;
import static com.projection.car.Utils.MEDIA;
import static com.projection.car.Utils.MSG_CMD_FOREGROUND;
import static com.projection.car.Utils.MSG_CMD_CARLIFE_DATA_SUBSCRIBE;
import static com.projection.car.Utils.MSG_CMD_CARLIFE_DATA_SUBSCRIBE_DONE;
import static com.projection.car.Utils.MSG_CMD_CAR_DATA_SUBSCRIBE_REQ;
import static com.projection.car.Utils.MSG_CMD_CAR_DATA_SUBSCRIBE_RSP;
import static com.projection.car.Utils.MSG_CMD_HU_INFO;
import static com.projection.car.Utils.MSG_CMD_HU_FEATURE_CONFIG_RESPONSE;
import static com.projection.car.Utils.MSG_CMD_HU_RSA_PUBLIC_KEY_RESPONSE;
import static com.projection.car.Utils.MSG_CMD_HU_PROTOCOL_VERSION;
import static com.projection.car.Utils.MSG_CMD_MD_AUTHEN_RESULT;
import static com.projection.car.Utils.MSG_CMD_MD_FEATURE_CONFIG_REQUEST;
import static com.projection.car.Utils.MSG_CMD_MD_RSA_PUBLIC_KEY_REQUEST;
import static com.projection.car.Utils.MSG_CMD_MD_INFO;
import static com.projection.car.Utils.MSG_CMD_PROTOCOL_VERSION_MATCH_STATUS;
import static com.projection.car.Utils.MSG_CMD_SCREEN_ON;
import static com.projection.car.Utils.MSG_CMD_STATISTIC_INFO;
import static com.projection.car.Utils.MSG_CMD_VIDEO_ENCODER_INIT;
import static com.projection.car.Utils.MSG_CMD_VIDEO_ENCODER_INIT_DONE;
import static com.projection.car.Utils.MSG_CMD_VIDEO_ENCODER_START;
import static com.projection.car.Utils.MSG_MEDIA_DATA;
import static com.projection.car.Utils.MSG_MEDIA_INIT;
import static com.projection.car.Utils.MSG_TOUCH_ACTION;
import static com.projection.car.Utils.MSG_TOUCH_CAR_HARD_KEY_CODE;
import static com.projection.car.Utils.MSG_VIDEO_DATA;
import static com.projection.car.Utils.MSG_WRITE_AUDIO;
import static com.projection.car.Utils.MSG_WRITE_VIDEO;
import static com.projection.car.Utils.REQUEST_CODE;
import static com.projection.car.Utils.TOUCH;
import static com.projection.car.Utils.VIDEO;
import static com.projection.car.Utils.bytesToInt2;
import static com.projection.car.Utils.bytesToShort2;
import static com.projection.car.Utils.exportCMDMsg;
import static com.projection.car.Utils.exportVideoMsg;
import static com.projection.car.Utils.intToBytes2;
import static com.projection.car.Utils.log;

public class MsgProcess {

    private volatile boolean usbOk;
    private volatile boolean mirrorRequested;
    private volatile boolean huVideoStarted;
    private volatile boolean mdInfoSent;
    private volatile boolean moduleStatusSent;
    private volatile boolean featureConfigRequested;
    private volatile boolean carDataSubscribeRequested;
    private volatile int huProtocolMajor = 1;
    private volatile int protocolProbeAttempt;
    private long videoTxCount;
    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;
    private Activity mContext;
    private byte[] mMdInfoPayload;


    private Handler mUsbReadHandler;
    private Handler mUsbWriteHandler;


    private MediaCodecTool mMediaCodecTool;

    private Path mGesturePath = new Path();
    private int mGestureMoveCount = 0;
    private ArrayList<Float> mGestureMoveArray = new ArrayList<>();
    private long mGestureStartTime = 0;

    private float mVISWidth = 1280;
    private float mVISHeight = 720;
    private float mMobileWidth = 1920;
    private float mMobileHeight = 1080;
    private float mPortraitScreenVISGestureFactorW = 1.0f;
    private float mPortraitScreenVISGestureFactorH = 1.0f;
    private float mLandscapeScreenVISGestureFactorW = 1.0f;
    private float mLandscapeScreenVISGestureFactorH = 1.0f;

    private float mLeft_x;
    private Handler mMainHandler = new Handler();

    private int mVideoBit = 0;
    private int mVideoFrame = 0;
    private InfoListener mInfoListener;

    MsgProcess(Activity context, int bit, int frame, InfoListener infoListener) {

        mContext = context;
        mInfoListener = infoListener;
        mVideoBit = bit;
        mVideoFrame = frame;

        refreshSize();

        mMediaCodecTool = new MediaCodecTool();
        mMediaCodecTool.setContext(context);
        mMdInfoPayload = buildMdInfoPayload();

        startUsbTransferThread();

    }

    public void startProjection(FileInputStream in, FileOutputStream out) {
        log("startProjection");
        usbOk = true;
        huVideoStarted = false;
        mirrorRequested = false;
        mdInfoSent = false;
        moduleStatusSent = false;
        featureConfigRequested = false;
        carDataSubscribeRequested = false;
        protocolProbeAttempt = 0;
        mInputStream = in;
        mOutputStream = out;
        mInfoListener.onProtocolEvent("USB read loop starting");
        mUsbReadHandler.sendEmptyMessage(0);

    }

    public void startCarUi() {
        if (mMediaCodecTool.isProjectionActive()) return;
        mInfoListener.onProtocolEvent("Starting native Car UI renderer");
        mMediaCodecTool.startCarUi(videoDataEncodeListener, mVISWidth, mVISHeight, mVideoBit, mVideoFrame);
    }

    public void requestMirrorPermission() { startCarUi(); }
    public boolean mediaPermissionOk(Activity activity, int resultCode, Intent data) { return mMediaCodecTool.isProjectionActive(); }

    public synchronized void resetUsb() {
        if (usbOk) {
            log("resetUsb");
            usbOk = false;
            huVideoStarted = false;
            mMediaCodecTool.stopProjection();
            mUsbWriteHandler.removeCallbacksAndMessages(null);
        }

    }


    private MediaCodecTool.VideoDataEncodeListener videoDataEncodeListener = new MediaCodecTool.VideoDataEncodeListener() {
        @Override
        public void onData(byte[] data) {
            if (!usbOk || !huVideoStarted) {
                return;
            }
            try {
//                                        log("data len = " + data.length);
                videoTxCount++;
                log("VIDEO QUEUE #" + videoTxCount + " h264Bytes=" + data.length);
                byte[] carLifeMsg = exportVideoMsg(MSG_VIDEO_DATA, data);
                byte[] headmsg = new byte[8];
                headmsg[3] = VIDEO;
                intToBytes2(carLifeMsg.length, headmsg, 4);//carlifemsg len
                CarMsg carMsg = new CarMsg(headmsg, carLifeMsg);
                // Real-time mirroring: never let stale video frames accumulate.
                mUsbWriteHandler.removeMessages(MSG_WRITE_VIDEO);
                mUsbWriteHandler.obtainMessage(MSG_WRITE_VIDEO, carMsg).sendToTarget();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onAudioData(byte[] pcm) {
            if (!usbOk || !huVideoStarted || pcm == null || pcm.length == 0) return;
            try {
                byte[] carLifeMsg = exportVideoMsg(MSG_MEDIA_DATA, pcm);
                byte[] headmsg = new byte[8];
                headmsg[3] = MEDIA;
                intToBytes2(carLifeMsg.length, headmsg, 4);
                mUsbWriteHandler.obtainMessage(MSG_WRITE_AUDIO, new CarMsg(headmsg, carLifeMsg)).sendToTarget();
            } catch (Throwable t) {
                log("AUDIO QUEUE ERROR: " + t);
            }
        }
    };

    private void refreshSize() {

        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mInfoListener.onVISSize((int) mVISWidth, (int) mVISHeight);

            }
        });


        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager manager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        manager.getDefaultDisplay().getRealMetrics(metrics);
        log("www = " + metrics.widthPixels + "  hhh = " + metrics.heightPixels);
        mMobileWidth = metrics.widthPixels;
        mMobileHeight = metrics.heightPixels;
        final SharedPreferences sharedPreferences = mContext.getSharedPreferences("set", MODE_PRIVATE);
        mMobileWidth = sharedPreferences.getFloat("mobile_w", (float) mMobileWidth);
        mMobileHeight = sharedPreferences.getFloat("mobile_h", (float) mMobileHeight);
        mLandscapeScreenVISGestureFactorW = mMobileWidth / mVISWidth;
        mLandscapeScreenVISGestureFactorH = mMobileHeight / mVISHeight;

        float portrixScrennWidth = mVISWidth * mVISHeight / mMobileWidth;// 车机竖屏的实际宽 用车机的高做投屏的高，保持比例
        mPortraitScreenVISGestureFactorW = mMobileHeight / (portrixScrennWidth);//竖屏下宽带除车机投屏实际屏幕宽度
        mPortraitScreenVISGestureFactorH = mMobileWidth / mVISHeight;

        mLeft_x = (mVISWidth - portrixScrennWidth) / 2.0f; //界面偏移值
        log("refreshSize w " + mMobileWidth + " h = " + mMobileHeight + ", mVISWidth " + mVISWidth + "mVISHeight" + mVISHeight + ", mirror = " + mPortraitScreenVISGestureFactorW + ", " + mPortraitScreenVISGestureFactorH +
                mLandscapeScreenVISGestureFactorW + ", " + mLandscapeScreenVISGestureFactorH + ", leftx " + mLeft_x);
    }

    private void genarateGesture(int type, float g_x, float g_y) {
        log("Touch received from HU: type=" + type + ", x=" + g_x + ", y=" + g_y);
        mMediaCodecTool.onCarTouch(type, g_x, g_y);
    }

    private int readFully(FileInputStream in, byte[] buffer, int length) throws IOException {
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new IOException("USB stream closed");
            }
            if (read == 0) {
                continue;
            }
            offset += read;
        }
        return offset;
    }

    private byte[] buildMdInfoPayload() {
        CarlifeDeviceInfoProto.CarlifeDeviceInfo.Builder builder =
                CarlifeDeviceInfoProto.CarlifeDeviceInfo.newBuilder();
        try { builder.setOs("Android"); } catch (Throwable ignored) {}
        try { builder.setBoard(Build.BOARD); } catch (Throwable ignored) {}
        try { builder.setBootloader(Build.BOOTLOADER); } catch (Throwable ignored) {}
        try { builder.setBrand(Build.BRAND); } catch (Throwable ignored) {}
        try { builder.setCpuAbi(Build.CPU_ABI); } catch (Throwable ignored) {}
        try { builder.setCpuAbi2(Build.CPU_ABI2); } catch (Throwable ignored) {}
        try { builder.setDevice(Build.DEVICE); } catch (Throwable ignored) {}
        try { builder.setDisplay(Build.DISPLAY); } catch (Throwable ignored) {}
        try { builder.setFingerprint(Build.FINGERPRINT); } catch (Throwable ignored) {}
        try { builder.setHardware(Build.HARDWARE); } catch (Throwable ignored) {}
        try { builder.setHost(Build.HOST); } catch (Throwable ignored) {}
        try { builder.setCid(Build.ID); } catch (Throwable ignored) {}
        try { builder.setManufacturer(Build.MANUFACTURER); } catch (Throwable ignored) {}
        try { builder.setModel(Build.MODEL); } catch (Throwable ignored) {}
        try { builder.setProduct(Build.PRODUCT); } catch (Throwable ignored) {}
        try { builder.setSerial(Build.SERIAL == null ? "unknown" : Build.SERIAL); } catch (Throwable ignored) {
            builder.setSerial("unknown");
        }
        try { builder.setCodename(Build.VERSION.CODENAME); } catch (Throwable ignored) {}
        try { builder.setIncremental(Build.VERSION.INCREMENTAL); } catch (Throwable ignored) {}
        try { builder.setRelease(Build.VERSION.RELEASE); } catch (Throwable ignored) {}
        try { builder.setSdk(Build.VERSION.SDK); } catch (Throwable ignored) {}
        try { builder.setSdkInt(Build.VERSION.SDK_INT); } catch (Throwable ignored) {}
        try { builder.setBtaddress("unknown"); } catch (Throwable ignored) {}
        try { builder.setBtname(Build.MODEL); } catch (Throwable ignored) {}
        return builder.build().toByteArray();
    }

    private byte[] buildModuleStatusPayload() {
        CarlifeModuleStatusListProto.CarlifeModuleStatusList.Builder list =
                CarlifeModuleStatusListProto.CarlifeModuleStatusList.newBuilder();
        int[] modules = new int[] {1, 2, 3, 4, 6, 8, 9};
        for (int moduleId : modules) {
            CarlifeModuleStatusProto.CarlifeModuleStatus status =
                    CarlifeModuleStatusProto.CarlifeModuleStatus.newBuilder()
                            .setModuleID(moduleId)
                            .setStatusID(0)
                            .build();
            list.addModuleStatus(status);
        }
        list.setCnt(list.getModuleStatusCount());
        return list.build().toByteArray();
    }

    private static String hex(byte[] data) {
        if (data == null) return "<null>";
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (byte b : data) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    private void sendCmdDirect(int serviceType, byte[] payload) throws IOException {
        byte[] inner = exportCMDMsg(serviceType, payload, 0);
        byte[] outer = new byte[8];
        outer[3] = CMD;
        intToBytes2(inner.length, outer, 4);
        long t = System.nanoTime();
        log(String.format("TRACE TX CMD type=0x%08X payloadLen=%d reserved=0", serviceType,
                payload == null ? 0 : payload.length));
        log("TRACE TX OUTER HEX = " + hex(outer));
        log("TRACE TX INNER HEX = " + hex(inner));
        if (payload != null) log("TRACE TX PAYLOAD HEX = " + hex(payload));
        mOutputStream.write(outer);
        mOutputStream.write(inner);
        mOutputStream.flush();
        log(String.format("TRACE TX DONE type=0x%08X elapsed=%dus", serviceType,
                (System.nanoTime() - t) / 1000L));
    }

    private void sendPhoneV2HuInfoFollowups() throws IOException {
        if (!featureConfigRequested) {
            sendCmdDirect(MSG_CMD_MD_FEATURE_CONFIG_REQUEST, null);
            featureConfigRequested = true;
            log("TX original-phone flow FEATURE_CONFIG_REQUEST 0x00010051");
            mInfoListener.onProtocolEvent("TX feature config request");
        }
        if (!carDataSubscribeRequested) {
            sendCmdDirect(MSG_CMD_CAR_DATA_SUBSCRIBE_REQ, null);
            carDataSubscribeRequested = true;
            log("TX original-phone flow CAR_DATA_SUBSCRIBE_REQ 0x00010031");
            mInfoListener.onProtocolEvent("TX car data subscribe request");
        }
    }

    private void scheduleOfficialModuleStatus() {
        if (moduleStatusSent) {
            return;
        }
        moduleStatusSent = true;
        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!usbOk || mOutputStream == null) {
                    return;
                }
                try {
                    byte[] payload = buildModuleStatusPayload();
                    sendCmdDirect(0x00010026, payload);
                    log("TX original-phone flow MODULE_STATUS_LIST 0x00010026 payload=" + payload.length);
                    mInfoListener.onProtocolEvent("TX module status list (original v2 flow)");
                } catch (Exception e) {
                    log("TX module status failed: " + e.getMessage());
                }
            }
        }, 500);
    }

    /**
     * Send the exact legacy/official CarLife v2 VERSION_MATCH_STATUS packet with the
     * minimum possible latency.  Older Baidu phone code supports HU protocol v2.0
     * and responds with only { matchStatus = 1 }; AOA sends its 8-byte mux header
     * and the CarLife command as two consecutive FileOutputStream writes.
     */
    private long sendOfficialProtocolMatchFast() throws IOException {
        final byte[] outer = new byte[] {
                0, 0, 0, CMD,
                0, 0, 0, 10
        };
        final byte[] inner = new byte[] {
                0, 2, 0, 0,
                0, 1, 0, 2,
                8, 1
        };
        final long started = System.nanoTime();
        mOutputStream.write(outer);
        mOutputStream.write(inner);

        // Baidu's Android phone client that supports protocol 2.0 immediately sends
        // MD_INFO after VERSION_MATCH_STATUS, before HU_INFO arrives.
        if (!mdInfoSent) {
            sendCmdDirect(MSG_CMD_MD_INFO, mMdInfoPayload);
            mdInfoSent = true;
            scheduleOfficialModuleStatus();
        }

        return (System.nanoTime() - started) / 1000L;
    }

    private void startUsbTransferThread() {
        HandlerThread inthread = new HandlerThread("read");
        inthread.start();
        mUsbReadHandler = new Handler(inthread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case 0: {
//                        mMainHandler.postDelayed(runnable_toast,300000 - 3000);
//                        mMainHandler.postDelayed(runnable,300000);
                        while (usbOk) {
                            try {
                                byte[] data = new byte[8];
                                int len = readFully(mInputStream, data, data.length);

                                if (len == 8) {
                                    int msg_type = data[3];
                                    int msgLen = bytesToInt2(data, 4);
                                    if (msgLen < 8 || msgLen > (64 * 1024 * 1024)) {
                                        throw new IOException("Invalid CarLife outer length " + msgLen);
                                    }
                                    byte[] msgdata = new byte[msgLen];
                                    len = readFully(mInputStream, msgdata, msgdata.length);

                                    int carmsgLenUnsigned = bytesToShort2(msgdata, 0) & 0xFFFF;
                                    int innerReserved = bytesToShort2(msgdata, 2) & 0xFFFF;
                                    int type = bytesToInt2(msgdata, 4);
                                    if (carmsgLenUnsigned > msgLen - 8) {
                                        throw new IOException("Invalid CarLife payload length " + carmsgLenUnsigned +
                                                " for outer body " + msgLen);
                                    }

                                    // Critical timing probe: reply before file/UI logging, protobuf parsing,
                                    // HandlerThread scheduling, or any other work.
                                    long fastTxMicros = -1L;
                                    if (msg_type == CMD && type == MSG_CMD_HU_PROTOCOL_VERSION) {
                                        fastTxMicros = sendOfficialProtocolMatchFast();
                                    }

                                    log("msg_type = " + msg_type + ", read data = " + Arrays.toString(data));
                                    mInfoListener.onProtocolEvent(String.format("USB RX outer type=%d len=%d", msg_type, msgLen));
                                    log("msgLen = " + msgLen);
                                    log("read data = " + Arrays.toString(msgdata));
                                    log("TRACE RX OUTER HEX = " + hex(data));
                                    log("TRACE RX INNER HEX = " + hex(msgdata));
                                    log("read msg data = " + len + " msgLen " + msgLen);
                                    log("read carmsgLen data = " + carmsgLenUnsigned + " reserved " + innerReserved + " type " + type);
                                    if (msg_type == CMD) {
                                        mInfoListener.onProtocolEvent(String.format("RX CMD 0x%08X reserved=%d payload=%d",
                                                type, innerReserved, carmsgLenUnsigned));
                                    } else {
                                        mInfoListener.onProtocolEvent(String.format("RX channel=%d type=0x%08X reserved=%d payload=%d",
                                                msg_type, type, innerReserved, carmsgLenUnsigned));
                                    }
                                    if (fastTxMicros >= 0) {
                                        log("FAST TX exact packet outer=[0,0,0,1,0,0,0,10] inner=[0,2,0,0,0,1,0,2,8,1] in " +
                                                fastTxMicros + " us");
                                        mInfoListener.onProtocolEvent("FAST TX STATUS=1 + MD_INFO in " + fastTxMicros + " us");
                                    }

                                    byte[] carmsg = new byte[carmsgLenUnsigned];
                                    System.arraycopy(msgdata, 8, carmsg, 0, carmsgLenUnsigned);
                                    log(String.format("TRACE RX FRAME channel=%d type=0x%08X reserved=%d payloadLen=%d",
                                            msg_type, type, innerReserved, carmsgLenUnsigned));
                                    log("TRACE RX PAYLOAD HEX = " + hex(carmsg));
                                    msgdata = carmsg;
                                    if (msg_type == CMD) {
                                        switch (type) {
                                            case MSG_CMD_HU_PROTOCOL_VERSION: {
                                                int huMinor = 0;
                                                try {
                                                    CarlifeProtocolVersionProto.CarlifeProtocolVersion version =
                                                            CarlifeProtocolVersionProto.CarlifeProtocolVersion.parseFrom(msgdata);
                                                    huProtocolMajor = version.getMajorVersion();
                                                    huMinor = version.getMinorVersion();
                                                    mInfoListener.onProtocolEvent("HU protocol v" + huProtocolMajor + "." + huMinor +
                                                            " rxReserved=" + innerReserved);
                                                } catch (Exception e) {
                                                    mInfoListener.onProtocolEvent("HU protocol version received (" + msgdata.length +
                                                            " bytes), rxReserved=" + innerReserved);
                                                }
                                                protocolProbeAttempt++;
                                                mInfoListener.onProtocolEvent("Official v2 match already sent inline; retry #" +
                                                        protocolProbeAttempt + " means HU did not advance");
                                            }
                                            break;
                                            case MSG_CMD_HU_INFO: {
                                                try {
                                                    final CarlifeDeviceInfoProto.CarlifeDeviceInfo deviceInfo = CarlifeDeviceInfoProto.CarlifeDeviceInfo.parseFrom(msgdata);
                                                    log("os =" + deviceInfo.getOs() + ", cid =" + deviceInfo.getCid() + ", serial =" + deviceInfo.getSerial());
                                                    mInfoListener.onProtocolEvent("HU info: os=" + deviceInfo.getOs() + ", cid=" + deviceInfo.getCid() + ", serial=" + deviceInfo.getSerial());


                                                } catch (InvalidProtocolBufferException e) {
                                                    e.printStackTrace();
                                                }

                                                if (!mdInfoSent) {
                                                    sendCmdDirect(MSG_CMD_MD_INFO, mMdInfoPayload);
                                                    mdInfoSent = true;
                                                    mInfoListener.onProtocolEvent("TX MD_INFO after HU_INFO fallback");
                                                } else {
                                                    mInfoListener.onProtocolEvent("HU_INFO received; MD_INFO already sent");
                                                }
                                                mInfoListener.onProtocolEvent("HU_INFO parsed; sending documented MD capability requests");
                                                sendPhoneV2HuInfoFollowups();
                                            }
                                            break;
                                            case MSG_CMD_CARLIFE_DATA_SUBSCRIBE: {
                                                try {
                                                    CarlifeSubscribeMobileCarLifeInfoListProto.CarlifeSubscribeMobileCarLifeInfoList subscribeList =
                                                            CarlifeSubscribeMobileCarLifeInfoListProto.CarlifeSubscribeMobileCarLifeInfoList.parseFrom(msgdata);
                                                    mInfoListener.onProtocolEvent("HU data subscribe: cnt=" + subscribeList.getCnt());
                                                } catch (Exception e) {
                                                    mInfoListener.onProtocolEvent("HU data subscribe: " + msgdata.length + " bytes");
                                                }
                                                // The HU request and MD acknowledgement use the same protobuf list.
                                                // Echoing the requested list means we acknowledge exactly the modules the Honda asked for.
                                                mUsbWriteHandler.obtainMessage(MSG_CMD_CARLIFE_DATA_SUBSCRIBE_DONE,
                                                        exportCMDMsg(MSG_CMD_CARLIFE_DATA_SUBSCRIBE_DONE, msgdata)).sendToTarget();
                                                mInfoListener.onProtocolEvent("TX data subscribe done");
                                            }
                                            break;
                                            case MSG_CMD_VIDEO_ENCODER_INIT: {
                                                try {
                                                    CarlifeVideoEncoderInfoProto.CarlifeVideoEncoderInfo encoderInfo = CarlifeVideoEncoderInfoProto.CarlifeVideoEncoderInfo.parseFrom(msgdata);
                                                    log("encoderInfo = " + encoderInfo.getWidth() + ", " + encoderInfo.getHeight() + ", " + encoderInfo.getFrameRate());
                                                    mInfoListener.onProtocolEvent("Video init: " + encoderInfo.getWidth() + "x" + encoderInfo.getHeight() + " @" + encoderInfo.getFrameRate());
                                                    if (encoderInfo.getWidth() > 10 && encoderInfo.getHeight() > 10) {
                                                        mVISWidth = encoderInfo.getWidth();
                                                        mVISHeight = encoderInfo.getHeight();
                                                        refreshSize();
                                                        log("get cheji MirrorWidth = " + mVISWidth + ", MirrorHeight" + mVISHeight);
                                                    }

                                                } catch (InvalidProtocolBufferException e) {
                                                    e.printStackTrace();
                                                }

                                                CarlifeVideoEncoderInfoProto.CarlifeVideoEncoderInfo.Builder builder = CarlifeVideoEncoderInfoProto.CarlifeVideoEncoderInfo.newBuilder();
                                                builder.setFrameRate(mVideoBit);
                                                builder.setWidth((int) mVISWidth);
                                                builder.setHeight((int) mVISHeight);
                                                byte[] videoInitDone = builder.build().toByteArray();
                                                mUsbWriteHandler.obtainMessage(MSG_CMD_VIDEO_ENCODER_INIT_DONE,
                                                        exportCMDMsg(MSG_CMD_VIDEO_ENCODER_INIT_DONE, videoInitDone)).sendToTarget();
                                                mInfoListener.onProtocolEvent("TX video init done: " + (int) mVISWidth + "x" + (int) mVISHeight + " @" + mVideoBit);


                                            }
                                            break;
                                            case MSG_CMD_VIDEO_ENCODER_START: {
                                                huVideoStarted = true;
                                                mInfoListener.onProtocolEvent("HU requested video start -> H.264 enabled");
                                                mUsbWriteHandler.obtainMessage(MSG_CMD_VIDEO_ENCODER_START).sendToTarget();
                                            }
                                            break;
                                            case MSG_CMD_HU_FEATURE_CONFIG_RESPONSE: {
                                                try {
                                                    CarlifeFeatureConfigListProto.CarlifeFeatureConfigList cfg =
                                                            CarlifeFeatureConfigListProto.CarlifeFeatureConfigList.parseFrom(msgdata);
                                                    mInfoListener.onProtocolEvent("HU feature config response: cnt=" + cfg.getCnt());
                                                    log("HU feature config = " + cfg.toString());
                                                } catch (Exception e) {
                                                    mInfoListener.onProtocolEvent("HU feature config response: " + msgdata.length + " bytes");
                                                }
                                            }
                                            break;
                                            case MSG_CMD_CAR_DATA_SUBSCRIBE_RSP: {
                                                try {
                                                    CarlifeVehicleInfoListProto.CarlifeVehicleInfoList info =
                                                            CarlifeVehicleInfoListProto.CarlifeVehicleInfoList.parseFrom(msgdata);
                                                    mInfoListener.onProtocolEvent("HU car data subscribe response: cnt=" + info.getCnt());
                                                    log("HU car data subscribe response = " + info.toString());
                                                } catch (Exception e) {
                                                    mInfoListener.onProtocolEvent("HU car data subscribe response: " + msgdata.length + " bytes");
                                                }
                                            }
                                            break;
                                            case MSG_CMD_HU_RSA_PUBLIC_KEY_RESPONSE: {
                                                // We only need to prove whether this Honda requests encrypted content.
                                                // Full AES negotiation will be added only if this response is actually observed.
                                                mInfoListener.onProtocolEvent("HU RSA public key response: " + msgdata.length + " bytes");
                                                log("HU RSA public key payload = " + Arrays.toString(msgdata));
                                            }
                                            break;
                                            case MSG_CMD_STATISTIC_INFO: {

                                                try {
                                                    final CarlifeStatisticsInfoProto.CarlifeStatisticsInfo statisticsInfo = CarlifeStatisticsInfoProto.CarlifeStatisticsInfo.parseFrom(msgdata);
                                                    log("getCuid = " + statisticsInfo.getCuid() + "" + statisticsInfo.getVersionName() + statisticsInfo.getConnectTime() + statisticsInfo.getCrashLog());
                                                    mInfoListener.onProtocolEvent("STATISTIC_INFO: version=" + statisticsInfo.getVersionName() + ", cuid=" + statisticsInfo.getCuid());
                                                    mMainHandler.post(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            mInfoListener.onVISID(statisticsInfo.getCuid());
                                                        }
                                                    });
                                                } catch (InvalidProtocolBufferException e) {
                                                    e.printStackTrace();
                                                }
                                                mInfoListener.onProtocolEvent("STATISTIC_INFO parsed; no auth response sent; waiting for HU");
                                            }
                                            break;
                                            default: {
                                                mInfoListener.onProtocolEvent(String.format("RX CMD 0x%08X (%d bytes)", type, msgdata.length));
                                            }
                                            break;

                                        }
                                    } else if (msg_type == TOUCH) {
                                        log("read TOUCH data = " + Arrays.toString(msgdata));
                                        switch (type) {
                                            case MSG_TOUCH_CAR_HARD_KEY_CODE: {
                                                CarlifeCarHardKeyCodeProto.CarlifeCarHardKeyCode keyCode = CarlifeCarHardKeyCodeProto.CarlifeCarHardKeyCode.parseFrom(msgdata);
                                                log("keycode = " + keyCode.getKeycode());
                                                switch (keyCode.getKeycode()) {
                                                    case KEYCODE_SEEK_SUB: {
                                                        log("HU previous-track key received");
                                                    }
                                                    break;
                                                    case KEYCODE_SEEK_ADD: {
                                                        log("HU next-track key received");
                                                    }
                                                    break;
                                                }

                                            }
                                            break;
                                            case MSG_TOUCH_ACTION: {
                                                try {
                                                    CarlifeTouchActionProto.CarlifeTouchAction action = CarlifeTouchActionProto.CarlifeTouchAction.parseFrom(msgdata);
                                                    genarateGesture(action.getAction(), action.getX(), action.getY());
                                                    log("encoderInfo = " + action.getX() + ", " + action.getY() + ", " + action.getAction());
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                            break;
                                        }


                                    }


                                } else {
                                    log("read data = " + len + "  " + data.length);
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                                mInfoListener.onProtocolEvent("USB read error: " + e.getClass().getSimpleName() +
                                        (e.getMessage() == null ? "" : " - " + e.getMessage()));
                                resetUsb();
                                break;
                            }

                            //SystemClock.sleep(10);
                        }
                    }
                }
            }
        };

        HandlerThread outthread = new HandlerThread("write");
        outthread.start();
        mUsbWriteHandler = new Handler(outthread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);

                try {
                    switch (msg.what) {
                        case MSG_CMD_PROTOCOL_VERSION_MATCH_STATUS:
                        case MSG_CMD_CARLIFE_DATA_SUBSCRIBE_DONE:
                        case MSG_CMD_MD_INFO:
                        case MSG_CMD_MD_AUTHEN_RESULT: {
                            byte[] carLifeMsg = (byte[]) msg.obj;
                            byte[] headmsg = new byte[8];
                            headmsg[3] = CMD;
                            intToBytes2(carLifeMsg.length, headmsg, 4);//carlifemsg len
                            if (msg.arg1 == 1) {
                                byte[] packet = new byte[headmsg.length + carLifeMsg.length];
                                System.arraycopy(headmsg, 0, packet, 0, headmsg.length);
                                System.arraycopy(carLifeMsg, 0, packet, headmsg.length, carLifeMsg.length);
                                mOutputStream.write(packet);
                                mOutputStream.flush();
                                log("msg=" + msg.what + " combined write =" + Arrays.toString(packet));
                            } else {
                                mOutputStream.write(headmsg);
                                mOutputStream.flush();
                                log("msg=" + msg.what + "write data =" + Arrays.toString(headmsg));
                                mOutputStream.write(carLifeMsg);
                                mOutputStream.flush();
                                log("msg=" + msg.what + "write data =" + Arrays.toString(carLifeMsg));
                            }
                            int txReserved = bytesToShort2(carLifeMsg, 2) & 0xFFFF;
                            mInfoListener.onProtocolEvent(String.format("USB TX CMD 0x%08X innerLen=%d reserved=%d mode=%s",
                                    msg.what, carLifeMsg.length, txReserved, msg.arg1 == 1 ? "combined" : "split"));
                            log("write data ok");
                        }
                        break;
                        case MSG_CMD_VIDEO_ENCODER_INIT_DONE: {

                            {
                                byte[] carLifeMsg = (byte[]) msg.obj;
                                byte[] headmsg = new byte[8];
                                headmsg[3] = CMD;
                                intToBytes2(carLifeMsg.length, headmsg, 4);//carlifemsg len
                                mOutputStream.write(headmsg);
                                mOutputStream.flush();
                                log("msg=" + msg.what + "write data =" + Arrays.toString(headmsg));
                                mOutputStream.write(carLifeMsg);
                                log("msg=" + msg.what + "write data =" + Arrays.toString(carLifeMsg));
                                log("write data ok");
                            }

                            {
                                byte[] carLifeMsg = exportCMDMsg(MSG_CMD_FOREGROUND, null);
                                byte[] headmsg = new byte[8];
                                headmsg[3] = CMD;
                                intToBytes2(carLifeMsg.length, headmsg, 4);//carlifemsg len
                                mOutputStream.write(headmsg);
                                mOutputStream.flush();
                                log("msg=" + MSG_CMD_FOREGROUND + "write data =" + Arrays.toString(headmsg));
                                mOutputStream.write(carLifeMsg);
                                log("msg=" + MSG_CMD_FOREGROUND + "write data =" + Arrays.toString(carLifeMsg));
                                mOutputStream.flush();
                                log("write data ok");
                            }


                        }
                        break;
                        case MSG_CMD_VIDEO_ENCODER_START: {
                            log("now start MSG_CMD_VIDEO_ENCODER_START");

                            CarlifeMusicInitProto.CarlifeMusicInit.Builder builder = CarlifeMusicInitProto.CarlifeMusicInit.newBuilder();
                            builder.setSampleRate(48000);
                            builder.setChannelConfig(2);
                            builder.setSampleFormat(16);
                            byte[] carLifeMsg = exportVideoMsg(MSG_MEDIA_INIT, builder.build().toByteArray());
                            byte[] headmsg = new byte[8];
                            headmsg[3] = MEDIA;
                            intToBytes2(carLifeMsg.length, headmsg, 4);//carlifemsg len
                            mOutputStream.write(headmsg);
                            log("msg=MSG_MEDIA_INIT" + "write data =" + Arrays.toString(headmsg));
                            mOutputStream.write(carLifeMsg);
                            log("msg=MSG_MEDIA_INIT" + "write data =" + Arrays.toString(carLifeMsg));
                            log("write data ok  audiohandler start");

                            if (!mMediaCodecTool.isProjectionActive()) {
                                mInfoListener.onProtocolEvent("HU requested video; mirror not active yet");
                                requestMirrorPermission();
                            } else {
                                mInfoListener.onProtocolEvent("HU video start -> Car UI already streaming");
                            }
                        }
                        break;
                        case MSG_WRITE_AUDIO:
                        case MSG_WRITE_VIDEO: {
                            //log("write audio or video ..................." + msg.what);
                            CarMsg carMsg = (CarMsg) msg.obj;
                            long txStarted = System.nanoTime();
                            if (msg.what == MSG_WRITE_VIDEO) {
                                log("VIDEO USB TX #" + videoTxCount + " outerBytes=" + carMsg.head.length +
                                        " innerBytes=" + carMsg.msg.length);
                            }
                            mOutputStream.write(carMsg.head);
                            mOutputStream.write(carMsg.msg);
                            if (msg.what == MSG_WRITE_VIDEO) {
                                log("VIDEO USB TX DONE #" + videoTxCount + " elapsedUs=" +
                                        ((System.nanoTime() - txStarted) / 1000L));
                            }
                        }
                        break;
                    }
                } catch (Exception e) {
                    log("USB WRITE ERROR: " + e);
                    java.io.StringWriter sw = new java.io.StringWriter();
                    e.printStackTrace(new java.io.PrintWriter(sw));
                    log(sw.toString());
                    resetUsb();
                }
            }
        };
    }

    public interface InfoListener {
        void onVISSize(int x, int y);

        void onVISID(String id);

        void onProtocolEvent(String event);
    }

    static class CarMsg {
        byte[] head;
        byte[] msg;

        CarMsg(byte[] b1, byte[] b3) {
            head = b1;
            msg = b3;
        }
    }
}
