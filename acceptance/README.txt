1.  install selenium webdriver lib by "easy_install selenium"
2.  change mDecideEndpoint, mDecideFallbackEndpoint and mEditorUrl to your IP address with port 8000.
    eg. my IP is 172.16.20.43, so I set them to "http://172.16.20.43:8000"
3.  change the android library location to your local library location in the build.gradle of the sample app
4.  launch emulator or hook up your Android device (make sure there's only 1 device/emulator connected to your
    machine
5.  now run "run.sh"
