package net.coding.program.common.widget;

import android.content.Context;
import android.os.CountDownTimer;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.RequestParams;

import net.coding.program.common.Global;
import net.coding.program.common.base.MyJsonResponse;
import net.coding.program.common.network.MyAsyncHttpClient;
import net.coding.program.common.util.InputCheck;
import net.coding.program.common.util.OnTextChange;

import org.json.JSONObject;

/**
 * Created by chenchao on 16/1/4.
 */
public class ValidePhoneView extends TextView {

    OnTextChange editPhone;
    String inputPhone = "";

    private String url = Global.HOST_API + "/user/generate_phone_code";

    public ValidePhoneView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setOnClickListener(v -> sendPhoneMessage());
    }

    public void setEditPhone(OnTextChange edit) {
        editPhone = edit;
    }

    public void setPhoneString(String phone) {
        inputPhone = phone;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void startTimer() {
        countDownTimer.cancel();
        countDownTimer.start();
    }

    private CountDownTimer countDownTimer = new CountDownTimer(60000, 1000) {

        public void onTick(long millisUntilFinished) {
            ValidePhoneView.this.setText(String.format("%d秒", millisUntilFinished / 1000));
            ValidePhoneView.this.setEnabled(false);
        }

        public void onFinish() {
            ValidePhoneView.this.setEnabled(true);
            ValidePhoneView.this.setText("发送验证码");
        }
    };

    public void onStop() {
        countDownTimer.cancel();
        countDownTimer.onFinish();
    }

    void sendPhoneMessage() {
        if (inputPhone.isEmpty() && editPhone == null) {
            Log.e("", "editPhone is null");
            return;
        }

        String phoneString;
        if (editPhone != null) {
           phoneString = editPhone.getText().toString();
        } else {
            phoneString = inputPhone;
        }

        if (!InputCheck.checkPhone(getContext(), phoneString)) return;

        RequestParams params = new RequestParams();
        params.put("phone", phoneString);
        AsyncHttpClient client = MyAsyncHttpClient.createClient(getContext());
        client.post(getContext(), url, params, new MyJsonResponse(getContext()) {
            @Override
            public void onMySuccess(JSONObject response) {
                super.onMySuccess(response);
                net.coding.program.common.util.SingleToast.showMiddleToast(getContext(), "验证码已发送");
            }

            @Override
            public void onMyFailure(JSONObject response) {
                super.onMyFailure(response);
                countDownTimer.cancel();
                countDownTimer.onFinish();
            }
        });

        countDownTimer.start();
    }
}
