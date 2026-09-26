package com.projection.car;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import androidx.annotation.NonNull;
import java.nio.ByteBuffer;
import static com.projection.car.Utils.log;

/**
 * Minimal bridge video endpoint.
 * Temporary status frame until Android Auto H.264 is forwarded directly to CarLife.
 * No Spotify, maps, navigation, mirroring, test audio or custom launcher UI.
 */
public class MediaCodecTool {
    private MediaCodec codec; private Surface surface; private HandlerThread thread; private Handler handler;
    private volatile boolean active; private byte[] config; private boolean prepend=true;
    private int width,height,fps; private VideoDataEncodeListener listener;

    public void setContext(android.content.Context c) {}

    public void startCarUi(VideoDataEncodeListener l,float w,float h,int frameRate,int bitRate){
        if(active)return; listener=l;width=(int)w;height=(int)h;fps=Math.max(10,frameRate);
        try{
            codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            MediaFormat f=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,width,height);
            f.setInteger(MediaFormat.KEY_BIT_RATE,Math.max(1500000,bitRate));
            f.setInteger(MediaFormat.KEY_FRAME_RATE,fps);f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            f.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            f.setInteger(MediaFormat.KEY_PROFILE,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
            f.setInteger(MediaFormat.KEY_LEVEL,MediaCodecInfo.CodecProfileLevel.AVCLevel31);
            if(Build.VERSION.SDK_INT>=29)f.setInteger(MediaFormat.KEY_MAX_B_FRAMES,0);
            codec.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);surface=codec.createInputSurface();
            codec.setCallback(new MediaCodec.Callback(){
                public void onInputBufferAvailable(@NonNull MediaCodec c,int i){}
                public void onOutputFormatChanged(@NonNull MediaCodec c,@NonNull MediaFormat f){log("BRIDGE H264 placeholder format="+f);}
                public void onError(@NonNull MediaCodec c,@NonNull MediaCodec.CodecException e){log("BRIDGE H264 ERROR "+e.getDiagnosticInfo());}
                public void onOutputBufferAvailable(@NonNull MediaCodec c,int i,@NonNull MediaCodec.BufferInfo bi){
                    try{ByteBuffer b=c.getOutputBuffer(i);if(b==null){c.releaseOutputBuffer(i,false);return;}ByteBuffer d=b.duplicate();d.position(bi.offset);d.limit(bi.offset+bi.size);byte[] out=new byte[d.remaining()];d.get(out);
                    if((bi.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0){config=out;prepend=true;}
                    else{if((bi.flags&MediaCodec.BUFFER_FLAG_KEY_FRAME)!=0&&prepend&&config!=null){byte[] a=new byte[config.length+out.length];System.arraycopy(config,0,a,0,config.length);System.arraycopy(out,0,a,config.length,out.length);out=a;prepend=false;}if(listener!=null)listener.onData(out);}
                    c.releaseOutputBuffer(i,false);}catch(Throwable t){log("BRIDGE encoder callback "+t);}
                }});
            codec.start();active=true;thread=new HandlerThread("bridge-status");thread.start();handler=new Handler(thread.getLooper());handler.post(draw);
            log("AA_BRIDGE placeholder video started "+width+"x"+height);
        }catch(Throwable t){log("AA_BRIDGE placeholder start failed "+t);stopProjection();}
    }

    private final Runnable draw=new Runnable(){public void run(){if(!active||surface==null)return;Canvas c=null;try{c=surface.lockCanvas(null);c.drawColor(Color.rgb(5,12,20));Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.WHITE);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(42);c.drawText("Android Auto → CarLife",80,300,p);p.setTypeface(Typeface.DEFAULT);p.setTextSize(25);p.setColor(Color.rgb(120,190,240));c.drawText("Bridge transport ready",80,355,p);p.setTextSize(19);p.setColor(Color.LTGRAY);c.drawText("Waiting for Android Auto projection…",80,405,p);}catch(Throwable t){log("BRIDGE status draw "+t);}finally{if(c!=null)try{surface.unlockCanvasAndPost(c);}catch(Throwable ignored){}}if(active&&handler!=null)handler.postDelayed(this,1000);}};
    public void onCarTouch(int action,float x,float y){log("AA_BRIDGE TOUCH pending-forward action="+action+" x="+x+" y="+y);}
    public boolean isProjectionActive(){return active;}
    public void stopProjection(){active=false;if(handler!=null)handler.removeCallbacksAndMessages(null);if(thread!=null)thread.quitSafely();thread=null;handler=null;try{if(codec!=null)codec.stop();}catch(Throwable ignored){}try{if(codec!=null)codec.release();}catch(Throwable ignored){}try{if(surface!=null)surface.release();}catch(Throwable ignored){}codec=null;surface=null;}
    public interface VideoDataEncodeListener{void onData(byte[] data);void onAudioData(byte[] pcm);}
}
