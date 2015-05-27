package com.howoldareyou.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.facepp.error.FaceppParseException;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity implements OnClickListener {
	//用于startActivityForResult()的请求码，随便设置一个常量0x110
	private static final int PIC_CODE = 0x110;
	
	//标示UI控件的变量
	private ImageView mPhoto = null;
	private Button mGetImage = null;
	private Button mDetect = null;
	private TextView mTip = null;
	private View mWaitting = null;
	
	//标示当前所使用的图片的路径
	private String mCurrentPhotoStr = null;
	
	//用于存储经过压缩后的Bitmap对象
	private Bitmap mPhotoImg = null;
	
	//创建一个画笔，用于绘制矩形框
	private Paint mPaint = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		//完成各个UI控件的初始化
		initViews();
		
		//完成部分UI控件设置监听器的初始化
		initEvents();
		
		//完成画笔的初始化
		mPaint = new Paint();
		
	}

	private void initEvents() {
		mGetImage.setOnClickListener(this);
		mDetect.setOnClickListener(this);
	}
	
	//定义两个常量来区分图片分析之后是成功还是出错
	private static final int MSG_SUCCESS = 0x111;
	private static final int MSG_ERROR = 0x112;
	
	//用于避免子线程控制UI的异步处理
	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case MSG_SUCCESS:
				mWaitting.setVisibility(View.GONE);
				JSONObject rs = (JSONObject) msg.obj;
				prepareRsBitmap(rs);
				mPhoto.setImageBitmap(mPhotoImg);
				break;
			case MSG_ERROR:
				mWaitting.setVisibility(View.GONE);
				String errorMsg = (String) msg.obj;
				if(TextUtils.isEmpty(errorMsg)) {
					mTip.setText("确认是否联网.");
				}
				else {
					mTip.setText(errorMsg);
				}
				break;
			}
			super.handleMessage(msg);
		};
	};
	
	
	private void prepareRsBitmap(JSONObject rs) {
		//因为画矩形是在原图上面画的，因此先用bitmap取得一个复制品，再用Canvas画布画
		Bitmap bitmap = Bitmap.createBitmap(mPhotoImg.getWidth() , mPhotoImg.getHeight() , mPhotoImg.getConfig());
		Canvas canvas = new Canvas(bitmap);
		canvas.drawBitmap(mPhotoImg , 0 , 0 , null);
		
		try {
			JSONArray faces = rs.getJSONArray("face");
			
			//记录检测到了多少张脸
			int faceCount = faces.length();
			mTip.setText("发现有" + faceCount + "张脸.");
			
			for(int i=0;i<faceCount;i++) {
				//拿到单独的face对象
				JSONObject face = faces.getJSONObject(i);
				//接下来是解析face对象的属性，因为我们要围着脸画一个矩形，
				//因此需要获得人脸左上角的起始坐标和人脸的宽度和高度，这样才能确定一个矩形
				JSONObject positionObj = face.getJSONObject("position");
				float x = (float) positionObj.getJSONObject("center").getDouble("x");
				float y = (float) positionObj.getJSONObject("center").getDouble("y");
				float width = (float) positionObj.getDouble("width");
				float height = (float) positionObj.getDouble("height");
				//face++返回的那些JSON数据都是百分比，需要转换为实际像素点位置后才能使用
				x = x / 100 * bitmap.getWidth();
				y = y / 100 * bitmap.getHeight();
				width = width / 100 * bitmap.getWidth();
				height = height / 100 * bitmap.getHeight();
				//设置画笔的属性
				mPaint.setColor(Color.WHITE);
				mPaint.setStrokeWidth(3);
				//开始画矩形
				canvas.drawLine(x - width / 2 , y - height / 2 , x - width / 2 , y + height / 2 , mPaint);
				canvas.drawLine(x - width / 2 , y + height / 2 , x + width / 2 , y + height / 2 , mPaint);
				canvas.drawLine(x + width / 2 , y + height / 2 , x + width / 2 , y - height / 2 , mPaint);
				canvas.drawLine(x + width / 2 , y - height / 2 , x - width / 2 , y - height / 2 , mPaint);
				//获得图像中人物的性别和年龄
				JSONObject attributeObj = face.getJSONObject("attribute");
				String sex = attributeObj.getJSONObject("gender").getString("value");
				int age = attributeObj.getJSONObject("age").getInt("value");
				//接下来绘制显示性别和年龄的气泡，直接用TextView就能省很多事：
				//气泡背景background是一个9patch图片；drawableLeft是性别；文字text是年龄
				//现在的问题是：把一个TextView转化成Bitmap，绘制在当前的画布上
				Bitmap ageBitmap = buildAgeBitmap(age , "Male".equals(sex));
				//下面是为了让气泡与图形的大小保持一个和谐的比例
				int ageWidth = ageBitmap.getWidth();
				int ageHeight = ageBitmap.getHeight();
				if(bitmap.getWidth() < mPhoto.getWidth() && bitmap.getHeight() < mPhoto.getHeight()) {
					float ratio = Math.max(bitmap.getWidth() * 1.0f / mPhoto.getWidth() , bitmap.getHeight() * 1.0f / mPhoto.getHeight());
					ageBitmap = Bitmap.createScaledBitmap(ageBitmap, (int)(ageWidth * ratio), (int)(ageHeight * ratio) , false);
				}
				canvas.drawBitmap(ageBitmap , x - ageBitmap.getWidth() / 2 , y - height / 2 - ageBitmap.getHeight() , null);
				//把画完的位图重新赋给那个变量
				mPhotoImg = bitmap;
			}
			
		} 
		catch (JSONException e) {
			e.printStackTrace();
		}
		
	}
	
	private Bitmap buildAgeBitmap(int age, boolean isMale) {
		TextView tv = (TextView) findViewById(R.id.id_ageAndSex);
		tv.setText(" " + age + " ");
		if(isMale == true) {
			tv.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.male) , null , null , null);
		}
		else {
			tv.setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(R.drawable.female) , null , null , null);
		}
		tv.setDrawingCacheEnabled(true);
		Bitmap bitmap = Bitmap.createBitmap(tv.getDrawingCache());
		tv.destroyDrawingCache();
		return bitmap;
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.id_getImage:
			//接下来实现获取图片
			Intent intent = new Intent(Intent.ACTION_PICK);
			intent.setType("image/*");
			startActivityForResult(intent, PIC_CODE);
			break;
		case R.id.id_detect:
			//让进度条显示出来
			mWaitting.setVisibility(View.VISIBLE);
			
			//为了避免异常操作，先判断一下用户是否选择了照片
			if(mCurrentPhotoStr != null && mCurrentPhotoStr.trim().equals("") == false) {
				//如果用户已经选择了照片了
				resizePhoto();
			}
			else {
				//如果用户还没有选择图片以上来就开始分析了，就采用默认图片
				mPhotoImg = BitmapFactory.decodeResource(getResources() , R.drawable.t4);
			}
			
			FaceDetect.detect(mPhotoImg, new FaceDetect.CallBack() {
				@Override
				public void success(JSONObject result) {
					Message msg = Message.obtain();
					msg.what = MSG_SUCCESS;
					msg.obj = result;
					mHandler.sendMessage(msg);
				}
				
				@Override
				public void error(FaceppParseException exception) {
					Message msg = Message.obtain();
					msg.what = MSG_ERROR;
					msg.obj = exception.getErrorMessage();
					mHandler.sendMessage(msg);
				}
			});
			break;
		}
	}
	


	/*
	 * 由于用到了startActivityForResult()，那么肯定得有一个方法要对此作出回应
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		//如果是代表获取图片的那个请求码，就进一步执行下一步操作
		if(requestCode == PIC_CODE) {
			//还得看看Intent中的内容是不是空的
			if(intent != null) {
				Uri uri = intent.getData();
				Cursor cursor = getContentResolver().query(uri, null, null, null, null);
				cursor.moveToFirst();
				int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
				//这样就拿到了一个关键的数据：图片的路径
				mCurrentPhotoStr = cursor.getString(idx);
				cursor.close();
				//有了这个路径之后，就可以去这个路径去获取图片
				//不过由于通常一个照片好一点的动不动就是好几十MB，而Face++的SDK对此有限制，要求照片转换为二进制数据
				//最大不能超过3M，因此需要对要获取的图片进行一个压缩。所以自定义了一个resizePhoto()方法去压缩照片
				resizePhoto();
				//压缩完之后就能在屏幕上显示出来了
				mPhoto.setImageBitmap(mPhotoImg);
				mTip.setText("可以开始分析了==>");
			}
		}
		super.onActivityResult(requestCode, resultCode, intent);
	}

	/*
	 * 在这里压缩方法要注意的问题是，最终压缩后的图片不能超过3M ！！
	 */
	private void resizePhoto() {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(mCurrentPhotoStr , options);
		
		double ratio = Math.max(options.outWidth * 1.0d / 1024f , options.outHeight * 1.0d / 1024f);
		options.inSampleSize = (int) Math.ceil(ratio);
		options.inJustDecodeBounds = false;
		mPhotoImg = BitmapFactory.decodeFile(mCurrentPhotoStr , options);
	}

	private void initViews() {
		mPhoto = (ImageView) findViewById(R.id.id_photo);
		mGetImage = (Button) findViewById(R.id.id_getImage);
		mDetect = (Button) findViewById(R.id.id_detect);
		mTip = (TextView) findViewById(R.id.id_tip);
		mWaitting = findViewById(R.id.id_watting);
	}



}
