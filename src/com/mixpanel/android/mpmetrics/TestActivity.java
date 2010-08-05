package com.mixpanel.android.mpmetrics;

import java.util.HashMap;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;

public class TestActivity extends Activity {
    
    private MPMetrics mMPMetrics;
    private String looks = "Fluffy";
    private String personality = "Viscious";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
       
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mMPMetrics = new MPMetrics(this, "1dcc469a225696478c604ddc560d1673");
        mMPMetrics.enableTestMode();
        
        RadioGroup personalityGroup = (RadioGroup) findViewById(R.id.personality_group);
        final RadioButton cute = (RadioButton) findViewById(R.id.cute);
        final RadioButton viscious = (RadioButton) findViewById(R.id.viscious);
        personalityGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                
                if (cute.getId() == checkedId) {
                    personality = "Cute";
                } else if (viscious.getId() == checkedId) {
                    personality = "Vicious";
                }
                
                
            }
        });
        RadioGroup looksGroup = (RadioGroup) findViewById(R.id.looks_group);
        final RadioButton fluffy = (RadioButton) findViewById(R.id.fluffy);
        final RadioButton spikey = (RadioButton) findViewById(R.id.spikey);
        looksGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (fluffy.getId() == checkedId) {
                    looks = "Fluffy";
                } else if (spikey.getId() == checkedId) {
                    looks = "Spiky";
                }
            }
        });
        
        
        Button submit = (Button) findViewById(R.id.submit);
        submit.setOnClickListener(new View.OnClickListener() {
            
            public void onClick(View v) {
                HashMap<String, String> map = new HashMap<String, String>();
                map.put("looks", looks);
                map.put("personality", personality);
                mMPMetrics.event("creature type", map);
                
                //map.put("property2", "value2");
                //mMPMetrics.event("event2", map);
                //HashMap<String, String> superProperties = new HashMap<String, String>();
                //superProperties.put("super1", "supervalue1");
                //mMPMetrics.register(superProperties);
                //mMPMetrics.funnel("funnel2", 1, "event1", null);
            }
        });
    }

    @Override
    protected void onPause() {
        
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
    }
    
    

}
