package com.projection.car;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import javax.net.ssl.*;
import static com.projection.car.Utils.log;

/** Android Auto Head Unit Server: exact AAP 1.7 + application-layer mutual TLS diagnostic. */
public final class AndroidAutoHostProbe {
 public interface Listener { void onResult(boolean ok,String message); }
 private static volatile Socket socket; private static volatile boolean running;
 private static final byte[] VERSION_17={0x00,0x03,0x00,0x06,0x00,0x01,0x00,0x01,0x00,0x07};
 private static final byte[] AUTH_OK={0x00,0x03,0x00,0x04,0x00,0x04,0x08,0x00};
 private static final String CERT_B64="MIIDJTCCAg0CAnZTMA0GCSqGSIb3DQEBCwUAMFsxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MR8wHQYDVQQKDBZHb29nbGUgQXV0b21vdGl2ZSBMaW5rMB4XDTE0MDcwODIyNDkxOFoXDTQ0MDcwNzIyNDkxOFowVTELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MSEwHwYDVQQKDBhHb29nbGUtQW5kcm9pZC1SZWZlcmVuY2UwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCpqQmvoDW/XsREoj20dRcMqJGWh8RlUoHB8CpBpsoqV4nAuvNngkyrdpCf1yg0fVAp2Ugj5eOtzbiN6BxoNHpPgiZ64pc+JRlwjmyHpssDaHzP+zHZM7acwMcroNVyynSzpiydEDyx/KPtEz5AsKi7c7AYYEtnCmAnK/waN1RT5KdZ9f97D9NeF7Ljdk+IKFROJh7Nv/YGiv9GdPZh/ezSm2qhD3gzdh9PYs2cu0u+N17PYpSYB7vXPcYa/gmIVipIJ5RuMQVBWrCgtfzwKPqbnJQVykm8LnysK+8RCgmPLN3uhsZx6Whax2TVXb1q68DoiaFPhvMfPr2i/9IKaC69AgMBAAEwDQYJKoZIhvcNAQELBQADggEBAIpfjQriEtbpUyWLoOOfJsjFN04+ajq91XALCPd+2ixWHZIBJiucrrf0H7OgY7eFnNbU0cRqiDZHI8BtvzFxNi/JgXqCmSHRrlaoIsITfqo8KHwcAMs4qWTeLQmkTXBZYz0M3HwC7N1vOGjAJJN5qENIm1Jq+/3cfxVg2zhHPKY8qtdgl73YIXb9Xx3WmPCBeRBCKJncj0Rq14uaOjWXRyBgbmdzMXJzFGPHx3wN04JqGyfPFlDazXExFQwuAryjoYBRdxPxGufeQCp3am4xxI2oxNIzR+4LnOcDhgU1B7sbkVzbKj5gjdOQAmxnKCfBtUNB63a7yzGPYGPIwlBsm54=";
 private static final String KEY_B64="MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCpqQmvoDW/XsREoj20dRcMqJGWh8RlUoHB8CpBpsoqV4nAuvNngkyrdpCf1yg0fVAp2Ugj5eOtzbiN6BxoNHpPgiZ64pc+JRlwjmyHpssDaHzP+zHZM7acwMcroNVyynSzpiydEDyx/KPtEz5AsKi7c7AYYEtnCmAnK/waN1RT5KdZ9f97D9NeF7Ljdk+IKFROJh7Nv/YGiv9GdPZh/ezSm2qhD3gzdh9PYs2cu0u+N17PYpSYB7vXPcYa/gmIVipIJ5RuMQVBWrCgtfzwKPqbnJQVykm8LnysK+8RCgmPLN3uhsZx6Whax2TVXb1q68DoiaFPhvMfPr2i/9IKaC69AgMBAAECggEAbBoW3963IG6jpA+0PW11+EzYJw/u5ZiCsS3z3s0Fd6E7VqBIQyXU8FOlpxMSvQ8zqtaVjroGLlIsS88feo4leM+28Qm70I8W/I7jPDPcmxlSnbqycnDu5EY5IeVi27eAUI+LUbBs3APb900Rl2p4uKfoBkAlC0yjI5J1GcczZhf7RDh1wGgFWZI+ljiSrfpdiA4XmcZ9c7FlO5+NTotZzYeNx1iZprajV1/dlDy8UWEkwoWtppeGzUf3HHgl8yay62ub2vo5I1Z7Z98Roq8KC1o7k2IXOrHztCl3X03gMwlIF4WQ6Fx5LZDU9dfaPhzkutekVgbtO9SzHgb3NXCZwQKBgQDcSS/OLll18ssjBwc7PsdaIFIPlF428Tk8qezEnDmHS6xeztkGnpOlilk9jYSsVUbQmq8MwBSjfMVH95B0w0yyfOYqjgTocg4lRCoPuBdnuBY/lU1Lws4FoGsGMNFkHWjHzl622mavkJiDzWA+CORPUllS/DnPKJnZk2n0zZRKaQKBgQDFKqvePMx/a/ayQ09UZYxov0vwRyNkHevmwEGQjOiHKozWvLqWhCvFtwo+VqHqmCw95cYUpg1GvppB6Lnw2uHgWAWxr3ugDjaRYSqG/L7FG6FDF+1sPvBuxNpBmto59TI1fBFmU9VBGLDnr1M27qH3KTWlA3lCsovV6Dbk7D+vNQKBgE6GgFYdS6KyFBu+a6OA84t7LgWDvDoVr3Oil1ZW4mMKZL2/OroTWUqPkNRSWFMeawn9uhzvc+v7lE/dPk+BNxwBTgMpcTJzRfue2ueTljRQ+Q1daZpyLQLwdnZUfLAVk752IGlKXYSEJPoHAiHbBZgJIPJmGy1vqbhXxlOP3SbRAoGBAJoAQ2/5gy0/sdf5FRxxmOM0D+dkWTNY36pDnrJ+LR1uUcVkckUghWQQHRMl7aBkLaJHN5lnPdV1CN3UHnAPNwBZIFFyJJiWoW6aO3JmNceVVjcmmE7FNlz+qw81GaDNcOMvvhN0BYyr8Xl1iwTMDXwVFw6FkRBUjz6L+1yBXxjFAoGAJZcU+tEM1+gHPCqHK2bPkfYOCyEAro4zY/VWXZKHgCoPau8Uc9+vFu2QVMb5kVyLTdyRLQKpooR6f8En6utS/G15YuqRYqzSTrMBzpRrqIwbgKI9RHNPAvhtVAmXnwsYDPIQ1rrELK6WzTjUySRd7gyCoq+DlY7ZKDa7FUz05Ek=";

 public static synchronized void startSession(Listener l){
  if(running){log("AA_V40 start ignored: session already active");return;}
  running=true; new Thread(()->run(l),"aa-v40-tls").start();
 }
 public static synchronized void stopSession(){running=false;try{if(socket!=null)socket.close();}catch(Throwable ignored){}socket=null;}

 private static void run(Listener l){
  Socket s=new Socket(); socket=s;
  try{
   log("AA_V40 MODE=single-shot AAP1.7+mutualTLS endpoint=127.0.0.1:5277");
   long t=System.currentTimeMillis();
   s.connect(new InetSocketAddress("127.0.0.1",5277),3000);
   s.setTcpNoDelay(true); s.setKeepAlive(true); s.setSoTimeout(2500);
   InputStream in=s.getInputStream(); OutputStream out=s.getOutputStream();
   log("AA_V40 CONNECTED ms="+(System.currentTimeMillis()-t));
   out.write(VERSION_17);out.flush();
   log("AA_V40 TX VERSION_REQUEST 1.7 hex="+hex(VERSION_17,64));

   byte[] vr=readExact(in,12);
   log("AA_V40 RX VERSION bytes="+vr.length+" hex="+hex(vr,64));
   Version v=parseVersion(vr);
   if(v==null || v.status!=0) throw new IOException("bad VERSION_RESPONSE "+hex(vr,64));
   log("AA_V40 VERSION_OK negotiated="+v.major+"."+v.minor+" status=0");

   SSLEngine eng=createEngine();
   eng.beginHandshake();
   ByteBuffer appIn=ByteBuffer.allocate(65536);
   ByteArrayOutputStream pending=new ByteArrayOutputStream();
   long deadline=System.currentTimeMillis()+15000;
   while(running && !handshakeDone(eng) && System.currentTimeMillis()<deadline){
    SSLEngineResult.HandshakeStatus hs=eng.getHandshakeStatus();
    if(hs==SSLEngineResult.HandshakeStatus.NEED_TASK){
     Runnable r; while((r=eng.getDelegatedTask())!=null)r.run(); continue;
    }
    if(hs==SSLEngineResult.HandshakeStatus.NEED_WRAP){
     ByteBuffer net=ByteBuffer.allocate(eng.getSession().getPacketBufferSize()*2);
     SSLEngineResult rr=eng.wrap(ByteBuffer.allocate(0),net);
     net.flip(); byte[] tls=new byte[net.remaining()];net.get(tls);
     if(tls.length>0){byte[] f=frame(3,tls);out.write(f);out.flush();log("AA_V40 TLS TX bytes="+tls.length+" head="+hex(tls,24));}
     if(rr.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_TASK){Runnable r;while((r=eng.getDelegatedTask())!=null)r.run();}
     continue;
    }
    if(hs==SSLEngineResult.HandshakeStatus.NEED_UNWRAP){
     AapFrame f=readFrame6(in);
     if(f.type!=3) throw new IOException("expected TLS type=3 got="+f.type);
     log("AA_V40 TLS RX bytes="+f.payload.length+" head="+hex(f.payload,24));
     pending.write(f.payload);
     byte[] all=pending.toByteArray(); ByteBuffer net=ByteBuffer.wrap(all); appIn.clear();
     SSLEngineResult rr=eng.unwrap(net,appIn);
     int consumed=rr.bytesConsumed();
     pending.reset(); if(consumed<all.length)pending.write(all,consumed,all.length-consumed);
     if(rr.getStatus()==SSLEngineResult.Status.BUFFER_UNDERFLOW) continue;
     if(rr.getStatus()!=SSLEngineResult.Status.OK) throw new SSLException("unwrap "+rr.getStatus());
     if(rr.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_TASK){Runnable r;while((r=eng.getDelegatedTask())!=null)r.run();}
     continue;
    }
    if(hs==SSLEngineResult.HandshakeStatus.FINISHED || hs==SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) break;
    throw new SSLException("unexpected handshake state "+hs);
   }
   if(!handshakeDone(eng)) throw new SSLException("TLS handshake timeout");
   SSLSession sess=eng.getSession();
   log("AA_V40 TLS_OK protocol="+sess.getProtocol()+" cipher="+sess.getCipherSuite());

   out.write(AUTH_OK);out.flush();
   log("AA_V40 TX AUTH_COMPLETE hex="+hex(AUTH_OK,64));

   // Official DHU capture shows the next frame is encrypted application traffic:
   // 4-byte AAP header followed directly by a TLS record.
   byte[] h4=readExact(in,4);
   int n=u16(h4,2); byte[] enc=readExact(in,n);
   log("AA_V40 POST_AUTH_RX channel="+(h4[0]&255)+" flags=0x"+String.format(Locale.US,"%02X",h4[1]&255)+" encryptedBytes="+n+" head="+hex(enc,32));
   log("AA_V40 MILESTONE TLS+AUTH COMPLETE; ready for encrypted service discovery");
   if(l!=null)l.onResult(true,"AA TLS authenticated; service discovery received");
  }catch(Throwable e){
   log("AA_V40 ERROR "+e.getClass().getSimpleName()+": "+e.getMessage());
   if(l!=null)l.onResult(false,"AA v4.0: "+e.getClass().getSimpleName()+": "+e.getMessage());
  }finally{running=false;try{s.close();}catch(Throwable ignored){}if(socket==s)socket=null;log("AA_V40 COMPLETE");}
 }

 private static SSLEngine createEngine() throws Exception{
  byte[] cb=Base64.getDecoder().decode(CERT_B64), kb=Base64.getDecoder().decode(KEY_B64);
  X509Certificate cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(cb));
  PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(kb));
  KeyStore ks=KeyStore.getInstance(KeyStore.getDefaultType());ks.load(null);
  char[] pass=new char[0];ks.setKeyEntry("aa",key,pass,new java.security.cert.Certificate[]{cert});
  KeyManagerFactory kmf=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());kmf.init(ks,pass);
  TrustManager[] trust={new X509TrustManager(){public void checkClientTrusted(X509Certificate[] c,String a){}public void checkServerTrusted(X509Certificate[] c,String a){}public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}}};
  SSLContext ctx=SSLContext.getInstance("TLS");ctx.init(kmf.getKeyManagers(),trust,new SecureRandom());
  SSLEngine e=ctx.createSSLEngine("android-auto",5277);e.setUseClientMode(true);
  e.setEnabledProtocols(new String[]{"TLSv1.2"});
  return e;
 }
 private static boolean handshakeDone(SSLEngine e){SSLEngineResult.HandshakeStatus h=e.getHandshakeStatus();return h==SSLEngineResult.HandshakeStatus.FINISHED||h==SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;}
 private static byte[] frame(int type,byte[] p){int len=p.length+2;byte[] b=new byte[p.length+6];b[0]=0;b[1]=3;b[2]=(byte)(len>>8);b[3]=(byte)len;b[4]=(byte)(type>>8);b[5]=(byte)type;System.arraycopy(p,0,b,6,p.length);return b;}
 private static AapFrame readFrame6(InputStream in)throws IOException{byte[] h=readExact(in,6);int len=u16(h,2);if(len<2||len>65535)throw new IOException("AAP len="+len);return new AapFrame(u16(h,4),readExact(in,len-2));}
 private static byte[] readExact(InputStream in,int n)throws IOException{byte[] b=new byte[n];int o=0;while(o<n){int r=in.read(b,o,n-o);if(r<0)throw new EOFException("need "+n+" got "+o);o+=r;}return b;}
 private static Version parseVersion(byte[] a){if(a.length>=12&&(a[0]&255)==0&&(a[4]&255)==0&&(a[5]&255)==2)return new Version(u16(a,6),u16(a,8),u16(a,10));return null;}
 private static int u16(byte[] a,int o){return ((a[o]&255)<<8)|(a[o+1]&255);}
 private static final class Version{final int major,minor,status;Version(int a,int b,int c){major=a;minor=b;status=c;}}
 private static final class AapFrame{final int type;final byte[] payload;AapFrame(int t,byte[] p){type=t;payload=p;}}
 private static String hex(byte[] b,int max){StringBuilder s=new StringBuilder();int n=Math.min(b.length,max);for(int i=0;i<n;i++){if(i>0)s.append(' ');s.append(String.format(Locale.US,"%02X",b[i]&255));}if(b.length>n)s.append(" ... +").append(b.length-n);return s.toString();}
}
