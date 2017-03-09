package com.kru13.httpserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Semaphore;

import android.os.Message;
import android.util.Log;


public class SocketServer extends Thread {

	ServerSocket serverSocket;
	public final int port = 12345;
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

    public String getCurrentDate() {
        Date date = new Date();
        return date.toString();
    }

    private class ServerThread extends Thread{

            private Socket clientSocket;
            private BufferedWriter out;

            public ServerThread(Socket clientSocket) {
                this.clientSocket = clientSocket;
            }

            public void run() {
                try {

                    Log.d("SERVER", "2.semaphore.availablePermits(): " + semaphore.availablePermits());

                    out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

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
                            try {
                                sleep(4000);
                            } catch (Exception e) {
                            }

                            int size = printOkPage(out, br);
                            addConnectionRecord(size);

                            //in.close();
                        } catch (FileNotFoundException e) {
                            printPageNotFound(out);
                        } finally {
                            out.flush();
                            //out.close();
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

        private void addConnectionRecord(int size) {
            Message msg = context.handler.obtainMessage();
            msg.obj = "File name: " + context.myExternalFile.getName()
                    + ", Size: " + Integer.toString(size) + " bytes, ThreadID: " + Thread.currentThread().getId()
                    + ", LocalSocketAddress: " + clientSocket.getLocalSocketAddress()
                    + ", RemoteSocketAddress: "
                    + clientSocket.getRemoteSocketAddress();
            context.handler.sendMessage(msg);
        }

        private int printOkPage(BufferedWriter out, BufferedReader br) {
            int size = 0;

            try {
                out.append("HTTP/1.1 200 OK" + '\n');      // nebo write();
                out.append("Date: " + getCurrentDate() + '\n');
                out.append("Content-Type: text/html" + '\n' + '\n');

                String strLine;

                while ((strLine = br.readLine()) != null) {
                    out.append(strLine + '\n');
                    size += strLine.getBytes().length;
                }
            } catch (IOException e) {
            e.printStackTrace();
            }

            return size;

        }

        private void printPageNotFound(BufferedWriter out) {
            try {
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
        }
    }
}
