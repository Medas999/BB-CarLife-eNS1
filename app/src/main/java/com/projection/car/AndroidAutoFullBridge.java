package com.projection.car;
import java.io.*;import java.net.*;import java.nio.*;import java.security.*;import java.security.cert.*;import java.security.spec.*;import java.util.*;import javax.net.ssl.*;
import static com.projection.car.Utils.log;

/** End-to-end Android Auto AAP 1.7 H264 receiver for the CarLife bridge. */
public final class AndroidAutoFullBridge {
 public interface Listener { void onStatus(boolean ok,String s); void onVideo(byte[] h264); }
 private static volatile boolean running; private static volatile Socket socket;
 private static final byte[] VER={0,3,0,6,0,1,0,1,0,7}, AUTH={0,3,0,4,0,4,8,0};
 private static final String CERT="MIIDJTCCAg0CAnZTMA0GCSqGSIb3DQEBCwUAMFsxCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MR8wHQYDVQQKDBZHb29nbGUgQXV0b21vdGl2ZSBMaW5rMB4XDTE0MDcwODIyNDkxOFoXDTQ0MDcwNzIyNDkxOFowVTELMAkGA1UEBhMCVVMxCzAJBgNVBAgMAkNBMRYwFAYDVQQHDA1Nb3VudGFpbiBWaWV3MSEwHwYDVQQKDBhHb29nbGUtQW5kcm9pZC1SZWZlcmVuY2UwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCpqQmvoDW/XsREoj20dRcMqJGWh8RlUoHB8CpBpsoqV4nAuvNngkyrdpCf1yg0fVAp2Ugj5eOtzbiN6BxoNHpPgiZ64pc+JRlwjmyHpssDaHzP+zHZM7acwMcroNVyynSzpiydEDyx/KPtEz5AsKi7c7AYYEtnCmAnK/waN1RT5KdZ9f97D9NeF7Ljdk+IKFROJh7Nv/YGiv9GdPZh/ezSm2qhD3gzdh9PYs2cu0u+N17PYpSYB7vXPcYa/gmIVipIJ5RuMQVBWrCgtfzwKPqbnJQVykm8LnysK+8RCgmPLN3uhsZx6Whax2TVXb1q68DoiaFPhvMfPr2i/9IKaC69AgMBAAEwDQYJKoZIhvcNAQELBQADggEBAIpfjQriEtbpUyWLoOOfJsjFN04+ajq91XALCPd+2ixWHZIBJiucrrf0H7OgY7eFnNbU0cRqiDZHI8BtvzFxNi/JgXqCmSHRrlaoIsITfqo8KHwcAMs4qWTeLQmkTXBZYz0M3HwC7N1vOGjAJJN5qENIm1Jq+/3cfxVg2zhHPKY8qtdgl73YIXb9Xx3WmPCBeRBCKJncj0Rq14uaOjWXRyBgbmdzMXJzFGPHx3wN04JqGyfPFlDazXExFQwuAryjoYBRdxPxGufeQCp3am4xxI2oxNIzR+4LnOcDhgU1B7sbkVzbKj5gjdOQAmxnKCfBtUNB63a7yzGPYGPIwlBsm54=", KEY="MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCpqQmvoDW/XsREoj20dRcMqJGWh8RlUoHB8CpBpsoqV4nAuvNngkyrdpCf1yg0fVAp2Ugj5eOtzbiN6BxoNHpPgiZ64pc+JRlwjmyHpssDaHzP+zHZM7acwMcroNVyynSzpiydEDyx/KPtEz5AsKi7c7AYYEtnCmAnK/waN1RT5KdZ9f97D9NeF7Ljdk+IKFROJh7Nv/YGiv9GdPZh/ezSm2qhD3gzdh9PYs2cu0u+N17PYpSYB7vXPcYa/gmIVipIJ5RuMQVBWrCgtfzwKPqbnJQVykm8LnysK+8RCgmPLN3uhsZx6Whax2TVXb1q68DoiaFPhvMfPr2i/9IKaC69AgMBAAECggEAbBoW3963IG6jpA+0PW11+EzYJw/u5ZiCsS3z3s0Fd6E7VqBIQyXU8FOlpxMSvQ8zqtaVjroGLlIsS88feo4leM+28Qm70I8W/I7jPDPcmxlSnbqycnDu5EY5IeVi27eAUI+LUbBs3APb900Rl2p4uKfoBkAlC0yjI5J1GcczZhf7RDh1wGgFWZI+ljiSrfpdiA4XmcZ9c7FlO5+NTotZzYeNx1iZprajV1/dlDy8UWEkwoWtppeGzUf3HHgl8yay62ub2vo5I1Z7Z98Roq8KC1o7k2IXOrHztCl3X03gMwlIF4WQ6Fx5LZDU9dfaPhzkutekVgbtO9SzHgb3NXCZwQKBgQDcSS/OLll18ssjBwc7PsdaIFIPlF428Tk8qezEnDmHS6xeztkGnpOlilk9jYSsVUbQmq8MwBSjfMVH95B0w0yyfOYqjgTocg4lRCoPuBdnuBY/lU1Lws4FoGsGMNFkHWjHzl622mavkJiDzWA+CORPUllS/DnPKJnZk2n0zZRKaQKBgQDFKqvePMx/a/ayQ09UZYxov0vwRyNkHevmwEGQjOiHKozWvLqWhCvFtwo+VqHqmCw95cYUpg1GvppB6Lnw2uHgWAWxr3ugDjaRYSqG/L7FG6FDF+1sPvBuxNpBmto59TI1fBFmU9VBGLDnr1M27qH3KTWlA3lCsovV6Dbk7D+vNQKBgE6GgFYdS6KyFBu+a6OA84t7LgWDvDoVr3Oil1ZW4mMKZL2/OroTWUqPkNRSWFMeawn9uhzvc+v7lE/dPk+BNxwBTgMpcTJzRfue2ueTljRQ+Q1daZpyLQLwdnZUfLAVk752IGlKXYSEJPoHAiHbBZgJIPJmGy1vqbhXxlOP3SbRAoGBAJoAQ2/5gy0/sdf5FRxxmOM0D+dkWTNY36pDnrJ+LR1uUcVkckUghWQQHRMl7aBkLaJHN5lnPdV1CN3UHnAPNwBZIFFyJJiWoW6aO3JmNceVVjcmmE7FNlz+qw81GaDNcOMvvhN0BYyr8Xl1iwTMDXwVFw6FkRBUjz6L+1yBXxjFAoGAJZcU+tEM1+gHPCqHK2bPkfYOCyEAro4zY/VWXZKHgCoPau8Uc9+vFu2QVMb5kVyLTdyRLQKpooR6f8En6utS/G15YuqRYqzSTrMBzpRrqIwbgKI9RHNPAvhtVAmXnwsYDPIQ1rrELK6WzTjUySRd7gyCoq+DlY7ZKDa7FUz05Ek=";
 public static synchronized void start(Listener l){if(running)return;running=true;new Thread(()->run(l),"aa-full-video").start();}
 public static synchronized void stop(){running=false;try{if(socket!=null)socket.close();}catch(Exception e){}socket=null;}

 private static void run(Listener l){
  Socket s=new Socket();socket=s;
  try{
   log("AA_FULL V4.2 start AAP1.7 TLS video bridge");
   s.connect(new InetSocketAddress("127.0.0.1",5277),3000);s.setTcpNoDelay(true);s.setKeepAlive(true);s.setSoTimeout(5000);
   InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();out.write(VER);out.flush();
   byte[] vr=readN(in,12);if(vr.length<12||u16(vr,4)!=2||u16(vr,10)!=0)throw new IOException("version rejected "+hex(vr,24));
   log("AA_FULL VERSION_OK 1."+u16(vr,8));
   SSLEngine e=engine();handshake(e,in,out);
   log("AA_FULL TLS_OK "+e.getSession().getProtocol()+" "+e.getSession().getCipherSuite());
   out.write(AUTH);out.flush();log("AA_FULL AUTH_COMPLETE");

   ByteArrayOutputStream video=new ByteArrayOutputStream(1024*1024);int videoSession=0;long frames=0;
   while(running){
    EncFrame ef=readEnc(in);byte[] plain=unwrap(e,ef.enc);
    if(plain.length==0)continue;
    int flags=ef.flags, ch=ef.channel;
    boolean hasType=(flags==0x0B||flags==0x09||flags==0x0F||flags==0x0D);
    int type=hasType&&plain.length>=2?u16(plain,0):-1;
    if(ch==0 && type==5){
      log("AA_FULL RX SERVICE_DISCOVERY_REQUEST bytes="+plain.length);
      sendEncrypted(e,out,0,0x0B,6,serviceDiscovery());
      log("AA_FULL TX SERVICE_DISCOVERY_RESPONSE video=1280x720@30 H264");
      if(l!=null)l.onStatus(true,"AA service discovery answered; waiting for video");
    } else if(type==7){
      int service=parseFieldVarint(plain,2,2,-1);
      log("AA_FULL RX CHANNEL_OPEN ch="+ch+" service="+service);
      sendEncrypted(e,out,ch, ch==0?0x0B:0x0F,8,new byte[]{8,0});
    } else if(ch==5 && type==0x8000){
      log("AA_FULL RX SYSTEM AUDIO MEDIA_SETUP");
      sendEncrypted(e,out,5,0x0F,0x8003,new byte[]{8,2,16,30,24,0});
    } else if(ch==2 && type==0x8000){
      log("AA_FULL RX VIDEO MEDIA_SETUP");
      sendEncrypted(e,out,2,0x0F,0x8003,new byte[]{8,2,16,16,24,0});
      sendEncrypted(e,out,2,0x0F,0x8008,new byte[]{8,1,16,1});
      log("AA_FULL TX VIDEO CONFIG READY maxUnacked=16 + FOCUS_PROJECTED");
    } else if(ch==2 && type==0x8001){
      videoSession=parseFieldVarint(plain,2,1,0);log("AA_FULL RX VIDEO START session="+videoSession);
      if(l!=null)l.onStatus(true,"Android Auto video started");
    } else if(ch==2 && (type==0 || type==1 || flags==8 || flags==10)){
      int off=hasType?2:0;
      if(hasType && plain.length>=off+4 && starts(plain,off+8))off+=8;
      if(type==1){ // codec config (SPS/PPS), forward as Annex-B too
        if(off<plain.length){byte[] d=Arrays.copyOfRange(plain,off,plain.length);l.onVideo(d);log("AA_FULL H264 CONFIG bytes="+d.length);}
      } else if(flags==0x0B){
        if(off<plain.length && starts(plain,off)){byte[] d=Arrays.copyOfRange(plain,off,plain.length);l.onVideo(d);frames++;if(frames<6||frames%30==0)log("AA_FULL H264 FRAME #"+frames+" bytes="+d.length+" nal="+nal(d));}
      } else if(flags==0x09){
        video.reset();if(off<plain.length)video.write(plain,off,plain.length-off);
      } else if(flags==0x08){video.write(plain);}
      else if(flags==0x0A){video.write(plain);byte[] d=video.toByteArray();if(d.length>0){l.onVideo(d);frames++;if(frames<6||frames%30==0)log("AA_FULL H264 FRAME #"+frames+" fragmented bytes="+d.length+" nal="+nal(d));}video.reset();}
      if(videoSession!=0)sendEncrypted(e,out,2,0x0F,0x8004,ack(videoSession));
    } else if(ch==0 && type==11){ // ping
      byte[] ts=Arrays.copyOfRange(plain,2,plain.length);sendEncrypted(e,out,0,0x0B,12,ts);
    } else if(hasType && (type==9||type==15)){log("AA_FULL control type="+type);}
   }
  }catch(Throwable x){log("AA_FULL ERROR "+x.getClass().getSimpleName()+": "+x.getMessage());if(l!=null)l.onStatus(false,x.getClass().getSimpleName()+": "+x.getMessage());}
  finally{running=false;try{s.close();}catch(Exception z){}if(socket==s)socket=null;log("AA_FULL COMPLETE");}
 }

 private static void handshake(SSLEngine e,InputStream in,OutputStream out)throws Exception{
  e.beginHandshake();ByteArrayOutputStream pend=new ByteArrayOutputStream();long end=System.currentTimeMillis()+15000;
  while(System.currentTimeMillis()<end){
   SSLEngineResult.HandshakeStatus h=e.getHandshakeStatus();
   if(h==SSLEngineResult.HandshakeStatus.FINISHED||h==SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)return;
   if(h==SSLEngineResult.HandshakeStatus.NEED_TASK){Runnable r;while((r=e.getDelegatedTask())!=null)r.run();continue;}
   if(h==SSLEngineResult.HandshakeStatus.NEED_WRAP){ByteBuffer n=ByteBuffer.allocate(65536);e.wrap(ByteBuffer.allocate(0),n);n.flip();byte[] b=new byte[n.remaining()];n.get(b);if(b.length>0){out.write(raw(0,3,3,b));out.flush();}continue;}
   if(h==SSLEngineResult.HandshakeStatus.NEED_UNWRAP){
    if(pend.size()==0){byte[] h6=readN(in,6);int n=u16(h6,2)-2;if(u16(h6,4)!=3)throw new IOException("expected TLS");pend.write(readN(in,n));}
    while(pend.size()>0&&e.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_UNWRAP){byte[] a=pend.toByteArray();ByteBuffer src=ByteBuffer.wrap(a),dst=ByteBuffer.allocate(65536);SSLEngineResult r=e.unwrap(src,dst);int c=r.bytesConsumed();pend.reset();if(c<a.length)pend.write(a,c,a.length-c);if(r.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_TASK){Runnable t;while((t=e.getDelegatedTask())!=null)t.run();}if(r.getStatus()==SSLEngineResult.Status.BUFFER_UNDERFLOW)break;if(r.getStatus()!=SSLEngineResult.Status.OK)throw new SSLException("unwrap "+r.getStatus());if(c==0)break;}continue;
   }
  }throw new SSLException("handshake timeout");
 }
 private static EncFrame readEnc(InputStream in)throws IOException{byte[] h=readN(in,4);int n=u16(h,2);if(n<1||n>65535)throw new IOException("enc len "+n);return new EncFrame(h[0]&255,h[1]&255,readN(in,n));}
 private static byte[] unwrap(SSLEngine e,byte[] enc)throws Exception{ByteBuffer src=ByteBuffer.wrap(enc),dst=ByteBuffer.allocate(131072);while(src.hasRemaining()){SSLEngineResult r=e.unwrap(src,dst);if(r.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NEED_TASK){Runnable t;while((t=e.getDelegatedTask())!=null)t.run();}if(r.getStatus()==SSLEngineResult.Status.BUFFER_UNDERFLOW)break;if(r.getStatus()!=SSLEngineResult.Status.OK)throw new SSLException("session unwrap "+r.getStatus());if(r.bytesConsumed()==0)break;}dst.flip();byte[] p=new byte[dst.remaining()];dst.get(p);return p;}
 private static synchronized void sendEncrypted(SSLEngine e,OutputStream out,int ch,int flags,int type,byte[] proto)throws Exception{byte[] p=new byte[2+proto.length];p[0]=(byte)(type>>8);p[1]=(byte)type;System.arraycopy(proto,0,p,2,proto.length);ByteBuffer src=ByteBuffer.wrap(p),net=ByteBuffer.allocate(65536);while(src.hasRemaining()){SSLEngineResult r=e.wrap(src,net);if(r.getStatus()!=SSLEngineResult.Status.OK)throw new SSLException("wrap "+r.getStatus());}net.flip();byte[] enc=new byte[net.remaining()];net.get(enc);byte[] h={(byte)ch,(byte)flags,(byte)(enc.length>>8),(byte)enc.length};out.write(h);out.write(enc);out.flush();}

 // Minimal valid HU: video sink only, H264 BP, 1280x720, 30 fps + required identity.
 private static byte[] serviceDiscovery()throws IOException{
  PB r=new PB();

  // Sensor source (channel 1): driving status + night.
  PB senSvc=new PB();senSvc.v(1,1);PB senSrc=new PB();
  PB s1=new PB();s1.v(1,1);senSrc.msg(1,s1.b());
  PB s2=new PB();s2.v(1,10);senSrc.msg(1,s2.b());
  senSvc.msg(2,senSrc.b());r.msg(1,senSvc.b());

  // Video sink (channel 2): H.264 BP, Android Auto's 1280x720 profile, 30 fps.
  PB vidSvc=new PB();vidSvc.v(1,2);PB sink=new PB();sink.v(1,3);sink.v(2,0);sink.v(5,1);
  PB vc=new PB();vc.v(1,2);vc.v(2,2);vc.v(3,0);vc.v(4,0);vc.v(5,160);vc.v(8,10000);vc.v(10,3);
  sink.msg(4,vc.b());vidSvc.msg(3,sink.b());r.msg(1,vidSvc.b());

  // Input source (channel 3): touchscreen matching the AA canvas.
  PB inSvc=new PB();inSvc.v(1,3);PB input=new PB();PB touch=new PB();touch.v(1,1280);touch.v(2,720);
  input.msg(2,touch.b());inSvc.msg(4,input.b());r.msg(1,inSvc.b());

  // System audio sink (channel 5) is mandatory for a viable AA head unit profile.
  PB auSvc=new PB();auSvc.v(1,5);PB auSink=new PB();auSink.v(1,1);auSink.v(2,2);
  PB ac=new PB();ac.v(1,48000);ac.v(2,16);ac.v(3,2);auSink.msg(3,ac.b());auSink.v(5,1);
  auSvc.msg(3,auSink.b());r.msg(1,auSvc.b());

  r.str(2,"Honda");r.str(3,"e:NS1");r.str(4,"2026");r.str(5,"ens1-aa-bridge-moto-v43");r.v(6,0);
  r.str(7,"OpenHU");r.str(8,"eNS1 Bridge");r.str(9,"1");r.str(10,"4.3");r.v(11,0);r.str(14,"Android Auto");

  // Explicit HeadUnitInfo. Vehicle type 3 = motorcycle: AA then uses the phone microphone,
  // so omitting a head-unit microphone service does not make discovery invalid.
  PB hi=new PB();hi.str(1,"Honda");hi.str(2,"e:NS1");hi.str(3,"2026");hi.str(4,"ens1-aa-bridge-moto-v43");
  hi.str(5,"OpenHU");hi.str(6,"eNS1 Bridge");hi.str(7,"1");hi.str(8,"4.3");hi.v(9,3);
  r.msg(17,hi.b());
  return r.b();
 }
 private static byte[] ack(int sid)throws IOException{PB p=new PB();p.v(1,sid);p.v(2,1);return p.b();}
 private static int parseFieldVarint(byte[] b,int off,int wanted,int def){try{int i=off;while(i<b.length){int[] q=rv(b,i);int tag=q[0];i=q[1];int f=tag>>3,w=tag&7;if(w==0){q=rv(b,i);if(f==wanted)return q[0];i=q[1];}else if(w==2){q=rv(b,i);i=q[1]+q[0];}else break;} }catch(Exception e){}return def;}
 private static int[] rv(byte[] b,int i){int v=0,s=0;while(i<b.length){int x=b[i++]&255;v|=(x&127)<<s;if((x&128)==0)return new int[]{v,i};s+=7;}return new int[]{v,i};}
 private static boolean starts(byte[] b,int o){return o>=0&&o+3<b.length&&b[o]==0&&b[o+1]==0&&(b[o+2]==1||(b[o+2]==0&&b[o+3]==1));}
 private static int nal(byte[] b){for(int i=0;i+4<b.length;i++)if(starts(b,i)){int j=i+(b[i+2]==1?3:4);return j<b.length?(b[j]&31):-1;}return -1;}
 private static byte[] raw(int ch,int flags,int type,byte[] p){int n=p.length+2;byte[] b=new byte[p.length+6];b[0]=(byte)ch;b[1]=(byte)flags;b[2]=(byte)(n>>8);b[3]=(byte)n;b[4]=(byte)(type>>8);b[5]=(byte)type;System.arraycopy(p,0,b,6,p.length);return b;}
 private static byte[] readN(InputStream in,int n)throws IOException{byte[] b=new byte[n];int o=0;while(o<n){int x=in.read(b,o,n-o);if(x<0)throw new EOFException();o+=x;}return b;}
 private static int u16(byte[] b,int o){return ((b[o]&255)<<8)|(b[o+1]&255);}
 private static String hex(byte[] b,int n){StringBuilder s=new StringBuilder();for(int i=0;i<Math.min(n,b.length);i++)s.append(String.format("%02X ",b[i]&255));return s.toString();}
 private static SSLEngine engine()throws Exception{X509Certificate c=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(CERT)));PrivateKey k=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(KEY)));KeyStore ks=KeyStore.getInstance(KeyStore.getDefaultType());ks.load(null);ks.setKeyEntry("aa",k,new char[0],new java.security.cert.Certificate[]{c});KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(ks,new char[0]);TrustManager[] tm={new X509TrustManager(){public void checkClientTrusted(X509Certificate[]x,String a){}public void checkServerTrusted(X509Certificate[]x,String a){}public X509Certificate[]getAcceptedIssuers(){return new X509Certificate[0];}}};SSLContext x=SSLContext.getInstance("TLS");x.init(km.getKeyManagers(),tm,new SecureRandom());SSLEngine e=x.createSSLEngine("android-auto",5277);e.setUseClientMode(true);e.setEnabledProtocols(new String[]{"TLSv1.2"});return e;}
 private static final class EncFrame{final int channel,flags;final byte[] enc;EncFrame(int c,int f,byte[]e){channel=c;flags=f;enc=e;}}
 private static final class PB{final ByteArrayOutputStream o=new ByteArrayOutputStream();void tag(int f,int w){vv((f<<3)|w);}void vv(long v){while((v&~127L)!=0){o.write((int)((v&127)|128));v>>>=7;}o.write((int)v);}void v(int f,long v){tag(f,0);vv(v);}void str(int f,String s)throws IOException{byte[]b=s.getBytes("UTF-8");msg(f,b);}void msg(int f,byte[]b)throws IOException{tag(f,2);vv(b.length);o.write(b);}byte[]b(){return o.toByteArray();}}
}
