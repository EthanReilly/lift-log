package com.ethanreilly.liftlog;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LiftLogPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
