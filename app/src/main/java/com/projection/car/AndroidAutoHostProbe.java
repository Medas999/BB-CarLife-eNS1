package com.projection.car;

import android.content.Context;
import android.content.pm.PackageInfo;
import java.io.*;
import java.net.*;
import java.util.Locale;
import static com.projection.car.Utils.log;

/** Single-shot Android Auto Head Unit Server transport diagnostic. */
public final class AndroidAutoHostProbe {
 public interface Listener { void onResult(boolean ok,String message); }
 private static volatile Socket socket; private static volatile boolean running;
 private static final byte[] VERSION_11={0x00,0x00,0x00,0x01,0x00,0x01,0x00,0x01};

 public static synchronized void startSession(Listener l){
  if(running){log("AA_V38 start ignored: session already active");return;}
  running=true; new Thread(()->run(l),"aa-v38-single").start();
 }
 public static synchronized void stopSession(){running=false;try{if(socket!=null)socket.close();}catch(Throwable ignored){}socket=null;}

 private static void run(Listener l){
  Socket s=new Socket(); socket=s;
  try{
   log("AA_V38 MODE=single-shot endpoint=127.0.0.1:5277");
   log("AA_V38 NOTE one connection only; no reconnect matrix (5277 accept loop can be parked by a silent peer)");
   long t=System.currentTimeMillis();
   s.connect(new InetSocketAddress("127.0.0.1",5277),3000);
   s.setTcpNoDelay(true); s.setKeepAlive(true); s.setSoTimeout(500);
   log("AA_V38 CONNECTED ms="+(System.currentTimeMillis()-t)+" local="+s.getLocalAddress()+":"+s.getLocalPort()+" remote="+s.getRemoteSocketAddress());
   InputStream in=s.getInputStream(); OutputStream out=s.getOutputStream();
   log("AA_V38 TX VERSION_REQUEST requested=1.1 bytes=8 hex="+hex(VERSION_11,64));
   out.write(VERSION_11); out.flush();

   ByteArrayOutputStream rx=new ByteArrayOutputStream(); byte[] b=new byte[4096];
   long start=System.currentTimeMillis(), deadline=start+12000;
   while(running && System.currentTimeMillis()<deadline){
    try{
     int n=in.read(b);
     if(n<0){log("AA_V38 EOF afterMs="+(System.currentTimeMillis()-start)+" rx="+rx.size());break;}
     if(n>0){
      rx.write(b,0,n);
      log("AA_V38 RX bytes="+n+" total="+rx.size()+" afterMs="+(System.currentTimeMillis()-start)+" hex="+hex(copy(b,n),512));
      Version v=parseVersion(rx.toByteArray());
      if(v!=null){
       log("AA_V38 VERSION_RESPONSE negotiated="+v.major+"."+v.minor+" status=0x"+String.format(Locale.US,"%04X",v.status));
       if(l!=null)l.onResult(v.status==0,"AA version "+v.major+"."+v.minor+" status="+v.status);
       break;
      }
     }
    }catch(SocketTimeoutException ignored){}
   }
   if(rx.size()==0){
    log("AA_V38 SILENT_5277 12000ms: TCP accepted but Head Unit Server did not service VERSION_REQUEST");
    log("AA_V38 RECOVERY_HINT: stop then start Android Auto Head Unit Server before next attempt; do not retry sockets");
    if(l!=null)l.onResult(false,"AA 5277 silent; restart Head Unit Server then retry once");
   } else log("AA_V38 RX_TOTAL bytes="+rx.size()+" hex="+hex(rx.toByteArray(),1024));
  }catch(Throwable e){
   log("AA_V38 END "+e.getClass().getSimpleName()+": "+e.getMessage());
   if(l!=null)l.onResult(false,"AA: "+e.getClass().getSimpleName()+": "+e.getMessage());
  }finally{running=false;try{s.close();}catch(Throwable ignored){}if(socket==s)socket=null;log("AA_V38 COMPLETE");}
 }

 private static Version parseVersion(byte[] a){
  // Expected plaintext response: 00 00 | 00 02 | major(2) | minor(2) | status(2)
  for(int o=0;o+9<a.length;o++){
   if((a[o]&255)==0&&(a[o+1]&255)==0&&(a[o+2]&255)==0&&(a[o+3]&255)==2){
    return new Version(u16(a,o+4),u16(a,o+6),u16(a,o+8));
   }
  }
  // Also log-compatible with a 4-byte [channel flags len] header if encountered.
  for(int o=0;o+11<a.length;o++){
   if((a[o]&255)==0&&(a[o+4]&255)==0&&(a[o+5]&255)==2)
    return new Version(u16(a,o+6),u16(a,o+8),u16(a,o+10));
  }
  return null;
 }
 private static int u16(byte[] a,int o){return ((a[o]&255)<<8)|(a[o+1]&255);}
 private static final class Version{final int major,minor,status;Version(int a,int b,int c){major=a;minor=b;status=c;}}
 private static byte[] copy(byte[] a,int n){byte[] b=new byte[n];System.arraycopy(a,0,b,0,n);return b;}
 private static String hex(byte[] b,int max){StringBuilder s=new StringBuilder();int n=Math.min(b.length,max);for(int i=0;i<n;i++){if(i>0)s.append(' ');s.append(String.format(Locale.US,"%02X",b[i]&255));}if(b.length>n)s.append(" ... +").append(b.length-n);return s.toString();}
}