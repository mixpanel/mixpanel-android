package com.alooma.driver;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import android.view.View;
import android.widget.EditText;

import android.content.Context;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends ActionBarActivity {

    public static final String MIXPANEL_TOKEN = "d36769c7f898550cbcadc7b659bb9647";
    private MixpanelAPI mMixpanel;

    /** Called when the user clicks the Send button */
    public void sendMessage(View view) {
        EditText editText = (EditText) findViewById(R.id.edit_message);
        String val = editText.getText().toString();
        if (val.isEmpty()) {
            val = "default_value";
        }

        JSONObject properties = new JSONObject();
        try {
            properties.put("cell-kpi", val);
        } catch(JSONException e)
        {

        }

        //mMixpanel.getPeople().identify("19810");
        //mMixpanel.getPeople().set("Plan", "Premium");
        //mMixpanel.getPeople().increment("points", 500);
        //mMixpanel.getPeople().trackCharge(100, null);
        mMixpanel.track("Aloomix", properties);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMixpanel = MixpanelAPI.getInstance(this, MIXPANEL_TOKEN);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroy() {
        mMixpanel.flush();
        super.onDestroy();
    }
}
