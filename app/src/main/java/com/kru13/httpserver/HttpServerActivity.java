package com.kru13.httpserver;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
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

	private SocketServer s;
	//private CameraThread cameraThread;
	protected CameraDevice cameraDevice;
	private Handler mBackgroundHandler;
	private HandlerThread mBackgroundThread;

	private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
	static {
		ORIENTATIONS.append(Surface.ROTATION_0, 90);
		ORIENTATIONS.append(Surface.ROTATION_90, 0);
		ORIENTATIONS.append(Surface.ROTATION_180, 270);
		ORIENTATIONS.append(Surface.ROTATION_270, 180);
	}
	private Size imageDimension;

	private static final int READ_EXTERNAL_STORAGE = 1;

	public TextView tv;
	private TextureView textureView;

	public File myExternalFile;
	private String filename = "page.html";
	private String filepath;
	private static final String TAG = "AndroidCameraApi";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_http_server);

        Button btn1 = (Button)findViewById(R.id.button1);
        Button btn2 = (Button)findViewById(R.id.button2);
		tv = (TextView) findViewById(R.id.textView);
		tv.setMovementMethod(new ScrollingMovementMethod());

		textureView = (TextureView) findViewById(R.id.textureView);
		textureView.setSurfaceTextureListener(textureListener);

        btn1.setOnClickListener(this);
        btn2.setOnClickListener(this);

		if (!isExternalStorageAvailable()) {
			btn1.setEnabled(false);
			btn2.setEnabled(false);
		}

		else {
			File extStore = Environment.getExternalStorageDirectory();
			filepath = extStore.toString();
			myExternalFile = new File(getExternalFilesDir(filepath), filename);
		}
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

	final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
		@Override
		public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
			super.onCaptureCompleted(session, request, result);
			Toast.makeText(HttpServerActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
			createCameraPreview();
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
		CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
		try {
			CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
			Size[] jpegSizes = null;
			if (characteristics != null) {
				jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
			}
			int width = 640;
			int height = 480;
			if (jpegSizes != null && 0 < jpegSizes.length) {
				width = jpegSizes[0].getWidth();
				height = jpegSizes[0].getHeight();
			}
			ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
			List<Surface> outputSurfaces = new ArrayList<Surface>(2);
			outputSurfaces.add(reader.getSurface());
			outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
			final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
			captureBuilder.addTarget(reader.getSurface());
			captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
			// Orientation
			int rotation = getWindowManager().getDefaultDisplay().getRotation();
			captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
			final File file = new File(Environment.getExternalStorageDirectory()+"/pic.jpg");
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
						output = new FileOutputStream(file);
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
					Toast.makeText(HttpServerActivity.this, "Saved:" + file, Toast.LENGTH_SHORT).show();
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
					Toast.makeText(AndroidCameraApi.this, "Configuration change", Toast.LENGTH_SHORT).show();
				}
			}, null);
		} catch (CameraAccessException e) {
			e.printStackTrace();
		}
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
				s = new SocketServer(this);
				s.start();

				//camera = new CameraThread();
				//camera.start();
			}
		}
		if (v.getId() == R.id.button2) {
			s.close();
			try {
				s.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
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

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		switch (requestCode) {

			case READ_EXTERNAL_STORAGE:
				if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)
						&& (grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
					s = new SocketServer(this);
					s.start();

					//camera = new CameraThread();
					//camera.start();
				}
				break;

			default:
				break;
		}
	}

	public final Handler handler = new Handler(Looper.getMainLooper()){
		@Override
		public void handleMessage(Message msg) {
			tv.append(msg.obj.toString() + '\n');
			super.handleMessage(msg);
		}
	};
}
