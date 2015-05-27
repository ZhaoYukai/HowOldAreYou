package com.howoldareyou.app;

import java.io.ByteArrayOutputStream;

import org.json.JSONObject;

import com.facepp.error.FaceppParseException;
import com.facepp.http.HttpRequests;
import com.facepp.http.PostParameters;

import android.graphics.Bitmap;
import android.util.Log;

public class FaceDetect {
	
	//下面的那个detect()方法可能需要返回多个返回值：
	//如果检测成功就返回一个JSON字符串；如果检测失败就返回一个提示信息；
	//想完成这样返回多个返回值，可以考虑“接口”
	public interface CallBack {
		void success(JSONObject result);
		void error(FaceppParseException exception);
	}
	
	//用于分析图片的方法
	public static void detect(final Bitmap bitmap , final CallBack callBack) {
		//分析是很耗时的，所以要开一个子线程进行
		new Thread(new Runnable() {
			@Override
			public void run() {
				//创建request请求
				//构造器的第三个参数是问是否是中国地区。经过测试，发现第三个参数和第四个参数都为true，能获得更可靠的网络连接
				HttpRequests requests = new HttpRequests(Constant.KEY , Constant.SECRET , true , true);
				//传进来的bitmap还需要进一步处理
				Bitmap bmSmall = Bitmap.createBitmap(bitmap , 0 , 0 , bitmap.getWidth() , bitmap.getHeight());
				//将bitmap压缩到输出流当中去，然后再把这个输出流转化成字节数组
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				//通过compress()方法，就把bmSmall压缩到了stream中了
				bmSmall.compress(Bitmap.CompressFormat.JPEG , 100 , stream);
				//再把stream转化成字节数组
				byte[] arrays = stream.toByteArray();
				//有了二进制的字节之后，就可以拼接一个参数了
				PostParameters params = new PostParameters();
				params.setImg(arrays);
				//下面就可以通过网络进行分析了
				try {
					JSONObject jsonObject = requests.detectionDetect(params);
					Log.d("TAG" , jsonObject.toString());
					if(callBack != null) {
						callBack.success(jsonObject);
					}
				} 
				catch (FaceppParseException e) {
					e.printStackTrace();
					if(callBack != null) {
						callBack.error(e);
					}
				}
			}//run()方法结束
		}).start();
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}
