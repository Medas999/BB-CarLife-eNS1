package com.projection.car;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;
import static com.projection.car.Utils.log;

/**
 * Android Auto Head Unit Server transport.
 * Stage 1: real AAP version negotiation + framed RX/TX capture.
 * The next protocol phase is app-layer TLS; every byte is logged so we can
 * implement the exact Samsung/AA behavior observed on the device.
 */
public final class AndroidAutoHostProbe {
    public interface Listener { void onResult(boolean ok,String message); }
    private static volatile Socket socket;
    private static volatile boolean running;

    public static void probe(Listener listener){ startSession(listener); }

    public static synchronized void startSession(Listener listener){
        if(running){ if(listener!=null)listener.onResult(true,"AA session already running"); return; }
        running=true;
        new Thread(() -> run(listener),"aa-host-session").start();
    }

    public static synchronized void stopSession(){
        running=false; try{if(socket!=null)socket.close();}catch(Throwable ignored){} socket=null;
    }

    private static void run(Listener listener){
        Socket s=new Socket(); socket=s;
        try{
            long t=System.currentTimeMillis();
            s.connect(new InetSocketAddress("127.0.0.1",5277),3000);
            s.setTcpNoDelay(true); s.setSoTimeout(5000);
            InputStream in=s.getInputStream(); OutputStream out=s.getOutputStream();
            log("AA_BRIDGE CONNECTED 127.0.0.1:5277 ms="+(System.currentTimeMillis()-t));
            if(listener!=null)listener.onResult(true,"AA connected; starting AAP v1.2");

            // AAP transport header: channel=0, FIRST|LAST=0x03, payload length=6.
            // Payload: VERSION_REQUEST(0x0001), major=1, minor=1.
            byte[] versionRequest=new byte[]{0x00,0x03,0x00,0x06, 0x00,0x01,0x00,0x01,0x00,0x02};
            tx(out,"VERSION_REQUEST",versionRequest);

            while(running){
                byte[] h=readExact(in,4);
                if(h==null)throw new java.io.EOFException("AA socket EOF");
                int ch=h[0]&255, flags=h[1]&255, len=((h[2]&255)<<8)|(h[3]&255);
                if(len<0||len>1048576)throw new java.io.IOException("bad AAP len="+len);
                byte[] p=readExact(in,len); if(p==null)throw new java.io.EOFException("AA payload EOF");
                log(String.format(Locale.US,"AA_RX frame ch=%d flags=0x%02X len=%d head=%s",ch,flags,len,hex(p,96)));
                if(ch==0 && p.length>=2){
                    int type=((p[0]&255)<<8)|(p[1]&255);
                    if(type==2 && p.length>=8){
                        int maj=((p[2]&255)<<8)|(p[3]&255), min=((p[4]&255)<<8)|(p[5]&255), status=((p[6]&255)<<8)|(p[7]&255);
                        log("AA_VERSION_RESPONSE major="+maj+" minor="+min+" status="+status);
                        if(listener!=null)listener.onResult(status==0,"AA version "+maj+"."+min+" status="+status+"; waiting TLS phase");
                    } else if(type==3){
                        log("AA_TLS_RX bytes="+(p.length-2)+" data="+hex(slice(p,2),96));
                    } else log(String.format(Locale.US,"AA_CONTROL_RX type=0x%04X bytes=%d",type,p.length));
                }
            }
        }catch(Throwable e){
            log("AA_BRIDGE SESSION END "+e.getClass().getSimpleName()+": "+e.getMessage());
            if(listener!=null)listener.onResult(false,"AA session: "+e.getClass().getSimpleName()+": "+e.getMessage());
        }finally{
            running=false;try{s.close();}catch(Throwable ignored){}if(socket==s)socket=null;
        }
    }

    private static void tx(OutputStream out,String what,byte[] b)throws Exception{
        log("AA_TX "+what+" bytes="+b.length+" hex="+hex(b,128));out.write(b);out.flush();
    }
    private static byte[] readExact(InputStream in,int n)throws Exception{
        byte[] b=new byte[n];int o=0;while(o<n){int r=in.read(b,o,n-o);if(r<0)return null;if(r==0)continue;o+=r;}return b;
    }
    private static byte[] slice(byte[] a,int off){byte[] b=new byte[Math.max(0,a.length-off)];System.arraycopy(a,off,b,0,b.length);return b;}
    private static String hex(byte[] b,int max){StringBuilder s=new StringBuilder();int n=Math.min(b.length,max);for(int i=0;i<n;i++){if(i>0)s.append(' ');s.append(String.format(Locale.US,"%02X",b[i]&255));}if(b.length>n)s.append(" ... +").append(b.length-n);return s.toString();}
}
