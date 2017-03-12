package com.kru13.httpserver;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Base64;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by Filip on 11.03.2017.
 */

public class CameraStream extends Thread {

    private ServerSocket se;
    private HttpServerActivity context;

    CameraStream (ServerSocket se, HttpServerActivity context) {
        this.se = se;
        this.context = context;

    }

    public void run () {
        try {
            Socket s = se.accept();


            BufferedOutputStream bout = new BufferedOutputStream( s.getOutputStream() );


                String s1 = "HTTP/1.1 200 OK" + '\n';
                bout.write(s1.getBytes());
                String s2 = "Date: " + SocketServer.getCurrentDate() + '\n';
                bout.write(s2.getBytes());
                String s3 = "Content-Type: video/x-motion-jpeg; boundary=gc0p4Jq0M2Yt08jU534c0p" + '\n' + '\n';
                bout.write(s3.getBytes());


                while (true) {
                    String s5 = "--gc0p4Jq0M2Yt08jU534c0p" + '\n';
                    bout.write(s5.getBytes());
                    String s6 = "Content-Type: image/jpeg" + '\n' + '\n';
                    bout.write(s6.getBytes());

                    Bitmap bm = BitmapFactory.decodeFile(Environment.getExternalStorageDirectory() + "/pic.jpg");
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bm.compress (Bitmap.CompressFormat.JPEG, 70, baos); //bm is the bitmap object
                    byte[] b = baos.toByteArray();

                    bout.write(b);

                    String s10 = "\n";
                    bout.write(s10.getBytes());
                }



        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
