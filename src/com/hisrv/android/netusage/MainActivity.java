package com.hisrv.android.netusage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private ImageView mImageRand;
	private ProgressDialog mProgressDialog;
	private EditText mEditRand, mEditPhone;
	private TextView mTextStatus, mTextType, mTextPeriod, mTextUsage,
			mTextTotal;
	private String mSession;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mImageRand = (ImageView) findViewById(R.id.image_rand);
		mEditRand = (EditText) findViewById(R.id.edit_rand);
		mEditPhone = (EditText) findViewById(R.id.edit_phone);
		mTextType = (TextView) findViewById(R.id.text_type);
		mTextPeriod = (TextView) findViewById(R.id.text_period);
		mTextUsage = (TextView) findViewById(R.id.text_usage);
		mTextTotal = (TextView) findViewById(R.id.text_total);
		mTextStatus = (TextView) findViewById(R.id.text_status);
		mEditPhone.setText(loadPhoneNumber());
		mImageRand.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				new RandTask().execute();
			}
		});
		findViewById(R.id.btn_query).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				savePhoneNumber(mEditPhone.getText().toString());
				new QueryTask().execute();
			}
		});
		new RandTask().execute();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	private Bitmap getRandImage() {
		HttpGet httpGet = new HttpGet(Urls.CAPTCHA);
		HttpClient httpClient = new DefaultHttpClient();
		try {
			HttpResponse resp = httpClient.execute(httpGet);
			Header[] headers = resp.getHeaders("Set-Cookie");
			if (headers.length > 0) {
				mSession = headers[0].getValue();
			}
			if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				InputStream is = resp.getEntity().getContent();
				return BitmapFactory.decodeStream(is);
			} else {
				return null;
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private FlowInfo queryFlowInfo(String phone, String captcha) {
		try {
			List<BasicNameValuePair> list = new ArrayList<BasicNameValuePair>();
			list.add(new BasicNameValuePair("num", phone));
			list.add(new BasicNameValuePair("code", captcha));
			HttpPost post = new HttpPost(Urls.QUERY);
			post.setEntity(new UrlEncodedFormEntity(list, HTTP.UTF_8));
			post.setHeader("Cookie", mSession);
			HttpClient httpClient = new DefaultHttpClient();
			HttpResponse resp = httpClient.execute(post);
			String data = EntityUtils.toString(resp.getEntity(), HTTP.UTF_8);
			JSONObject json = new JSONObject(data);
			int ret = json.getInt("ret");
			FlowInfo info = new FlowInfo();
			info.error = ret;
			if (ret == 0) {
				JSONObject item = json.getJSONObject("item");
				info.type = item.getString("tctype");
				info.usage = item.getString("usedflow");
				info.total = item.getString("totalamount");
				info.period = item.getString("createtime") + "~"
						+ item.getString("etime");
				info.status = item.getString("status");
			}
			return info;
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private void showProgressDialog(int strId) {
		mProgressDialog = ProgressDialog.show(this, null, getString(strId));
	}

	private void hideProgressDialog() {
		mProgressDialog.dismiss();
	}

	private void savePhoneNumber(String number) {
		SharedPreferences sp = PreferenceManager
				.getDefaultSharedPreferences(this);
		sp.edit().putString("phone", number).commit();
	}

	private String loadPhoneNumber() {
		SharedPreferences sp = PreferenceManager
				.getDefaultSharedPreferences(this);
		return sp.getString("phone", "");
	}

	private class RandTask extends AsyncTask<Void, Void, Bitmap> {

		@Override
		protected void onPreExecute() {
			showProgressDialog(R.string.fetching_captcha);
		}

		@Override
		protected Bitmap doInBackground(Void... params) {
			return getRandImage();
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			hideProgressDialog();
			if (result == null) {
				Toast.makeText(MainActivity.this, R.string.err_network,
						Toast.LENGTH_LONG).show();
				finish();
			} else {
				mImageRand.setImageBitmap(result);
			}
		}
	}

	private class QueryTask extends AsyncTask<Void, Void, FlowInfo> {

		@Override
		protected void onPreExecute() {
			showProgressDialog(R.string.querying);
		}

		@Override
		protected FlowInfo doInBackground(Void... params) {
			return queryFlowInfo(mEditPhone.getText().toString(), mEditRand
					.getText().toString());
		}

		@Override
		protected void onPostExecute(FlowInfo result) {
			hideProgressDialog();
			new RandTask().execute();
			if (result == null) {
				Toast.makeText(MainActivity.this, R.string.err_network,
						Toast.LENGTH_SHORT).show();
			} else if (result.error == FlowInfo.ERR_PHONE) {
				Toast.makeText(MainActivity.this, R.string.err_phone,
						Toast.LENGTH_SHORT).show();
			} else if (result.error == FlowInfo.ERR_CAPTCHA) {
				Toast.makeText(MainActivity.this, R.string.err_captcha,
						Toast.LENGTH_SHORT).show();
			} else if (result.error == FlowInfo.SUCCESSED) {
				mTextUsage.setText(result.usage);
				mTextTotal.setText(result.total);
				mTextStatus.setText(result.status);
				mTextPeriod.setText(result.period);
				mTextType.setText(result.type);
			} else {
				Toast.makeText(MainActivity.this, R.string.err_network,
						Toast.LENGTH_SHORT).show();
			}
		}
	}

}
