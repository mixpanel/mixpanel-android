1.  install selenium webdriver lib by "sudo easy_install selenium"
2.  download sample app to acceptance folder by "git clone https://github.com/mixpanel/sample-android-mixpanel-integration.git"
3.  sync Gradle with Android Studios
4.  change "com.mixpanel.android:mixpanel-android:4.1.0@aar" in build.gradle to your library location
5.  add "mavenLocal()" to
	 	repositories {
    	 	mavenCentral()
 	 	} 
    in build.gradle
6.  change minSdkVersion to 8 in AndroidManiFest.xml
7.  change mDecideEndpoint, mDecideFallbackEndpoint and mEditorUrl to your IP address with port 8000.
    eg. my IP is 172.16.20.43, so I set them to "http://172.16.20.43:8000"
8.  change MIXPANEL_API_TOKEN in config to your project token
9.  do uploadArchives
10.  launch emulator or hook up your Android device (make sure there's only 1 device/emulator connected to your
    machine
11. now run "run.sh"
