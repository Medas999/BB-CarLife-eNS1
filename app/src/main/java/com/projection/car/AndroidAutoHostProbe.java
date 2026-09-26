package com.projection.car;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import static com.projection.car.Utils.log;

/** Stage-0 probe for Android Auto Head Unit Server on the same phone. */
public final class AndroidAutoHostProbe {
    public interface Listener { void onResult(boolean ok, String message); }
    public static void probe(Listener listener) {
        new Thread(() -> {
            Socket s = new Socket();
            try {
                long t=System.currentTimeMillis();
                s.connect(new InetSocketAddress("127.0.0.1", 5277), 2500);
                s.setSoTimeout(1500);
                InputStream in=s.getInputStream(); OutputStream out=s.getOutputStream();
                String m="AA Head Unit Server reachable on 127.0.0.1:5277 in "+(System.currentTimeMillis()-t)+" ms";
                log("AA_BRIDGE PROBE OK "+m);
                listener.onResult(true,m);
            } catch (Throwable e) {
                String m="AA Head Unit Server not reachable on 127.0.0.1:5277: "+e.getClass().getSimpleName()+": "+e.getMessage();
                log("AA_BRIDGE PROBE FAIL "+m);
                listener.onResult(false,m);
            } finally { try{s.close();}catch(Throwable ignored){} }
        },"aa-host-probe").start();
    }
}
