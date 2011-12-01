package com.mixpanel.android.hellomixpanel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.mixpanel.android.mpmetrics.MPMetrics;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class HelloMixpanel extends Activity {
    
    Button mButton;
    MPMetrics mMPMetrics;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mMPMetrics = new MPMetrics(this, "c35a4b5163ee2c097de447765f691544");
        
        mButton = (Button) findViewById(R.id.button);
        mButton.setOnClickListener(new View.OnClickListener() {
            
            public void onClick(View arg0) {
                JSONObject properties = new JSONObject();
                try {
	                properties.put("gender", "male");
	                properties.put("age", 24);
	                properties.put("registered", true);
	                properties.put("some list", new JSONArray("[1,2,3,4,5]"));
                } catch(JSONException e) { }
                mMPMetrics.track("Button Clicked", properties);
            }
        });
    }
}
