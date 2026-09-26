package com.projection.car;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import static com.projection.car.Utils.log;

/** Encodes a native 1024x768 car UI directly to H.264. No MediaProjection/screen mirroring. */
public class MediaCodecTool {
    private MediaCodec codec;
    private Surface inputSurface;
    private HandlerThread renderThread;
    private Handler renderHandler;
    private volatile boolean active;
    private byte[] config;
    private boolean prependConfig;
    private long frameNo;
    private int width, height, fps, bitrate;
    private VideoDataEncodeListener listener;

    public void startCarUi(VideoDataEncodeListener l, float w, float h, int frameRate, int bitRate) {
        if (active) return;
        listener=l; width=(int)w; height=(int)h; fps=Math.max(10, frameRate); bitrate=bitRate;
        try {
            codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            MediaFormat f=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,width,height);
            f.setInteger(MediaFormat.KEY_BIT_RATE,bitrate);
            f.setInteger(MediaFormat.KEY_FRAME_RATE,fps);
            f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            f.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            f.setInteger(MediaFormat.KEY_PROFILE,MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
            f.setInteger(MediaFormat.KEY_LEVEL,MediaCodecInfo.CodecProfileLevel.AVCLevel31);
            if(Build.VERSION.SDK_INT>=29) f.setInteger(MediaFormat.KEY_MAX_B_FRAMES,0);
            if(Build.VERSION.SDK_INT>=23) f.setInteger(MediaFormat.KEY_PRIORITY,0);
            try { f.setInteger("latency",0); } catch(Throwable ignored) {}
            codec.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface=codec.createInputSurface();
            codec.setCallback(new MediaCodec.Callback(){
                public void onInputBufferAvailable(@NonNull MediaCodec c,int i){}
                public void onOutputFormatChanged(@NonNull MediaCodec c,@NonNull MediaFormat f){ log("CAR UI H264 format="+f); }
                public void onError(@NonNull MediaCodec c,@NonNull MediaCodec.CodecException e){ log("CAR UI H264 ERROR "+e.getDiagnosticInfo()); }
                public void onOutputBufferAvailable(@NonNull MediaCodec c,int i,@NonNull MediaCodec.BufferInfo bi){
                    try {
                        ByteBuffer b=c.getOutputBuffer(i);
                        if(b==null){c.releaseOutputBuffer(i,false);return;}
                        ByteBuffer d=b.duplicate(); d.position(Math.max(0,bi.offset)); d.limit(Math.min(d.capacity(),bi.offset+bi.size));
                        byte[] out=new byte[d.remaining()]; d.get(out); frameNo++;
                        if((bi.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0){config=out;prependConfig=true;log("CAR UI SPS/PPS bytes="+out.length);}
                        else {
                            if((bi.flags&MediaCodec.BUFFER_FLAG_KEY_FRAME)!=0 && prependConfig && config!=null){
                                byte[] all=new byte[config.length+out.length]; System.arraycopy(config,0,all,0,config.length);System.arraycopy(out,0,all,config.length,out.length);out=all;prependConfig=false;
                            }
                            if(listener!=null) listener.onData(out);
                        }
                        c.releaseOutputBuffer(i,false);
                    }catch(Throwable t){log("CAR UI encoder callback error "+t);}
                }
            });
            codec.start(); active=true;
            renderThread=new HandlerThread("car-ui-render");renderThread.start();renderHandler=new Handler(renderThread.getLooper());
            renderHandler.post(renderLoop);
            log("CAR UI started "+width+"x"+height+" @"+fps+" bitrate="+bitrate);
        } catch(Throwable t){log("CAR UI START ERROR "+t);stopProjection();}
    }

    private final Runnable renderLoop=new Runnable(){
        public void run(){
            if(!active||inputSurface==null)return;
            Canvas c=null;
            try{c=inputSurface.lockCanvas(null);drawHome(c);}catch(Throwable t){log("CAR UI DRAW ERROR "+t);}
            finally{if(c!=null)try{inputSurface.unlockCanvasAndPost(c);}catch(Throwable ignored){}}
            if(active&&renderHandler!=null)renderHandler.postDelayed(this,1000L/fps);
        }
    };

    private void drawHome(Canvas c){
        int w=c.getWidth(),h=c.getHeight();
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawColor(Color.rgb(5,15,27));
        p.setColor(Color.rgb(10,29,48));c.drawRect(0,0,w,86,p);
        p.setColor(Color.WHITE);p.setTextSize(32);p.setFakeBoldText(true);c.drawText("Honda e:NS1",42,54,p);
        p.setFakeBoldText(false);p.setTextSize(22);p.setColor(Color.rgb(190,210,230));
        c.drawText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()),w-100,52,p);

        String[] names={"Навигация","YouTube","YouTube Music","Музыка","Настройки"};
        int[] accents={Color.rgb(20,150,255),Color.rgb(245,45,45),Color.rgb(220,35,70),Color.rgb(125,75,245),Color.rgb(80,130,165)};
        float gap=18, left=34, top=118, cardW=(w-left*2-gap*2)/3f, cardH=220;
        for(int i=0;i<5;i++){
            int row=i/3,col=i%3;float x=left+col*(cardW+gap),y=top+row*(cardH+gap);
            p.setColor(Color.rgb(12,34,55));c.drawRoundRect(new RectF(x,y,x+cardW,y+cardH),24,24,p);
            p.setColor(accents[i]);c.drawRoundRect(new RectF(x+22,y+22,x+88,y+88),18,18,p);
            p.setColor(Color.WHITE);p.setTextSize(26);p.setFakeBoldText(true);c.drawText(names[i],x+22,y+135,p);p.setFakeBoldText(false);
            p.setColor(Color.rgb(145,170,195));p.setTextSize(18);
            c.drawText(i==0?"Карты и маршруты":i==1?"Видео":i==2?"Музыка и плейлисты":i==3?"Медиатека":"Система",x+22,y+172,p);
        }
        float barY=h-94;p.setColor(Color.rgb(8,25,42));c.drawRect(0,barY,w,h,p);
        String[] dock={"Главная","Навигация","YouTube","Музыка","Настройки"};
        for(int i=0;i<dock.length;i++){
            float x=35+i*(w-70)/5f;p.setTextSize(18);p.setColor(i==0?Color.rgb(45,170,255):Color.rgb(180,200,220));c.drawText(dock[i],x,barY+56,p);
        }
    }

    public boolean isProjectionActive(){return active;}
    public void stopProjection(){
        active=false;
        if(renderHandler!=null)renderHandler.removeCallbacksAndMessages(null);
        if(renderThread!=null){renderThread.quitSafely();renderThread=null;}
        if(codec!=null){try{codec.stop();}catch(Throwable ignored){}try{codec.release();}catch(Throwable ignored){}codec=null;}
        if(inputSurface!=null){try{inputSurface.release();}catch(Throwable ignored){}inputSurface=null;}
    }

    public interface VideoDataEncodeListener { void onData(byte[] data); void onAudioData(byte[] pcm); }
}
