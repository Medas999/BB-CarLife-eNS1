package com.projection.car;

import java.io.*;
import java.net.*;
import java.util.Locale;
import static com.projection.car.Utils.log;

/** Multi-variant GAL framing diagnostic for Android Auto Head Unit Server. */
public final class AndroidAutoHostProbe {
 public interface Listener { void onResult(boolean ok,String message); }
 private static volatile Socket socket; private static volatile boolean running;
 private static final byte[][] REQS={
  {0x00,0x00,0x00,0x01,0x00,0x01,0x00,0x01}, // 2-byte header + v1.1
  {0x00,0x00,0x00,0x01,0x00,0x01,0x00,0x07}, // 2-byte header + v1.7
  {0x00,0x03,0x00,0x06,0x00,0x01,0x00,0x01,0x00,0x01}, // legacy control
  {0x00,0x03,0x00,0x06,0x00,0x01,0x00,0x01,0x00,0x07}  // legacy control
 };
 private static final String[] NAMES={"HDR2-v1.1","HDR2-v1.7","HDR4-v1.1","HDR4-v1.7"};
 public static synchronized void startSession(Listener l){if(running)return;running=true;new Thread(()->run(l),"aa-gal-matrix").start();}
 public static synchronized void stopSession(){running=false;try{if(socket!=null)socket.close();}catch(Throwable ignored){}socket=null;}
 private static void run(Listener l){
  boolean matched=false;
  try{
   for(int i=0;i<REQS.length && running;i++){
    Socket s=new Socket();socket=s;ByteArrayOutputStream rx=new ByteArrayOutputStream();
    try{
     long t=System.currentTimeMillis();s.connect(new InetSocketAddress("127.0.0.1",5277),3000);s.setTcpNoDelay(true);s.setSoTimeout(400);
     log("AA_MATRIX "+NAMES[i]+" CONNECTED ms="+(System.currentTimeMillis()-t));
     InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
     log("AA_MATRIX "+NAMES[i]+" TX bytes="+REQS[i].length+" hex="+hex(REQS[i],64));out.write(REQS[i]);out.flush();
     long end=System.currentTimeMillis()+2500;byte[] b=new byte[4096];
     while(running&&System.currentTimeMillis()<end){
      try{int n=in.read(b);if(n<0){log("AA_MATRIX "+NAMES[i]+" EOF total="+rx.size());break;}if(n>0){rx.write(b,0,n);log("AA_MATRIX "+NAMES[i]+" RX bytes="+n+" total="+rx.size()+" hex="+hex(copy(b,n),256));}}
      catch(SocketTimeoutException ignored){}
     }
     if(rx.size()==0)log("AA_MATRIX "+NAMES[i]+" NO_RX 2500ms");
     else {log("AA_MATRIX "+NAMES[i]+" TOTAL hex="+hex(rx.toByteArray(),512)); if(hasSuccess(rx.toByteArray())){matched=true;log("AA_MATRIX MATCH="+NAMES[i]);if(l!=null)l.onResult(true,"AA framing match: "+NAMES[i]);break;}}
    }catch(Throwable e){log("AA_MATRIX "+NAMES[i]+" END "+e.getClass().getSimpleName()+": "+e.getMessage());}
    finally{try{s.close();}catch(Throwable ignored){} if(socket==s)socket=null;}
    try{Thread.sleep(500);}catch(InterruptedException ignored){}
   }
   if(!matched&&l!=null)l.onResult(false,"AA framing matrix complete; see log");
  }finally{running=false;socket=null;log("AA_MATRIX COMPLETE matched="+matched);}
 }
 private static boolean hasSuccess(byte[] a){
  // Accept both known response layouts: hdr2 + 0002/1/7/0000, or hdr4 carrying response payload.
  for(int o=0;o+7<a.length;o++) if((a[o]&255)==0&&(a[o+1]&255)==2){
   int st=((a[o+6]&255)<<8)|(a[o+7]&255);if(st==0)return true;
  }
  return false;
 }
 private static byte[] copy(byte[] a,int n){byte[] b=new byte[n];System.arraycopy(a,0,b,0,n);return b;}
 private static String hex(byte[] b,int max){StringBuilder s=new StringBuilder();int n=Math.min(b.length,max);for(int i=0;i<n;i++){if(i>0)s.append(' ');s.append(String.format(Locale.US,"%02X",b[i]&255));}if(b.length>n)s.append(" ... +").append(b.length-n);return s.toString();}
}