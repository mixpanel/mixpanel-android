package com.mixpanel.android.dummy;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;

/**
 * A dummy Activity for use in unit tests.
 */
public class DummyActivity extends Activity {

    Button mButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mButton = (Button) findViewById(R.id.button);
    }
}
