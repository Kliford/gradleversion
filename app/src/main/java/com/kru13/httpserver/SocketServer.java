package com.kru13.httpserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Semaphore;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import javax.net.ssl.HttpsURLConnection;

public class SocketServer extends Thread {

	ServerSocket serverSocket;
    ServerSocket serverSocketCam;
	public final int port = 12345;
    public final int videoPort = 12346;
	boolean bRunning;
    private HttpServerActivity context;
    private ArrayList<InetAddress> socketList;
    private Semaphore semaphore;

    String myData = "";

    SocketServer (HttpServerActivity context) {
        this.context = context;
        semaphore = new Semaphore(2);
    }

	public void close() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			Log.d("SERVER", "Error, probably interrupted in accept(), see log");
			e.printStackTrace();
		}
		bRunning = false;
	}

	public void run() {
        try {
        	Log.d("SERVER", "Creating Socket");
            serverSocket = new ServerSocket(port);

            bRunning = true;



            while (bRunning) {

                Log.d("SERVER", "Socket Waiting for connection");
                Socket s = serverSocket.accept();

//                Thread cs = new Thread( new CameraStream(new ServerSocket(videoPort), context));
//                cs.start();

                Log.d("SERVER", "Socket Accepted");
                Log.d("SERVER", "1.semaphore.availablePermits(): " + semaphore.availablePermits());


                Thread th = new Thread(new ServerThread(s));
                th.start();

            }
        }
        catch (IOException e) {
            if (serverSocket != null && serverSocket.isClosed())
            	Log.d("SERVER", "Normal exit");
            else {
            	Log.d("SERVER", "Error");
            	e.printStackTrace();
            }
        }
        finally {
        	serverSocket = null;
        	bRunning = false;
        }
    }

    public static String getCurrentDate() {
        Date date = new Date();
        return date.toString();
    }

    private class ServerThread extends Thread{

            private Socket clientSocket;
            //private BufferedWriter out;

            public ServerThread(Socket clientSocket) {
                this.clientSocket = clientSocket;
            }

            public void run() {
                try {

                    Log.d("SERVER", "2.semaphore.availablePermits(): " + semaphore.availablePermits());

                    BufferedOutputStream bout = new BufferedOutputStream( clientSocket.getOutputStream() );

                    BufferedReader brin = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                    String request = brin.readLine();
//                    Log.d("TADY", request );

                    //out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

                    semaphore.acquire();
                    semaphore.availablePermits();
                    Log.d("SERVER", "3.semaphore.availablePermits(): " + semaphore.availablePermits());

                        try {
                            FileInputStream fis = new FileInputStream(context.myExternalFile);
                            DataInputStream in = new DataInputStream(fis);
                            BufferedReader br = new BufferedReader(new InputStreamReader(in));

                            //nebo
                            //PrintWriter out = new PrintWriter(s.getOutputStream());
                            //out.println("HTTP/1.1 200 OK");
                            //out.println("Content-Type: text/html"); atd

                            /*
                            try {
                                sleep(4000);
                            } catch (Exception e) {
                            }
                            */


                            if (request.contains("pic")) {
                                String s1 = "HTTP/1.1 200 OK" + '\n';
                                bout.write(s1.getBytes());
                                String s2 = "Date: " + SocketServer.getCurrentDate() + '\n';
                                bout.write(s2.getBytes());
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

                            else if (request.contains("stream")){
                                String s1 = "HTTP/1.1 200 OK" + '\n';
                                bout.write(s1.getBytes());
                                String s2 = "Date: " + SocketServer.getCurrentDate() + '\n';
                                bout.write(s2.getBytes());
                                String s3 = "Content-Type: multipart/x-mixed-replace; boundary=gc0p4Jq0M2Yt08jU534c0p" + '\n' + '\n';
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
                            }

                            else if (request.contains("favico")){
                                fis.close();
                                in.close();
                                br.close();
                                bout.close();
                                return;
                            }
                            else {
                                int size = printOkPage(bout, br);
                                addConnectionRecord(size, clientSocket);
                            }

                            fis.close();
                            in.close();
                            br.close();
                            bout.close();

                            //in.close();
                        } catch (FileNotFoundException e) {
                            printPageNotFound(bout);
                        } finally {

                            bout.flush();
                            bout.close();
                            clientSocket.close();
                            semaphore.release();
                        }




                }catch(IOException e){
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


            }

        private void printServerTooBusy(BufferedWriter out) {
            try {
                out.append("HTTP/1.1 503 Service Unavailable" + '\n');
                out.append("Content-Type: text/html" + '\n' + '\n');
                out.append("<!DOCTYPE html>" + '\n');
                out.append("<html>" + '\n');
                out.append("<body>" + '\n');
                out.append("<h1>Error 503:  Server too busy.</h1>" + '\n');
                out.append("<p>We are really sorry.</p>" + '\n');
                out.append("</body>" + '\n');
                out.append("</html>" + '\n');
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void addConnectionRecord(int size, Socket clientSocket) {
            Message msg = context.handler.obtainMessage();
            msg.obj = "File name: " + context.myExternalFile.getName()
                    + ", Size: " + Integer.toString(size) + " bytes, ThreadID: " + Thread.currentThread().getId()
                    + ", LocalSocketAddress: " + clientSocket.getLocalSocketAddress()
                    + ", RemoteSocketAddress: "
                    + clientSocket.getRemoteSocketAddress();
            context.handler.sendMessage(msg);
        }
        // out: ZMENA Z BURREFEREDWRITERU NA BUFFEREDINPUTSTREAM
        //je "obecnejsi", dokaze zapisovat data, ne jen stringy
        private int printOkPage(BufferedOutputStream out, BufferedReader br) {
            int size = 0;


            Bitmap bm = BitmapFactory.decodeFile(Environment.getExternalStorageDirectory() + "/pic.jpg");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bm.compress (Bitmap.CompressFormat.JPEG, 90, baos); //bm is the bitmap object
            byte[] b = baos.toByteArray();

            String encodedImage = Base64.encodeToString(b, Base64.DEFAULT);

            StringBuilder sb = new StringBuilder();
            sb.append("data:image/jpeg;base64,");
            sb.append(encodedImage);

            String newImage = sb.toString();


            try {
                String s1 = "HTTP/1.1 200 OK" + '\n';
                out.write(s1.getBytes());
                String s2 = "Date: " + getCurrentDate() + '\n';
                out.write(s2.getBytes());
                String s3 = "Content-Type: text/html" + '\n' + '\n';
                out.write(s3.getBytes());

                String strLine;

                while ((strLine = br.readLine()) != null) {

                    String fileLine = strLine;
                    String s4 = fileLine + '\n';
                    out.write(s4.getBytes());
                    size += s4.getBytes().length;
                }
            } catch (IOException e) {
            e.printStackTrace();
            }

            return size;

        }

        private void printPageNotFound(BufferedOutputStream out) {
            /*
            try {//TODO: prevest

                out.append("HTTP/1.1 404 Not Found" + '\n');
                out.append("Content-Type: text/html" + '\n' + '\n');
                out.append("<!DOCTYPE html>" + '\n');
                out.append("<html>" + '\n');
                out.append("<body>" + '\n');
                out.append("<h1>Error 404:  Page not found</h1>" + '\n');
                out.append("<p>We are really sorry.</p>" + '\n');
                out.append("</body>" + '\n');
                out.append("</html>" + '\n');

            } catch (IOException e) {
                e.printStackTrace();
            }
            */
        }
    }
}
