package com.projection.car;

import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Path;
import android.graphics.Typeface;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.security.SecureRandom;
import android.util.Base64;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
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
    private Context appContext;
    private float calMinX=0,calMaxX=1024,calMinY=0,calMaxY=768;
    private boolean calibrated=false,calibrating=false;
    private int calStep=0;
    private final float[] calX=new float[4],calY=new float[4];
    private static final String SPOTIFY_CLIENT_ID="ddba95bc80cd40feb35199cd08c09268";
    private static final String SPOTIFY_REDIRECT="ens1carui://spotify/callback";
    private volatile String spotifyStatus="Не авторизован"; private String pkceVerifier; private String oauthState;
    private volatile String spotifyUser="",spotifyTrack="Нет активного трека",spotifyArtist="",spotifyDevice="";
    private volatile boolean spotifyPlaying=false,spotifyBusy=false; private final String[] spotifyPlaylists=new String[4]; private final String[] spotifyPlaylistUris=new String[4];
    private HandlerThread audioThread;
    private volatile Bitmap navMap; private volatile double navLat=50.4501,navLon=30.5234; private volatile boolean navHasGps=false; private LocationManager locationManager;
    private volatile String navStatus="Ожидание GPS"; private volatile double destLat=0,destLon=0; private volatile String destName=""; private volatile String routeSummary=""; private volatile String routeGeometry=""; private volatile int navZoom=12; private volatile boolean navLoading=false; private long navLastLoad=0; private final ExecutorService navExecutor=Executors.newSingleThreadExecutor(); private Handler audioHandler; private volatile boolean playing=false; private double tonePhase=0; private long playedFrames=0;

    public void setContext(Context c){ appContext=c.getApplicationContext(); loadCalibration(); startLocation(); if(isSpotifyAuthorized())refreshSpotify(); }

    private void loadCalibration(){ if(appContext==null)return; SharedPreferences sp=appContext.getSharedPreferences("carui_touch",0); calibrated=sp.getBoolean("ok",false); calMinX=sp.getFloat("minX",0);calMaxX=sp.getFloat("maxX",1024);calMinY=sp.getFloat("minY",0);calMaxY=sp.getFloat("maxY",768); SharedPreferences ss=appContext.getSharedPreferences("spotify",0); pkceVerifier=ss.getString("pkce_verifier",null);oauthState=ss.getString("oauth_state",null);if(ss.getString("token_json",null)!=null)spotifyStatus="Spotify подключён"; }
    private void saveCalibration(){ if(appContext==null)return; appContext.getSharedPreferences("carui_touch",0).edit().putBoolean("ok",true).putFloat("minX",calMinX).putFloat("maxX",calMaxX).putFloat("minY",calMinY).putFloat("maxY",calMaxY).apply(); }

    public void startCarUi(VideoDataEncodeListener l, float w, float h, int frameRate, int bitRate) {
        if (active) return;
        listener=l; width=(int)w; height=(int)h; fps=Math.max(10, frameRate); bitrate=bitRate;
        startAudioEngine();
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

        String[] names={"Навигация","YouTube","Spotify","Музыка","Настройки"};
        if(calibrating){ drawCalibration(c,p); c.restore(); return; }
        if(page==1){drawNavigation(c,p);c.restore();return;}
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
        if(calibrating){
            if(action==1){
                calX[calStep]=rawX; calY[calStep]=rawY; calStep++;
                if(calStep>=4){
                    calMinX=(calX[0]+calX[2])/2f; calMaxX=(calX[1]+calX[3])/2f;
                    calMinY=(calY[0]+calY[1])/2f; calMaxY=(calY[2]+calY[3])/2f;
                    if(calMaxX<calMinX){float t=calMinX;calMinX=calMaxX;calMaxX=t;}
                    if(calMaxY<calMinY){float t=calMinY;calMinY=calMaxY;calMaxY=t;}
                    calibrated=true;calibrating=false;calStep=0;saveCalibration();page=5;
                    log("TOUCH CAL SAVED X="+calMinX+".."+calMaxX+" Y="+calMinY+".."+calMaxY);
                }
            }
            return;
        }
        float baseX=rawX*(1024f/Math.max(1,width)),baseY=rawY*(768f/Math.max(1,height));
        float x=calibrated?(rawX-calMinX)*1024f/Math.max(1f,calMaxX-calMinX):baseX;
        float y=calibrated?(rawY-calMinY)*768f/Math.max(1f,calMaxY-calMinY):baseY;
        x=Math.max(0,Math.min(1024,x));y=Math.max(0,Math.min(768,y));
        if(action==0){ pressed=hitTile(x,y); }
        else if(action==1){
            int hit=hitTile(x,y);
            if(page==5 && x>=430 && x<=900 && y>=365 && y<=455){calibrating=true;calStep=0;pressed=-1;return;}
            if(page==5 && x>=430 && x<=900 && y>=475 && y<=550){calibrated=false;calMinX=0;calMaxX=1024;calMinY=0;calMaxY=768;if(appContext!=null)appContext.getSharedPreferences("carui_touch",0).edit().clear().apply();}
            if(page==1 && action==1){if(x>=840&&x<=945&&y>=190&&y<=300){navZoom=Math.min(18,navZoom+1);navLastLoad=0;}else if(x>=840&&x<=945&&y>=300&&y<=405){navZoom=Math.max(4,navZoom-1);navLastLoad=0;}else if(x>=45&&x<=790&&y>=120&&y<=620){setDestinationFromMap(x,y);navLastLoad=0;}else if(y>=640){page=0;}}
            if(page==3){if(!isSpotifyAuthorized()&&x>=300&&x<=760&&y>=365&&y<=520)startSpotifyLogin();else if(isSpotifyAuthorized()){if(y>=455&&y<=530&&x<190)spotifyCommand("prev");else if(y>=455&&y<=530&&x<295)spotifyCommand("toggle");else if(y>=455&&y<=530&&x<400)spotifyCommand("next");else if(x>=455&&x<=915&&y>=440&&y<=535){int col=x<695?0:1,row=y<495?0:1,idx=row*2+col;if(idx<4&&spotifyPlaylistUris[idx]!=null)spotifyCommand("playlist:"+spotifyPlaylistUris[idx]);}}}
            if(page==4 && x>=350 && x<=675 && y>=390 && y<=520){playing=!playing;log("CAR UI PLAYER playing="+playing);}
            if(hit>=0 && hit==pressed) page=hit+1;
            else if(page>0 && y>=540) page=0;
            pressed=-1;
        }
        log("CAR UI TOUCH raw="+rawX+","+rawY+" mapped="+x+","+y+" calibrated="+calibrated+" action="+action+" page="+page);
    }

    private int hitTile(float x,float y){
        float gap=18,left=30,top=106,cw=(1024-left*2-gap*2)/3f,ch=210;
        for(int i=0;i<5;i++){int row=i/3,col=i%3;float l=left+col*(cw+gap),t=top+row*(ch+gap);if(x>=l&&x<=l+cw&&y>=t&&y<=t+ch)return i;}
        return -1;
    }

    private void drawPage(Canvas c,Paint p,int pg){
        String[] title={"","Навигация","YouTube","Spotify","Музыка","Настройки"};
        int[] accent={0,Color.rgb(20,165,255),Color.rgb(245,30,45),Color.rgb(225,30,80),Color.rgb(132,68,245),Color.rgb(90,145,185)};
        p.setColor(Color.argb(225,7,25,43));c.drawRoundRect(new RectF(30,106,994,650),28,28,p);
        p.setColor(accent[pg]);c.drawRoundRect(new RectF(55,132,145,222),24,24,p);drawIcon(c,p,pg-1,100,177,34,Color.WHITE);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(42);p.setColor(Color.WHITE);c.drawText(title[pg],175,188,p);
        p.setTypeface(Typeface.DEFAULT);p.setTextSize(22);p.setColor(Color.rgb(160,195,220));c.drawText("Раздел подключён к Car UI • этап 2",175,226,p);
        p.setTextSize(25);p.setColor(Color.WHITE);
        c.drawText(pg==1?"Карты и построение маршрута":pg==2?"Видео и поиск YouTube":pg==3?"Музыка и плейлисты Spotify":pg==4?"Локальная медиатека и проигрыватель":"Настройки автомобильного интерфейса",70,320,p);
        p.setColor(withAlpha(accent[pg],70));c.drawRoundRect(new RectF(70,365,954,535),24,24,p);
        p.setTextSize(22);p.setColor(Color.rgb(205,225,238));if(pg==3){if(!isSpotifyAuthorized()){p.setColor(Color.rgb(20,45,55));c.drawRoundRect(new RectF(250,365,775,535),26,26,p);p.setColor(Color.rgb(30,215,96));c.drawRoundRect(new RectF(300,405,760,490),42,42,p);p.setColor(Color.WHITE);p.setTextSize(25);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));c.drawText("Подключить Spotify",405,458,p);p.setTypeface(Typeface.DEFAULT);p.setTextSize(18);p.setColor(Color.rgb(190,220,205));c.drawText(spotifyStatus,335,520,p);}else{refreshSpotify();p.setColor(Color.rgb(16,38,42));c.drawRoundRect(new RectF(70,350,954,535),22,22,p);p.setColor(Color.rgb(30,215,96));p.setTextSize(18);c.drawText("● Spotify  "+spotifyUser,92,380,p);p.setColor(Color.WHITE);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(25);c.drawText(spotifyTrack,92,420,p);p.setTypeface(Typeface.DEFAULT);p.setTextSize(17);p.setColor(Color.rgb(185,210,200));c.drawText(spotifyArtist+(spotifyDevice.length()>0?"  •  "+spotifyDevice:""),92,448,p);String[] ctl={"◀◀",spotifyPlaying?"Ⅱ":"▶","▶▶"};for(int i=0;i<3;i++){float xx=135+i*105;p.setColor(Color.rgb(30,215,96));c.drawCircle(xx,493,34,p);p.setColor(Color.WHITE);p.setTextSize(20);p.setTextAlign(Paint.Align.CENTER);c.drawText(ctl[i],xx,500,p);}p.setTextAlign(Paint.Align.LEFT);for(int i=0;i<4;i++)if(spotifyPlaylists[i]!=null){float xx=455+(i%2)*240,yy=474+(i/2)*42;p.setColor(Color.rgb(35,65,65));c.drawRoundRect(new RectF(xx,yy-28,xx+220,yy+8),12,12,p);p.setColor(Color.WHITE);p.setTextSize(14);String nm=spotifyPlaylists[i];if(nm.length()>22)nm=nm.substring(0,21)+"…";c.drawText(nm,xx+10,yy-5,p);}}}else if(pg==4){p.setColor(Color.rgb(20,45,70));c.drawRoundRect(new RectF(250,365,775,535),26,26,p);p.setColor(Color.WHITE);p.setTextSize(26);c.drawText("Тест аудиоканала Honda",335,410,p);p.setColor(Color.rgb(40,165,255));c.drawCircle(512,470,48,p);drawPlay(c,p,512,470,Color.WHITE);p.setTextSize(20);p.setColor(Color.rgb(180,210,230));c.drawText(playing?"Играет • "+playerTime():"Нажмите Play — тестовый звук 440 Гц",335,525,p);}else if(pg==5){c.drawText(calibrated?"Тачскрин откалиброван":"Используется стандартная калибровка",105,420,p);p.setColor(Color.rgb(40,165,255));c.drawRoundRect(new RectF(430,365,900,455),20,20,p);p.setColor(Color.WHITE);c.drawText("Калибровать тачскрин",500,420,p);p.setColor(Color.rgb(70,90,110));c.drawRoundRect(new RectF(430,475,900,535),18,18,p);p.setColor(Color.WHITE);p.setTextSize(19);c.drawText("Сбросить калибровку",535,514,p);}else{c.drawText("Тач Honda работает. Функции этого раздела",105,430,p);c.drawText("будут подключаться на следующих этапах.",105,470,p);}
        p.setColor(Color.rgb(40,165,255));c.drawRoundRect(new RectF(55,545,330,635),22,22,p);p.setColor(Color.WHITE);p.setTextSize(24);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));c.drawText("‹  На главную",90,600,p);p.setTypeface(Typeface.DEFAULT);
    }

    private void drawCalibration(Canvas c,Paint p){
        p.setColor(Color.argb(245,3,15,28));c.drawRect(0,86,1024,768,p);
        String[] msg={"Нажмите точно на метку: ВЕРХНИЙ ЛЕВЫЙ","Нажмите точно на метку: ВЕРХНИЙ ПРАВЫЙ","Нажмите точно на метку: НИЖНИЙ ЛЕВЫЙ","Нажмите точно на метку: НИЖНИЙ ПРАВЫЙ"};
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(28);p.setColor(Color.WHITE);c.drawText(msg[Math.min(calStep,3)],512,145,p);
        float[][] pts={{70,150},{954,150},{70,690},{954,690}};float x=pts[Math.min(calStep,3)][0],y=pts[Math.min(calStep,3)][1];
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(Color.rgb(40,190,255));c.drawCircle(x,y,30,p);c.drawLine(x-45,y,x+45,y,p);c.drawLine(x,y-45,x,y+45,p);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,7,p);
        p.setTextSize(19);p.setTypeface(Typeface.DEFAULT);p.setColor(Color.rgb(160,195,220));c.drawText("После 4 точек координаты сохранятся автоматически",512,735,p);p.setTextAlign(Paint.Align.LEFT);
    }

    private boolean isSpotifyAuthorized(){return appContext!=null&&appContext.getSharedPreferences("spotify",0).getString("token_json",null)!=null;}
    private void startLocation(){try{locationManager=(LocationManager)appContext.getSystemService(Context.LOCATION_SERVICE);if(ContextCompat.checkSelfPermission(appContext,android.Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){Location last=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);if(last==null)last=locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);if(last!=null)applyLocation(last);locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500,3,new LocationListener(){public void onLocationChanged(Location l){applyLocation(l);}public void onStatusChanged(String p,int s,Bundle b){}public void onProviderEnabled(String p){}public void onProviderDisabled(String p){}});}else navStatus="Разрешите GPS на телефоне";}catch(Throwable t){navStatus="GPS недоступен";log("NAV GPS "+t);}}
    private void applyLocation(Location l){navLat=l.getLatitude();navLon=l.getLongitude();navHasGps=true;navStatus=String.format(Locale.US,"GPS %.5f, %.5f",navLat,navLon);navLastLoad=0;}

    private JSONObject tokenObject(){try{return new JSONObject(appContext.getSharedPreferences("spotify",0).getString("token_json","{}"));}catch(Throwable t){return new JSONObject();}}
    private String spotifyToken(){return tokenObject().optString("access_token","");}
    private String api(String method,String endpoint,String body)throws Exception{HttpURLConnection h=(HttpURLConnection)new URL("https://api.spotify.com/v1"+endpoint).openConnection();h.setRequestMethod(method);h.setRequestProperty("Authorization","Bearer "+spotifyToken());h.setRequestProperty("Content-Type","application/json");h.setConnectTimeout(6000);h.setReadTimeout(7000);if(body!=null){h.setDoOutput(true);try(OutputStream os=h.getOutputStream()){os.write(body.getBytes("UTF-8"));}}int rc=h.getResponseCode();InputStream is=rc<400?h.getInputStream():h.getErrorStream();StringBuilder o=new StringBuilder();if(is!=null){BufferedReader br=new BufferedReader(new InputStreamReader(is));String q;while((q=br.readLine())!=null)o.append(q);br.close();}h.disconnect();if(rc==401)throw new SecurityException("401");if(rc>=400)throw new IOException("HTTP "+rc+" "+o);return o.toString();}
    private void refreshSpotify(){if(spotifyBusy||!isSpotifyAuthorized())return;spotifyBusy=true;navExecutor.execute(()->{try{JSONObject me=new JSONObject(api("GET","/me",null));spotifyUser=me.optString("display_name","Spotify");JSONObject pl=new JSONObject(api("GET","/me/playlists?limit=4",null));JSONArray a=pl.optJSONArray("items");for(int i=0;i<4;i++){spotifyPlaylists[i]=null;spotifyPlaylistUris[i]=null;}if(a!=null)for(int i=0;i<Math.min(4,a.length());i++){JSONObject x=a.optJSONObject(i);if(x!=null){spotifyPlaylists[i]=x.optString("name","Playlist");spotifyPlaylistUris[i]=x.optString("uri","");}}refreshSpotifyPlayerInternal();spotifyStatus="Spotify подключён";}catch(SecurityException e){spotifyStatus="Сессия истекла — авторизуйтесь снова";}catch(Throwable t){spotifyStatus="Spotify API: "+t.getMessage();log("SPOTIFY refresh "+t);}finally{spotifyBusy=false;}});}
    private void refreshSpotifyPlayerInternal(){try{String r=api("GET","/me/player",null);if(r.length()==0){spotifyTrack="Нет активного устройства";spotifyArtist="Откройте Spotify на телефоне";spotifyPlaying=false;return;}JSONObject j=new JSONObject(r);spotifyPlaying=j.optBoolean("is_playing",false);JSONObject d=j.optJSONObject("device");spotifyDevice=d==null?"":d.optString("name","");JSONObject it=j.optJSONObject("item");if(it!=null){spotifyTrack=it.optString("name","Трек");JSONArray ar=it.optJSONArray("artists");spotifyArtist=ar!=null&&ar.length()>0?ar.optJSONObject(0).optString("name",""):"";}}catch(Throwable t){log("SPOTIFY player "+t);}}
    private void spotifyCommand(String cmd){if(!isSpotifyAuthorized())return;navExecutor.execute(()->{try{if("toggle".equals(cmd))api("PUT",spotifyPlaying?"/me/player/pause":"/me/player/play",null);else if("next".equals(cmd))api("POST","/me/player/next",null);else if("prev".equals(cmd))api("POST","/me/player/previous",null);else if(cmd.startsWith("playlist:"))api("PUT","/me/player/play","{\"context_uri\":\""+cmd.substring(9)+"\"}");Thread.sleep(350);refreshSpotifyPlayerInternal();}catch(Throwable t){spotifyStatus="Playback: "+t.getMessage();log("SPOTIFY command "+t);}});}
    private void searchDestination(String q){if(q==null||q.trim().length()<2)return;navStatus="Поиск: "+q;navExecutor.execute(()->{HttpURLConnection h=null;try{String u="https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&countrycodes=ua&q="+URLEncoder.encode(q,"UTF-8");h=(HttpURLConnection)new URL(u).openConnection();h.setRequestProperty("User-Agent","eNS1-CarUI/3.0 contact:local-app");h.setConnectTimeout(6000);h.setReadTimeout(7000);JSONArray a=new JSONArray(readHttp(h));if(a.length()==0){navStatus="Адрес не найден";return;}JSONObject x=a.getJSONObject(0);destLat=x.getDouble("lat");destLon=x.getDouble("lon");destName=x.optString("display_name",q);buildRoute();}catch(Throwable t){navStatus="Ошибка поиска";log("NAV search "+t);}finally{if(h!=null)h.disconnect();}});}
    private String readHttp(HttpURLConnection h)throws Exception{int rc=h.getResponseCode();InputStream in=rc<400?h.getInputStream():h.getErrorStream();BufferedReader br=new BufferedReader(new InputStreamReader(in));StringBuilder b=new StringBuilder();String q;while((q=br.readLine())!=null)b.append(q);br.close();if(rc>=400)throw new IOException("HTTP "+rc);return b.toString();}
    private void buildRoute(){HttpURLConnection h=null;try{navStatus="Строю маршрут…";String u="https://router.project-osrm.org/route/v1/driving/"+navLon+","+navLat+";"+destLon+","+destLat+"?overview=full&geometries=polyline&steps=true";h=(HttpURLConnection)new URL(u).openConnection();h.setRequestProperty("User-Agent","eNS1-CarUI/3.0");JSONObject j=new JSONObject(readHttp(h));JSONArray rs=j.optJSONArray("routes");if(rs==null||rs.length()==0){navStatus="Маршрут не найден";return;}JSONObject r=rs.getJSONObject(0);double km=r.optDouble("distance",0)/1000.0;int min=(int)Math.round(r.optDouble("duration",0)/60.0);routeSummary=String.format(Locale.getDefault(),"%.1f км • %d мин",km,min);routeGeometry=r.optString("geometry","");navStatus="Маршрут готов";navLastLoad=0;}catch(Throwable t){navStatus="Ошибка маршрута";log("NAV route "+t);}finally{if(h!=null)h.disconnect();}}

    private void setDestinationFromMap(float x,float y){double n=Math.pow(2,navZoom);double centerX=(navLon+180.0)/360.0*n;double centerY=(1.0-Math.log(Math.tan(Math.toRadians(navLat))+1.0/Math.cos(Math.toRadians(navLat)))/Math.PI)/2.0*n;double px=(x-45f)/745f*768.0,py=(y-120f)/500f*768.0;double tx=Math.floor(centerX)+((px-384.0)/256.0);double ty=Math.floor(centerY)+((py-384.0)/256.0);destLon=tx/n*360.0-180.0;double a=Math.PI*(1-2*ty/n);destLat=Math.toDegrees(Math.atan(Math.sinh(a)));destName=String.format(Locale.getDefault(),"Точка %.4f, %.4f",destLat,destLon);navExecutor.execute(this::buildRoute);}
    private java.util.List<double[]> decodePolyline(String enc){java.util.ArrayList<double[]> out=new java.util.ArrayList<>();int i=0,lat=0,lon=0;while(i<enc.length()){int r=0,sh=0,b;do{b=enc.charAt(i++)-63;r|=(b&31)<<sh;sh+=5;}while(b>=32&&i<enc.length());lat+=(r&1)!=0?~(r>>1):(r>>1);r=0;sh=0;do{b=enc.charAt(i++)-63;r|=(b&31)<<sh;sh+=5;}while(b>=32&&i<enc.length());lon+=(r&1)!=0?~(r>>1):(r>>1);out.add(new double[]{lat/1e5,lon/1e5});}return out;}
    private void drawRouteOnMap(Canvas cc,Paint pp,int z,int cx,int cy){if(routeGeometry==null||routeGeometry.length()==0)return;try{java.util.List<double[]> pts=decodePolyline(routeGeometry);Path path=new Path();boolean first=true;double n=Math.pow(2,z);for(double[] q:pts){double xt=(q[1]+180.0)/360.0*n,yt=(1.0-Math.log(Math.tan(Math.toRadians(q[0]))+1.0/Math.cos(Math.toRadians(q[0])))/Math.PI)/2.0*n;float px=(float)((xt-(cx-1))*256.0),py=(float)((yt-(cy-1))*256.0);if(first){path.moveTo(px,py);first=false;}else path.lineTo(px,py);}pp.setStyle(Paint.Style.STROKE);pp.setStrokeWidth(10);pp.setStrokeCap(Paint.Cap.ROUND);pp.setColor(Color.rgb(20,120,255));cc.drawPath(path,pp);pp.setStrokeWidth(4);pp.setColor(Color.WHITE);cc.drawPath(path,pp);pp.setStyle(Paint.Style.FILL);}catch(Throwable t){log("NAV draw route "+t);}}
    private void requestNavMap(){
        long now=System.currentTimeMillis();if(navLoading||now-navLastLoad<1200)return;navLoading=true;navLastLoad=now;
        final double lat=navLat,lon=navLon;final int z=navZoom;
        navExecutor.execute(()->{try{
            double n=Math.pow(2,z);double xt=(lon+180.0)/360.0*n;double yt=(1.0-Math.log(Math.tan(Math.toRadians(lat))+1.0/Math.cos(Math.toRadians(lat)))/Math.PI)/2.0*n;
            int cx=(int)Math.floor(xt),cy=(int)Math.floor(yt);Bitmap big=Bitmap.createBitmap(768,768,Bitmap.Config.ARGB_8888);Canvas cc=new Canvas(big);Paint pp=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){int tx=cx+dx,ty=cy+dy;URL url=new URL("https://tile.openstreetmap.org/"+z+"/"+tx+"/"+ty+".png");HttpURLConnection h=(HttpURLConnection)url.openConnection();h.setRequestProperty("User-Agent","eNS1-CarUI/2.5 Android");h.setConnectTimeout(5000);h.setReadTimeout(7000);try(InputStream in=h.getInputStream()){Bitmap b=BitmapFactory.decodeStream(in);if(b!=null)cc.drawBitmap(b,(dx+1)*256,(dy+1)*256,pp);}finally{h.disconnect();}}
            drawRouteOnMap(cc,pp,z,cx,cy);navMap=big;log("NAV map loaded lat="+lat+" lon="+lon+" z="+z);
        }catch(Throwable t){log("NAV map error "+t);}finally{navLoading=false;}});
    }
    private void drawNavigation(Canvas c,Paint p){
        requestNavMap();p.setColor(Color.argb(235,5,18,30));c.drawRoundRect(new RectF(30,106,994,650),28,28,p);
        Bitmap b=navMap;if(b!=null)c.drawBitmap(b,null,new RectF(45,120,790,620),p);else{p.setColor(Color.rgb(20,45,65));c.drawRoundRect(new RectF(45,120,790,620),20,20,p);p.setColor(Color.WHITE);p.setTextSize(28);c.drawText("Загрузка карты…",280,360,p);}
        p.setColor(Color.argb(220,4,20,34));c.drawRoundRect(new RectF(805,120,975,620),22,22,p);p.setColor(Color.WHITE);p.setTextSize(22);c.drawText("Навигация",830,165,p);
        p.setColor(Color.rgb(40,165,255));c.drawCircle(890,245,42,p);p.setTextSize(42);p.setTextAlign(Paint.Align.CENTER);c.drawText("+",890,260,p);c.drawCircle(890,350,42,p);c.drawText("−",890,364,p);
        p.setColor(Color.rgb(35,95,135));c.drawRoundRect(new RectF(825,425,955,490),20,20,p);p.setTextSize(18);c.setDrawFilter(null);c.drawText("Моя точка",890,465,p);p.setTextAlign(Paint.Align.LEFT);p.setTextSize(14);p.setColor(Color.rgb(190,215,230));c.drawText(navStatus,815,530,p);if(routeSummary.length()>0){p.setColor(Color.rgb(40,165,255));p.setTextSize(18);c.drawText(routeSummary,820,565,p);p.setTextSize(12);p.setColor(Color.WHITE);String dn=destName.length()>22?destName.substring(0,21)+"…":destName;c.drawText(dn,820,590,p);}
        p.setTextSize(14);p.setColor(Color.rgb(190,205,215));c.drawText("© OpenStreetMap contributors",55,642,p);
        p.setColor(Color.rgb(40,165,255));c.drawRoundRect(new RectF(55,665,300,735),22,22,p);p.setColor(Color.WHITE);p.setTextSize(22);c.drawText("‹  На главную",90,710,p);
    }

    private String b64url(byte[] b){return Base64.encodeToString(b,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
    private void startSpotifyLogin(){
        try{
            byte[] rnd=new byte[48];new SecureRandom().nextBytes(rnd);pkceVerifier=b64url(rnd);
            oauthState=Long.toHexString(new SecureRandom().nextLong());
            appContext.getSharedPreferences("spotify",0).edit().putString("pkce_verifier",pkceVerifier).putString("oauth_state",oauthState).apply();
            String challenge=b64url(MessageDigest.getInstance("SHA-256").digest(pkceVerifier.getBytes("US-ASCII")));
            String scopes="user-read-private user-read-playback-state user-modify-playback-state user-read-currently-playing playlist-read-private user-library-read";
            String url="https://accounts.spotify.com/authorize?client_id="+SPOTIFY_CLIENT_ID+"&response_type=code&redirect_uri="+URLEncoder.encode(SPOTIFY_REDIRECT,"UTF-8")+"&code_challenge_method=S256&code_challenge="+challenge+"&state="+oauthState+"&scope="+URLEncoder.encode(scopes,"UTF-8");
            spotifyStatus="Откройте авторизацию на телефоне";
            Intent in=new Intent(Intent.ACTION_VIEW,Uri.parse(url));in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);appContext.startActivity(in);
            log("SPOTIFY OAuth launched");
        }catch(Throwable t){spotifyStatus="Ошибка OAuth";log("SPOTIFY OAuth error "+t);}
    }
    public void handleSpotifyCallback(Uri uri){
        if(uri==null)return; SharedPreferences ss=appContext.getSharedPreferences("spotify",0); if(pkceVerifier==null)pkceVerifier=ss.getString("pkce_verifier",null);if(oauthState==null)oauthState=ss.getString("oauth_state",null);String code=uri.getQueryParameter("code"),state=uri.getQueryParameter("state"),err=uri.getQueryParameter("error"); log("SPOTIFY callback code="+(code!=null)+" stateMatch="+(oauthState!=null&&oauthState.equals(state))+" verifier="+(pkceVerifier!=null));
        if(err!=null){spotifyStatus="Доступ отклонён";return;} if(code==null||oauthState==null||!oauthState.equals(state)){spotifyStatus="Ошибка callback/state";return;}
        final String c=code,v=pkceVerifier;spotifyStatus="Получение токена...";
        new Thread(()->exchangeSpotifyToken(c,v),"spotify-token").start();
    }
    private void exchangeSpotifyToken(String code,String verifier){
        HttpURLConnection con=null;try{
            URL u=new URL("https://accounts.spotify.com/api/token");con=(HttpURLConnection)u.openConnection();con.setRequestMethod("POST");con.setDoOutput(true);con.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
            String body="client_id="+URLEncoder.encode(SPOTIFY_CLIENT_ID,"UTF-8")+"&grant_type=authorization_code&code="+URLEncoder.encode(code,"UTF-8")+"&redirect_uri="+URLEncoder.encode(SPOTIFY_REDIRECT,"UTF-8")+"&code_verifier="+URLEncoder.encode(verifier,"UTF-8");
            try(OutputStream os=con.getOutputStream()){os.write(body.getBytes("UTF-8"));}
            int rc=con.getResponseCode();BufferedReader br=new BufferedReader(new InputStreamReader(rc<400?con.getInputStream():con.getErrorStream()));StringBuilder out=new StringBuilder();String line;while((line=br.readLine())!=null)out.append(line);br.close();
            String json=out.toString(); if(rc==200&&json.contains("\"access_token\"")){appContext.getSharedPreferences("spotify",0).edit().putString("token_json",json).remove("pkce_verifier").remove("oauth_state").apply();pkceVerifier=null;oauthState=null;spotifyStatus="Spotify подключён";log("SPOTIFY OAuth success");}else{spotifyStatus="Ошибка токена: "+rc;log("SPOTIFY token error "+rc+" "+json);}
        }catch(Throwable t){spotifyStatus="Ошибка сети";log("SPOTIFY token exception "+t);}finally{if(con!=null)con.disconnect();}
    }

    private void startAudioEngine(){
        if(audioThread!=null)return;
        audioThread=new HandlerThread("car-ui-audio");audioThread.start();audioHandler=new Handler(audioThread.getLooper());audioHandler.post(audioLoop);
    }
    private final Runnable audioLoop=new Runnable(){public void run(){
        if(audioHandler==null)return;
        final int frames=960; byte[] pcm=new byte[frames*4];
        if(playing){
            double hz=440.0; for(int i=0;i<frames;i++){short v=(short)(Math.sin(tonePhase)*7000);tonePhase+=2*Math.PI*hz/48000.0;if(tonePhase>2*Math.PI)tonePhase-=2*Math.PI;int o=i*4;pcm[o]=(byte)v;pcm[o+1]=(byte)(v>>8);pcm[o+2]=(byte)v;pcm[o+3]=(byte)(v>>8);} playedFrames+=frames;
        }
        if(listener!=null && playing)listener.onAudioData(pcm);
        if(audioHandler!=null)audioHandler.postDelayed(this,20);
    }};
    private String playerTime(){long sec=playedFrames/48000;return String.format(Locale.getDefault(),"%d:%02d",sec/60,sec%60);}

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
        if(audioHandler!=null)audioHandler.removeCallbacksAndMessages(null);if(audioThread!=null){audioThread.quitSafely();audioThread=null;}audioHandler=null;playing=false;
        if(codec!=null){try{codec.stop();}catch(Throwable ignored){}try{codec.release();}catch(Throwable ignored){}codec=null;}
        if(inputSurface!=null){try{inputSurface.release();}catch(Throwable ignored){}inputSurface=null;}
    }

    public interface VideoDataEncodeListener { void onData(byte[] data); void onAudioData(byte[] pcm); }
}
