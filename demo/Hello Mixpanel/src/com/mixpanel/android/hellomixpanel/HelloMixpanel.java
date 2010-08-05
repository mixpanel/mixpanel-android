package com.mixpanel.android.hellomixpanel;

import java.util.HashMap;

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
        
        mMPMetrics = new MPMetrics(this, "c0cf87b23e9a08ba5842f119d367775b");
        
        mButton = (Button) findViewById(R.id.button);
        mButton.setOnClickListener(new View.OnClickListener() {
            
            public void onClick(View arg0) {
                HashMap<String, String> properties = new HashMap<String, String>();
                properties.put("gender", "male");
                mMPMetrics.event("Button Clicked", properties);
            }
        });
    }
}