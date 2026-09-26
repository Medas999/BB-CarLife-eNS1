package com.projection.car;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Path;
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
    private volatile int page=0;
    private volatile int pressed=-1;

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
        final float sx=c.getWidth()/1024f, sy=c.getHeight()/768f;
        c.save(); c.scale(sx,sy);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);

        // Deep blue scenic-style background with horizon glow.
        p.setShader(new LinearGradient(0,0,1024,768,
                new int[]{Color.rgb(3,12,25),Color.rgb(7,31,55),Color.rgb(4,18,32)},
                new float[]{0f,.55f,1f},Shader.TileMode.CLAMP));
        c.drawRect(0,0,1024,768,p); p.setShader(null);
        p.setShader(new RadialGradient(520,340,520,
                new int[]{Color.argb(120,12,105,170),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
        c.drawCircle(520,340,520,p); p.setShader(null);
        // Abstract mountain silhouettes and road lights.
        Path mt=new Path(); mt.moveTo(0,390);mt.lineTo(155,240);mt.lineTo(250,330);mt.lineTo(390,185);
        mt.lineTo(530,345);mt.lineTo(690,220);mt.lineTo(830,345);mt.lineTo(1024,245);mt.lineTo(1024,500);mt.lineTo(0,500);mt.close();
        p.setColor(Color.argb(150,2,15,29));c.drawPath(mt,p);
        p.setShader(new LinearGradient(0,520,1024,610,Color.argb(100,0,160,255),Color.TRANSPARENT,Shader.TileMode.CLAMP));
        c.drawOval(new RectF(-80,500,1120,625),p);p.setShader(null);

        // Header
        p.setColor(Color.argb(190,3,17,31));c.drawRoundRect(new RectF(24,18,1000,84),22,22,p);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(29);p.setColor(Color.WHITE);
        c.drawText("Honda e:NS1",52,60,p);
        p.setTypeface(Typeface.DEFAULT);p.setTextSize(18);p.setColor(Color.rgb(150,190,220));
        c.drawText("CARLIFE  •  CONNECTED",255,58,p);
        String time=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date());
        p.setTextSize(28);p.setColor(Color.WHITE);c.drawText(time,895,59,p);

        String[] names={"Навигация","YouTube","YouTube Music","Музыка","Настройки"};
        if(page>0){ drawPage(c,p,page); c.restore(); return; }
        String[] subs={"Карты • маршруты","Видео • подписки","Треки • плейлисты","Медиатека","Экран • звук"};
        int[] accents={Color.rgb(20,165,255),Color.rgb(245,30,45),Color.rgb(225,30,80),Color.rgb(132,68,245),Color.rgb(90,145,185)};
        float gap=18,left=30,top=106,cw=(1024-left*2-gap*2)/3f,ch=210;
        for(int i=0;i<5;i++){
            int row=i/3,col=i%3;float x=left+col*(cw+gap),y=top+row*(ch+gap);
            p.setShadowLayer(18,0,8,Color.argb(130,0,0,0));p.setColor(pressed==i?Color.argb(245,20,65,95):Color.argb(225,7,25,43));
            c.drawRoundRect(new RectF(x,y,x+cw,y+ch),25,25,p);p.clearShadowLayer();
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(i==0?2.5f:1.2f);p.setColor(i==0?accents[i]:Color.argb(110,120,175,215));
            c.drawRoundRect(new RectF(x,y,x+cw,y+ch),25,25,p);p.setStyle(Paint.Style.FILL);
            // colored visual area
            p.setShader(new LinearGradient(x,y,x+cw,y+112,withAlpha(accents[i],210),withAlpha(accents[i],35),Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(x+1,y+1,x+cw-1,y+112),24,24,p);p.setShader(null);
            drawIcon(c,p,i,x+cw/2,y+55,36,Color.WHITE);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(25);p.setColor(Color.WHITE);
            c.drawText(names[i],x+20,y+151,p);
            p.setTypeface(Typeface.DEFAULT);p.setTextSize(16);p.setColor(Color.rgb(165,192,214));c.drawText(subs[i],x+20,y+180,p);
            p.setTextSize(34);p.setColor(Color.rgb(205,225,240));c.drawText("›",x+cw-34,y+177,p);
        }

        // Now-playing card fills sixth tile.
        float x=left+2*(cw+gap),y=top+ch+gap;
        p.setColor(Color.argb(225,7,25,43));c.drawRoundRect(new RectF(x,y,x+cw,y+ch),25,25,p);
        p.setShader(new LinearGradient(x,y,x+cw,y+90,Color.rgb(20,65,105),Color.rgb(9,30,50),Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(x+1,y+1,x+cw-1,y+95),24,24,p);p.setShader(null);
        drawIcon(c,p,5,x+54,y+48,28,Color.rgb(70,190,255));
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(22);p.setColor(Color.WHITE);c.drawText("Сейчас играет",x+96,y+45,p);
        p.setTypeface(Typeface.DEFAULT);p.setTextSize(16);p.setColor(Color.rgb(155,185,210));c.drawText("Музыка не выбрана",x+20,y+126,p);
        // player controls
        p.setColor(Color.rgb(40,165,255));c.drawCircle(x+cw/2,y+165,27,p);drawPlay(c,p,x+cw/2,y+165,Color.WHITE);
        p.setColor(Color.rgb(120,155,180));c.drawRect(x+24,y+199,x+cw-24,y+203,p);
        p.setColor(Color.rgb(40,180,255));c.drawRect(x+24,y+199,x+104,y+203,p);

        // Bottom dock
        float by=672;p.setColor(Color.argb(238,3,17,31));c.drawRoundRect(new RectF(22,by,1002,756),24,24,p);
        String[] dock={"Главная","Навигация","YouTube","Музыка","Настройки"};
        int[] ids={6,0,1,3,4};
        for(int i=0;i<5;i++){
            float cx=115+i*198;
            if(i==0){p.setColor(Color.argb(80,20,155,255));c.drawRoundRect(new RectF(cx-82,by+8,cx+82,by+76),18,18,p);}
            drawIcon(c,p,ids[i],cx,by+31,19,i==0?Color.rgb(70,190,255):Color.rgb(210,225,238));
            p.setTextAlign(Paint.Align.CENTER);p.setTextSize(14);p.setTypeface(Typeface.DEFAULT);p.setColor(i==0?Color.rgb(80,195,255):Color.rgb(195,215,230));
            c.drawText(dock[i],cx,by+66,p);p.setTextAlign(Paint.Align.LEFT);
        }
        c.restore();
    }

    public void onCarTouch(int action,float rawX,float rawY){
        float x=rawX*(1024f/Math.max(1,width)), y=rawY*(768f/Math.max(1,height));
        // CarLife touch action: 0 down, 1 up, 2 move on the tested Honda implementation.
        if(action==0){ pressed=hitTile(x,y); }
        else if(action==1){
            int hit=hitTile(x,y);
            if(hit>=0 && hit==pressed) page=hit+1;
            else if(y>=672) page=0;
            pressed=-1;
        }
        log("CAR UI TOUCH action="+action+" x="+x+" y="+y+" tile="+pressed+" page="+page);
    }

    private int hitTile(float x,float y){
        float gap=18,left=30,top=106,cw=(1024-left*2-gap*2)/3f,ch=210;
        for(int i=0;i<5;i++){int row=i/3,col=i%3;float l=left+col*(cw+gap),t=top+row*(ch+gap);if(x>=l&&x<=l+cw&&y>=t&&y<=t+ch)return i;}
        return -1;
    }

    private void drawPage(Canvas c,Paint p,int pg){
        String[] title={"","Навигация","YouTube","YouTube Music","Музыка","Настройки"};
        int[] accent={0,Color.rgb(20,165,255),Color.rgb(245,30,45),Color.rgb(225,30,80),Color.rgb(132,68,245),Color.rgb(90,145,185)};
        p.setColor(Color.argb(225,7,25,43));c.drawRoundRect(new RectF(30,106,994,650),28,28,p);
        p.setColor(accent[pg]);c.drawRoundRect(new RectF(55,132,145,222),24,24,p);drawIcon(c,p,pg-1,100,177,34,Color.WHITE);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(42);p.setColor(Color.WHITE);c.drawText(title[pg],175,188,p);
        p.setTypeface(Typeface.DEFAULT);p.setTextSize(22);p.setColor(Color.rgb(160,195,220));c.drawText("Раздел подключён к Car UI • этап 2",175,226,p);
        p.setTextSize(25);p.setColor(Color.WHITE);
        c.drawText(pg==1?"Карты и построение маршрута":pg==2?"Видео и поиск YouTube":pg==3?"Музыка и плейлисты YouTube Music":pg==4?"Локальная медиатека и проигрыватель":"Настройки автомобильного интерфейса",70,320,p);
        p.setColor(Color.argb(70,accent[pg]));c.drawRoundRect(new RectF(70,365,954,535),24,24,p);
        p.setTextSize(22);p.setColor(Color.rgb(205,225,238));c.drawText("Тач Honda работает. Функции этого раздела",105,430,p);c.drawText("будут подключаться на следующих этапах.",105,470,p);
        p.setColor(Color.rgb(40,165,255));c.drawRoundRect(new RectF(70,570,255,625),18,18,p);p.setColor(Color.WHITE);p.setTextSize(20);c.drawText("‹  На главную",95,606,p);
    }

    private int withAlpha(int color,int alpha){
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private void drawIcon(Canvas c,Paint p,int type,float cx,float cy,float r,int color){
        p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(4,r*.14f));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        Path q=new Path();
        if(type==0){ // navigation arrow
            q.moveTo(cx,cy-r);q.lineTo(cx+r*.72f,cy+r);q.lineTo(cx,cy+r*.55f);q.lineTo(cx-r*.72f,cy+r);q.close();c.drawPath(q,p);
        }else if(type==1){ // YouTube
            p.setStyle(Paint.Style.FILL);c.drawRoundRect(new RectF(cx-r,cy-r*.65f,cx+r,cy+r*.65f),r*.28f,r*.28f,p);
            p.setColor(Color.rgb(245,30,45));q.moveTo(cx-r*.2f,cy-r*.36f);q.lineTo(cx+r*.45f,cy);q.lineTo(cx-r*.2f,cy+r*.36f);q.close();c.drawPath(q,p);
        }else if(type==2){ // music play ring
            c.drawCircle(cx,cy,r*.82f,p);p.setStyle(Paint.Style.FILL);q.moveTo(cx-r*.18f,cy-r*.36f);q.lineTo(cx+r*.45f,cy);q.lineTo(cx-r*.18f,cy+r*.36f);q.close();c.drawPath(q,p);
        }else if(type==3||type==5){ // note
            c.drawLine(cx+r*.15f,cy-r*.72f,cx+r*.15f,cy+r*.38f,p);c.drawLine(cx+r*.15f,cy-r*.72f,cx+r*.7f,cy-r*.86f,p);
            p.setStyle(Paint.Style.FILL);c.drawCircle(cx-r*.15f,cy+r*.52f,r*.34f,p);c.drawCircle(cx+r*.55f,cy+r*.32f,r*.28f,p);
        }else if(type==4){ // gear-ish settings
            c.drawCircle(cx,cy,r*.62f,p);c.drawCircle(cx,cy,r*.2f,p);
            for(int i=0;i<8;i++){double a=i*Math.PI/4;float x1=cx+(float)Math.cos(a)*r*.72f,y1=cy+(float)Math.sin(a)*r*.72f;float x2=cx+(float)Math.cos(a)*r,y2=cy+(float)Math.sin(a)*r;c.drawLine(x1,y1,x2,y2,p);}
        }else{ // home
            q.moveTo(cx-r*.85f,cy);q.lineTo(cx,cy-r*.72f);q.lineTo(cx+r*.85f,cy);q.moveTo(cx-r*.58f,cy-r*.05f);q.lineTo(cx-r*.58f,cy+r*.72f);q.lineTo(cx+r*.58f,cy+r*.72f);q.lineTo(cx+r*.58f,cy-r*.05f);c.drawPath(q,p);
        }
        p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawPlay(Canvas c,Paint p,float cx,float cy,int color){
        p.setColor(color);Path q=new Path();q.moveTo(cx-7,cy-12);q.lineTo(cx+12,cy);q.lineTo(cx-7,cy+12);q.close();c.drawPath(q,p);
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
