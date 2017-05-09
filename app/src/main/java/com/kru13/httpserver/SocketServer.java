package com.kru13.httpserver;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Message;
import android.util.Log;

public class SocketServer extends Thread {

    private ServerSocket serverSocket;
    private final int port = 12345;
    final private int IMAGE_QUALITY = 70;
    final private int SEMAPHORE_PERMITS = 2;
    private boolean bRunning;
    private Semaphore semaphore;
    private HttpServerActivity context;

    //fractal
    private Bitmap bitmap;
    private int BITMAP_WIDTH = 800;
    private int BITMAP_HEIGHT = 600;
    private int ITERATIONS = 100;
    final private int FRACTAL_IMAGE_QUALITY = 100;

    static {
        System.loadLibrary("native-lib");
    }

    public native void changeToFractalPicture(Bitmap bitmapIn, int iterations);


    SocketServer(HttpServerActivity context) {
        semaphore = new Semaphore(SEMAPHORE_PERMITS);
        this.context = context;
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

                Log.d("SERVER", "Socket Accepted");
                Log.d("SERVER", "1.semaphore.availablePermits(): " + semaphore.availablePermits());

                Thread th = new Thread(new ServerThread(s));
                th.start();
            }
        } catch (IOException e) {
            if (serverSocket != null && serverSocket.isClosed())
                Log.d("SERVER", "Normal exit");
            else {
                Log.d("SERVER", "Error");
                e.printStackTrace();
            }
        } finally {
            serverSocket = null;
            bRunning = false;
        }
    }

    public static String getCurrentDate() {
        Date date = new Date();
        return date.toString();
    }

    private class ServerThread extends Thread {
        private Socket clientSocket;

        public ServerThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            try {

                Log.d("SERVER", "2.semaphore.availablePermits(): " + semaphore.availablePermits());

                BufferedOutputStream bout = new BufferedOutputStream(clientSocket.getOutputStream());
                BufferedReader bin = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                String request = bin.readLine();

                if (request == null) {
                    closeAndReleaseSocket(bout, clientSocket);
                    interrupt();
                    return;
                }

                if (!semaphore.tryAcquire()) {
                    sendServerTooBusyResponse(bout);
                    closeAndReleaseSocket(bout, clientSocket);
                    interrupt();
                }

                Log.d("SERVER", "3.semaphore.availablePermits(): " + semaphore.availablePermits());

                try {
                    if (request.contains("pic.jpg")) {
                        sendPictureResponse(bout, context.imageFilePath, IMAGE_QUALITY);
                    } else if (request.contains("stream.jpg")) {
                        sendMJPEGStreamResponse(bout, context.imageFilePath, IMAGE_QUALITY);
                    } else if (request.contentEquals("GET / HTTP/1.1")) { //HTML webpage

                        //Thread.sleep(4500); //simulated work to test semaphore

                        int size = sendOkPageResponse(bout, createBufferedReader(context.htmlFile));
                        addConnectionRecord(size, clientSocket);
                    } else if (request.contains("favico")) {
                        closeAndReleaseSocket(bout, clientSocket);
                        interrupt();
                    } else if (request.contains("screen")) {
                        sendMJPEGStreamScreenResponse(bout, context.screenshotFilePath, IMAGE_QUALITY);
                    } else if (request.contains("cgi-bin")) {
                        String command = request.substring(request.indexOf('n') + 2, request.indexOf("HTTP/1.1") - 1); // "GET /cgi-bin/prikaz HTTP/1.1"
                        String decodedCommand = java.net.URLDecoder.decode(command, "UTF-8");

                        execute(bout, decodedCommand);
                    }
                    else if(request.contains("fractal")) {

                        if (request.indexOf('?') != -1) {
                            Map<String, String> map = getQueryMap(request.substring(request.indexOf('?') + 1,
                                                                                    request.indexOf("HTTP/1.1") - 1));

                            if (map.containsKey("vyska")) {
                                BITMAP_HEIGHT = Integer.valueOf(map.get("vyska"));
                            }
                            if (map.containsKey("sirka")) {
                                BITMAP_WIDTH = Integer.valueOf(map.get("sirka"));
                            }
                            if (map.containsKey("iterace")) {
                                ITERATIONS = Integer.valueOf(map.get("iterace"));
                            }
                        }

                        bitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888);
                        changeToFractalPicture(bitmap,ITERATIONS);
                        sendPictureResponse(bout,bitmap,FRACTAL_IMAGE_QUALITY);
                    }
                } catch (FileNotFoundException e) {
                    printPageNotFound(bout);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    closeAndReleaseSocket(bout, clientSocket);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public Map<String, String> getQueryMap(String query)
        {
            String[] params = query.split("&");
            Map<String, String> map = new HashMap<String, String>();
            for (String param : params)
            {
                String name = param.split("=")[0];
                String value = param.split("=")[1];
                map.put(name, value);
            }
            return map;
        }

        private void sendMJPEGStreamScreenResponse(BufferedOutputStream bout, String screenshotFilePath, int image_quality) throws IOException, InterruptedException {
            String s1 = "HTTP/1.1 200 OK" + '\n';
            bout.write(s1.getBytes());
            String s2 = "Date: " + SocketServer.getCurrentDate() + '\n';
            bout.write(s2.getBytes());
            String s3 = "Content-Type: multipart/x-mixed-replace; boundary=gc0p4Jq0M2Yt08jU534c0p" + '\n' + '\n';
            bout.write(s3.getBytes());

            while (bRunning) {
                String s5 = "--gc0p4Jq0M2Yt08jU534c0p" + '\n';
                bout.write(s5.getBytes());
                String s6 = "Content-Type: image/jpeg" + '\n' + '\n';
                bout.write(s6.getBytes());

                sleep(2000);
                byte[] image = getImageInBytes(screenshotFilePath, image_quality);

                bout.write(image);

                String newLine = "\n";
                bout.write(newLine.getBytes());
            } // neukonceno s --gc0p4Jq0M2Yt08jU534c0p--

        }

        private void execute(BufferedOutputStream out, String command) {

            Process p;
            try {

                String s1 = "HTTP/1.1 200 OK" + '\n';
                out.write(s1.getBytes());
                String s2 = "Date: " + getCurrentDate() + '\n';
                out.write(s2.getBytes());
                String s3 = "Content-Type: text/html" + '\n' + '\n';
                out.write(s3.getBytes());

                p = Runtime.getRuntime().exec(command);
                p.waitFor();

                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

                String line = "";
                while ((line = reader.readLine()) != null) {

                    String responseLine = line + '\n';
                    out.write(responseLine.getBytes());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        private BufferedReader createBufferedReader(File myExternalFile) throws FileNotFoundException {
            FileInputStream fis = new FileInputStream(myExternalFile);
            DataInputStream in = new DataInputStream(fis);
            return new BufferedReader(new InputStreamReader(in));
        }

        private void closeAndReleaseSocket(BufferedOutputStream bout, Socket clientSocket) throws IOException {
            bout.flush();
            bout.close();
            clientSocket.close();
            if (semaphore.availablePermits() < SEMAPHORE_PERMITS)
                semaphore.release();
        }

        private void sendMJPEGStreamResponse(BufferedOutputStream bout, String imageFilePath, int quality) throws IOException {
            String s1 = "HTTP/1.1 200 OK" + '\n';
            bout.write(s1.getBytes());
            String s2 = "Date: " + SocketServer.getCurrentDate() + '\n';
            bout.write(s2.getBytes());
            String s3 = "Content-Type: multipart/x-mixed-replace; boundary=gc0p4Jq0M2Yt08jU534c0p" + '\n' + '\n';
            bout.write(s3.getBytes());

            while (bRunning) {
                String s5 = "--gc0p4Jq0M2Yt08jU534c0p" + '\n';
                bout.write(s5.getBytes());
                String s6 = "Content-Type: image/jpeg" + '\n' + '\n';
                bout.write(s6.getBytes());

                byte[] image = getImageInBytes(imageFilePath, quality);

                bout.write(image);

                String newLine = "\n";
                bout.write(newLine.getBytes());
            } // neukonceno --gc0p4Jq0M2Yt08jU534c0p--
        }

        private void sendPictureResponse(BufferedOutputStream bout, String pathToImage, int quality) throws IOException {
            String s1 = "HTTP/1.1 200 OK" + '\n';
            bout.write(s1.getBytes());
            String s2 = "Date: " + SocketServer.getCurrentDate() + '\n';
            bout.write(s2.getBytes());
            String s3 = "Content-Type: image/jpeg" + '\n' + '\n';
            bout.write(s3.getBytes());

            byte[] image = getImageInBytes(pathToImage, quality);

            bout.write(image);

            String newLine = "\n";
            bout.write(newLine.getBytes());
        }

        private void sendPictureResponse(BufferedOutputStream bout, Bitmap bitmap, int quality) throws IOException {
            String s1 = "HTTP/1.1 200 OK" + '\n';
            bout.write(s1.getBytes());
            String s2 = "Date: " + SocketServer.getCurrentDate() + '\n';
            bout.write(s2.getBytes());
            String s3 = "Content-Type: image/jpeg" + '\n' + '\n';
            bout.write(s3.getBytes());

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream);
            byte[] image = stream.toByteArray();

            bout.write(image);

            String newLine = "\n";
            bout.write(newLine.getBytes());
        }

        private byte[] getImageInBytes(String imageFilePath, int quality) {
            Bitmap bm = BitmapFactory.decodeFile(imageFilePath);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (bm != null) {
                bm.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            }
            return baos.toByteArray();
        }

        private void sendServerTooBusyResponse(BufferedOutputStream out) {
            try {
                String s1 = "HTTP/1.1 503 Service Unavailable" + '\n';
                out.write(s1.getBytes());
                String s2 = "Content-Type: text/html" + '\n' + '\n';
                out.write(s2.getBytes());
                String s3 = "<!DOCTYPE html>" + '\n';
                out.write(s3.getBytes());
                String s4 = "<html>" + '\n';
                out.write(s4.getBytes());
                String s5 = "<body>" + '\n';
                out.write(s5.getBytes());
                String s6 = "<h1>Error 503:  Server too busy.</h1>" + '\n';
                out.write(s6.getBytes());
                String s7 = "</body>" + '\n';
                out.write(s7.getBytes());
                String s8 = "</html>" + '\n';
                out.write(s8.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void addConnectionRecord(int size, Socket clientSocket) {
            Message msg = context.handler.obtainMessage();
            msg.obj = "File name: " + context.htmlFile.getName()
                    + ", Size: " + Integer.toString(size) + " bytes, ThreadID: " + Thread.currentThread().getId()
                    + ", LocalSocketAddress: " + clientSocket.getLocalSocketAddress()
                    + ", RemoteSocketAddress: "
                    + clientSocket.getRemoteSocketAddress();
            context.handler.sendMessage(msg);
        }

        // out: ZMENA Z BURREFEREDWRITERU NA BUFFEREDINPUTSTREAM
        //je "obecnejsi", dokaze zapisovat data, ne jen stringy
        private int sendOkPageResponse(BufferedOutputStream out, BufferedReader br) {
            int size = 0;

            try {
                String s1 = "HTTP/1.1 200 OK" + '\n';
                out.write(s1.getBytes());
                String s2 = "Date: " + getCurrentDate() + '\n';
                out.write(s2.getBytes());
                String s3 = "Content-Type: text/html" + '\n' + '\n';
                out.write(s3.getBytes());

                String strLine;

                while ((strLine = br.readLine()) != null) {
                    String responseLine = strLine + '\n';
                    out.write(responseLine.getBytes());
                    size += responseLine.getBytes().length;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return size;
        }

        private void printPageNotFound(BufferedOutputStream out) {

            try {
                String s1 = "HTTP/1.1 404 Not Found" + '\n';
                out.write(s1.getBytes());
                String s2 = "Content-Type: text/html" + '\n' + '\n';
                out.write(s2.getBytes());
                String s3 = "<!DOCTYPE html>" + '\n';
                out.write(s3.getBytes());
                String s4 = "<html>" + '\n';
                out.write(s4.getBytes());
                String s5 = "<body>" + '\n';
                out.write(s5.getBytes());
                String s6 = "<h1>Error 404:  Page not found</h1>" + '\n';
                out.write(s6.getBytes());
                String s7 = "</body>" + '\n';
                out.write(s7.getBytes());
                String s8 = "</html>" + '\n';
                out.write(s8.getBytes());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
