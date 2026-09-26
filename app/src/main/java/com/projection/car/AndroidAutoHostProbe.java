package com.projection.car;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Locale;
import static com.projection.car.Utils.log;

/** Raw GAL diagnostic transport for Android Auto Head Unit Server. */
public final class AndroidAutoHostProbe {
    public interface Listener { void onResult(boolean ok,String message); }
    private static volatile Socket socket; private static volatile boolean running;

    public static synchronized void startSession(Listener listener){
        if(running){if(listener!=null)listener.onResult(true,"AA session already running");return;}
        running=true; new Thread(()->run(listener),"aa-gal-session").start();
    }
    public static synchronized void stopSession(){running=false;try{if(socket!=null)socket.close();}catch(Throwable ignored){}socket=null;}

    private static void run(Listener listener){
        Socket s=new Socket();socket=s;
        try{
            long t=System.currentTimeMillis();
            s.connect(new InetSocketAddress("127.0.0.1",5277),3000);
            s.setTcpNoDelay(true);s.setSoTimeout(500);
            InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
            log("AA_GAL CONNECTED local=127.0.0.1:5277 ms="+(System.currentTimeMillis()-t));
            if(listener!=null)listener.onResult(true,"AA socket connected; AAP-framed GAL v1.7");

            // Android Auto GAL version exchange: 2-byte standard header + 6 raw bytes.
            // channel=0, flags=0 (control/plaintext), VERSION_REQUEST=1, version=1.7.
            byte[] req=new byte[]{0x00,0x03,0x00,0x06,0x00,0x01,0x00,0x01,0x00,0x07};
            log("AA_GAL TX AAP bytes="+req.length+" hex="+hex(req,64));
            out.write(req);out.flush();

            ByteArrayOutputStream rx=new ByteArrayOutputStream();
            long deadline=System.currentTimeMillis()+7000;
            byte[] buf=new byte[4096];
            while(running && System.currentTimeMillis()<deadline){
                try{
                    int n=in.read(buf);
                    if(n<0){log("AA_GAL EOF after rxBytes="+rx.size());break;}
                    if(n>0){
                        rx.write(buf,0,n);
                        log("AA_GAL RX CHUNK bytes="+n+" total="+rx.size()+" hex="+hex(copy(buf,n),256));
                        parseVersion(rx.toByteArray(),listener);
                        // Keep socket alive after version response: next bytes are TLS/control.
                        deadline=System.currentTimeMillis()+15000;
                    }
                }catch(SocketTimeoutException ignored){}
            }
            if(rx.size()==0)log("AA_GAL NO_RX 7000ms: localhost:5277 accepted TCP but returned zero bytes");
            else log("AA_GAL RX TOTAL bytes="+rx.size()+" hex="+hex(rx.toByteArray(),512));
        }catch(Throwable e){
            log("AA_GAL SESSION END "+e.getClass().getSimpleName()+": "+e.getMessage());
            if(listener!=null)listener.onResult(false,"AA GAL: "+e.getClass().getSimpleName()+": "+e.getMessage());
        }finally{running=false;try{s.close();}catch(Throwable ignored){}if(socket==s)socket=null;}
    }

    private static void parseVersion(byte[] a,Listener l){
        // Expected: header 00 00 followed by response 00 02 major minor status.
        for(int off=0;off+9<a.length;off++){
            if((a[off]&255)==0 && (a[off+2]&255)==0 && (a[off+3]&255)==2){
                int maj=((a[off+4]&255)<<8)|(a[off+5]&255);
                int min=((a[off+6]&255)<<8)|(a[off+7]&255);
                int st=((a[off+8]&255)<<8)|(a[off+9]&255);
                log("AA_GAL VERSION_RESPONSE offset="+off+" version="+maj+"."+min+" status="+st);
                if(l!=null)l.onResult(st==0,"AA GAL version "+maj+"."+min+" status="+st);
                return;
            }
        }
    }
    private static byte[] copy(byte[] a,int n){byte[] b=new byte[n];System.arraycopy(a,0,b,0,n);return b;}
    private static String hex(byte[] b,int max){StringBuilder s=new StringBuilder();int n=Math.min(b.length,max);for(int i=0;i<n;i++){if(i>0)s.append(' ');s.append(String.format(Locale.US,"%02X",b[i]&255));}if(b.length>n)s.append(" ... +").append(b.length-n);return s.toString();}
}
