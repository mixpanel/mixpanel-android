1.  install selenium webdriver lib by "pip install selenium"
2.  change mDecideEndpoint and mEditorUrl to your IP address with port 8000.
    eg. my IP is 172.16.20.43, so add
        <meta-data android:name="com.mixpanel.android.MPConfig.DecideEndpoint" android:value="http://172.16.20.43:8000" />
        <meta-data android:name="com.mixpanel.android.MPConfig.EditorUrl" android:value="http://172.16.20.43:8000" />
    to AndroidManifest.xml of mixpanel-android.
3.  change the android library location to your local library location in the build.gradle of the test app
4.  launch emulator or hook up your Android device (make sure there's only 1 device/emulator connected to your
    machine
5.  now run "run.sh"
