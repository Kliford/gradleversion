package com.kru13.httpserver;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Menu;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class HttpServerActivity extends Activity implements OnClickListener{

	static {
		System.loadLibrary("native-lib");
	}

	private CapturingService service;

	private SocketServer server;
	protected CameraDevice cameraDevice;
	private Handler mBackgroundHandler;
	private HandlerThread mBackgroundThread;
	private CameraCharacteristics characteristics;
	private ImageReader reader;
	private CameraManager manager;
	private List<Surface> outputSurfaces;
	private CaptureRequest.Builder captureBuilder;
	//private static HttpServerActivity context;

	private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
	static {
		ORIENTATIONS.append(Surface.ROTATION_0, 90);
		ORIENTATIONS.append(Surface.ROTATION_90, 0);
		ORIENTATIONS.append(Surface.ROTATION_180, 270);
		ORIENTATIONS.append(Surface.ROTATION_270, 180);
	}

	private final int IMAGE_WIDTH = 320;
	private final int IMAGE_HEIGHT = 240;
	private Size imageDimension;
	protected CaptureRequest.Builder captureRequestBuilder;
	protected CameraCaptureSession cameraCaptureSessions;
	private String cameraId;
	private ImageReader imageReader;

	private static final int READ_EXTERNAL_STORAGE = 1;
	private static final int REQUEST_MEDIA_PROJECTION = 1;
	private static final int REQUEST_CAMERA_PERMISSION = 200;

	public TextView tv;
	public TextView nativeTv;
	private TextureView textureView;

	public File myExternalPhotoFile;

	private static final String TAG = "AndroidCameraApi";
	private MediaProjectionManager mMediaProjectionManager;

	private final String imageFilename = "pic.jpg";
	public final String bitmapFilename = "bitmap.jpg";
	private String htmlFileName = "page.html";
	public String externalStoragePath;
	public String imageFilePath;
	public File htmlFile;
	private String screenshotFilename = "screencap.png";
	public String screenshotFilePath;



	private static final int REQUEST_CODE = 100;
	private static String STORE_DIRECTORY;
	private static int IMAGES_PRODUCED;
	private static final String SCREENCAP_NAME = "screencap";
	private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
	private static MediaProjection sMediaProjection;

	private MediaProjectionManager mProjectionManager;
	private ImageReader mImageReader;
	private Handler mHandler;
	private Display mDisplay;
	private VirtualDisplay mVirtualDisplay;
	private int mDensity;
	private int mWidth;
	private int mHeight;
	private int mRotation;

	private static HttpServerActivity context;

	public native String stringFromJNI();



	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_http_server);

		context = this;

		externalStoragePath = Environment.getExternalStorageDirectory().getPath();

		myExternalPhotoFile = new File(externalStoragePath, imageFilename);
		imageFilePath = myExternalPhotoFile.getPath();
		screenshotFilePath = new File(externalStoragePath, screenshotFilename).getPath();


		Button btn1 = (Button)findViewById(R.id.button1);
		Button btn2 = (Button)findViewById(R.id.button2);
		tv = (TextView) findViewById(R.id.textView);
		tv.setMovementMethod(new ScrollingMovementMethod());

		textureView = (TextureView) findViewById(R.id.textureView);
		textureView.setSurfaceTextureListener(textureListener);

		nativeTv = (TextView) findViewById(R.id.JNItextView);
		nativeTv.setText( stringFromJNI() );

		btn1.setOnClickListener(this);
		btn2.setOnClickListener(this);

		if (!isExternalStorageAvailable()) {
			btn1.setEnabled(false);
			btn2.setEnabled(false);
		}

		else {
			File extStore = Environment.getExternalStorageDirectory();
			htmlFile = new File(extStore, htmlFileName);


			mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

			// start capture handling thread
			new Thread() {
				@Override
				public void run() {
					Looper.prepare();
					mHandler = new Handler();
					Looper.loop();
				}
			}.start();
		}


	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE) {
			sMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);

			if (sMediaProjection != null) {
				File externalFilesDir = getExternalFilesDir(null);
				if (externalFilesDir != null) {
					STORE_DIRECTORY = externalFilesDir.getAbsolutePath() + "/screenshots/";
					File storeDirectory = new File(STORE_DIRECTORY);
					if (!storeDirectory.exists()) {
						boolean success = storeDirectory.mkdirs();
						if (!success) {
							Log.e(TAG, "failed to create file storage directory.");
							return;
						}
					}
				} else {
					Log.e(TAG, "failed to create file storage directory, getExternalFilesDir is null.");
					return;
				}

				// display metrics
				DisplayMetrics metrics = getResources().getDisplayMetrics();
				mDensity = metrics.densityDpi;
				mDisplay = getWindowManager().getDefaultDisplay();

				// create virtual display depending on device width / height
				createVirtualDisplay();


				// register media projection stop callback
				//sMediaProjection.registerCallback(new MediaProjectionStopCallback(), mHandler);
			}
		}
	}

	private void createVirtualDisplay() {
		// get width and height
		Point size = new Point();
		mDisplay.getSize(size);
		mWidth = size.x;
		mHeight = size.y;

		// start capture reader
		mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);
		mVirtualDisplay = sMediaProjection.createVirtualDisplay(SCREENCAP_NAME, mWidth, mHeight, mDensity, VIRTUAL_DISPLAY_FLAGS, mImageReader.getSurface(), null, mHandler);
		mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
	}

	TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
			//open your camera here
			openCamera();
		}
		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
			// Transform you image captured size according to the surface width and height
		}
		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
			return false;
		}
		@Override
		public void onSurfaceTextureUpdated(SurfaceTexture surface) {
		}
	};

	private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
		@Override
		public void onOpened(CameraDevice camera) {
			//This is called when the camera is open
			Log.e(TAG, "onOpened");
			cameraDevice = camera;
			createCameraPreview();
		}
		@Override
		public void onDisconnected(CameraDevice camera) {
			cameraDevice.close();
		}
		@Override
		public void onError(CameraDevice camera, int error) {
			cameraDevice.close();
			cameraDevice = null;
		}
	};

	protected void startBackgroundThread() {
		mBackgroundThread = new HandlerThread("Camera Background");
		mBackgroundThread.start();
		mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
	}

	protected void stopBackgroundThread() {
		mBackgroundThread.quitSafely();
		try {
			mBackgroundThread.join();
			mBackgroundThread = null;
			mBackgroundHandler = null;
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	protected void takePicture() {
		if(null == cameraDevice) {
			Log.e(TAG, "cameraDevice is null");
			return;
		}
		try {
			if (manager !=null) {
				Size[] jpegSizes = null;
				if (characteristics != null) {
					jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
				}

				reader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.JPEG, 1);

				outputSurfaces = new ArrayList<Surface>(2);
				outputSurfaces.add(reader.getSurface());
				outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
			}

			captureBuilder = createCaptureBuilder();

			ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
				@Override
				public void onImageAvailable(ImageReader reader) {
					Image image = null;
					try {
						image = reader.acquireLatestImage();
						ByteBuffer buffer = image.getPlanes()[0].getBuffer();
						byte[] bytes = new byte[buffer.capacity()];
						buffer.get(bytes);
						save(bytes);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						if (image != null) {
							image.close();
						}
					}
				}

				private void save(byte[] bytes) throws IOException {
					OutputStream output = null;
					try {
						output = new FileOutputStream(myExternalPhotoFile);
						output.write(bytes);
					} finally {
						if (null != output) {
							output.close();
						}
					}
				}
			};

			reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);

			final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
				@Override
				public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
					super.onCaptureCompleted(session, request, result);
					Toast.makeText(HttpServerActivity.this, "Saved:" + myExternalPhotoFile, Toast.LENGTH_SHORT).show();
					createCameraPreview();

				}
			};

			cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
				@Override
				public void onConfigured(CameraCaptureSession session) {
					try {
						session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
					} catch (CameraAccessException e) {
						e.printStackTrace();
					}
				}

				@Override
				public void onConfigureFailed(CameraCaptureSession session) {
				}
			}, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private CaptureRequest.Builder createCaptureBuilder() throws CameraAccessException {
		captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
		captureBuilder.addTarget(reader.getSurface());
		captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
		// Orientation
		int rotation = getWindowManager().getDefaultDisplay().getRotation();
		captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

		return captureBuilder;
	}

	protected void createCameraPreview() {
		try {
			SurfaceTexture texture = textureView.getSurfaceTexture();
			assert texture != null;
			texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
			Surface surface = new Surface(texture);
			captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
			captureRequestBuilder.addTarget(surface);
			cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
				@Override
				public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
					//The camera is already closed
					if (null == cameraDevice) {
						return;
					}
					// When the session is ready, we start displaying the preview.
					cameraCaptureSessions = cameraCaptureSession;
					updatePreview();
				}
				@Override
				public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
					Toast.makeText(HttpServerActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
				}
			}, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}

	private void openCamera() {
		manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
		Log.e(TAG, "is camera open");
		try {
			cameraId = manager.getCameraIdList()[0];
			characteristics = manager.getCameraCharacteristics(cameraId);
			StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
			assert map != null;
			imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
			// Add permission for camera and let user grant the permission
			if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(HttpServerActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
				return;
			}
			manager.openCamera(cameraId, stateCallback, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
		Log.e(TAG, "openCamera X");
	}

	protected void updatePreview() {
		if(null == cameraDevice) {
			Log.e(TAG, "updatePreview error, return");
		}
		captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
		try {
			cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
	}
	private void closeCamera() {
		if (null != cameraDevice) {
			cameraDevice.close();
			cameraDevice = null;
		}
		if (null != imageReader) {
			imageReader.close();
			imageReader = null;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.e(TAG, "onResume");
		startBackgroundThread();
		if (textureView.isAvailable()) {
			openCamera();
		} else {
			textureView.setSurfaceTextureListener(textureListener);
		}

	}


	@Override
	protected void onPause() {
		Log.e(TAG, "onPause");
		//closeCamera();
		stopBackgroundThread();
		super.onPause();
	}


	private static boolean isExternalStorageAvailable() {
		String extStorageState = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(extStorageState)) {
			return true;
		}
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.http_server, menu);
		return true;
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		if (v.getId() == R.id.button1) {
			int PERMISSION_ALL = 1;
			String[] PERMISSIONS = {Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE};

			if(!hasPermissions(this, PERMISSIONS)){
				ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
			}

			else {
				//startService(new Intent(getBaseContext(), MyService.class));

				//server = new SocketServer(this);
				//server.start();

				//do cyklu, vlakna
				takePicture();
				//

				Intent intent= new Intent(this, CapturingService.class);


				//startService(intent);
				bindService(intent, mConnection,Context.BIND_AUTO_CREATE);
				startService(intent);

				startProjection();
			}
		}
		if (v.getId() == R.id.button2) {
			server.close();
			try {
				server.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void startProjection() {
		startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
	}

	public static boolean hasPermissions(Context context, String... permissions) {
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
			for (String permission : permissions) {
				if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
					return false;
				}
			}
		}
		return true;
	}

	public static HttpServerActivity getContext() {
		return context;
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		switch (requestCode) {

			case READ_EXTERNAL_STORAGE:


				//startService(new Intent(getBaseContext(), MyService.class));


				takePicture();


				Intent intent = new Intent(this, CapturingService.class);


				bindService(intent, mConnection,
						Context.BIND_AUTO_CREATE);


			case REQUEST_CAMERA_PERMISSION:
				// close the app
				Toast.makeText(HttpServerActivity.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
				finish();

				break;

			default:
				break;
		}
	}

	private ServiceConnection mConnection = new ServiceConnection() {

		public void onServiceConnected(ComponentName className,
									   IBinder binder) {
			CapturingService.MyBinder b = (CapturingService.MyBinder) binder;
			service = b.getService();
			Toast.makeText(HttpServerActivity.this, "Connected", Toast.LENGTH_SHORT)
					.show();
		}

		public void onServiceDisconnected(ComponentName className) {
			service = null;
		}
	};

	public Handler handler = new Handler(Looper.getMainLooper()){
		@Override
		public void handleMessage(Message msg) {
			tv.append(msg.obj.toString() + '\n');
			super.handleMessage(msg);
		}
	};


	private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
		@Override
		public void onImageAvailable(ImageReader reader) {
			Image image = null;
			FileOutputStream fos = null;
			Bitmap bitmap = null;

			try {
				image = mImageReader.acquireLatestImage();
				if (image != null) {
					Image.Plane[] planes = image.getPlanes();
					ByteBuffer buffer = planes[0].getBuffer();
					int pixelStride = planes[0].getPixelStride();
					int rowStride = planes[0].getRowStride();
					int rowPadding = rowStride - pixelStride * mWidth;

					// create bitmap
					bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
					bitmap.copyPixelsFromBuffer(buffer);

					// write bitmap to a file
					fos = new FileOutputStream(screenshotFilePath);
					bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);

					IMAGES_PRODUCED++;
					Log.e(TAG, screenshotFilePath + IMAGES_PRODUCED);
				}

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (fos != null) {
					try {
						fos.close();
					} catch (IOException ioe) {
						ioe.printStackTrace();
					}
				}

				if (bitmap != null) {
					bitmap.recycle();
				}

				if (image != null) {
					image.close();
				}
			}
		}
	}

	public static class CapturingService extends Service {

		private SocketServer server;
		//private CameraCapturing cameraCapturing;
		private ScreenCapturing screenCapturing;

		IBinder mBinder = new MyBinder();

		public CapturingService() {
		}

		public void onCreate () {
			Intent notificationIntent = new Intent(this, HttpServerActivity.class);

			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
					notificationIntent, 0);

			Notification notification = new NotificationCompat.Builder(this)
					.setSmallIcon(R.drawable.ic_launcher)
					.setContentTitle("My Awesome App")
					.setContentText("Doing some work...")
					.setContentIntent(pendingIntent).build();

			NotificationManager mNotificationManager =

			(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

//When you issue multiple notifications about the same type of event, it’s best practice for your app to try to update an existing notification with this new information, rather than immediately creating a new notification. If you want to update this notification at a later date, you need to assign it an ID. You can then use this ID whenever you issue a subsequent notification. If the previous notification is still visible, the system will update this existing notification, rather than create a new one. In this example, the notification’s ID is 001//

			//mNotificationManager.notify();

			mNotificationManager.notify(2, notification);

			startForeground(2, notification);
		}

		public int onStartCommand(Intent intent, int flags, int startId) {
			server = new SocketServer(HttpServerActivity.getContext());
			server.start();

			return Service.START_STICKY;
		}

		@Override
		public IBinder onBind(Intent intent) {


			return mBinder;
		}

		public class MyBinder extends Binder {
			CapturingService getService() {
				return CapturingService.this;
			}
		}

	}

}

