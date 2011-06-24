package com.mixpanel.android.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.UUID;

import android.content.Context;

// class that maintains a UUID to track this particular installation of the host app
//
// adapted from http://android-developers.blogspot.com/2011/03/identifying-app-installations.html
//
public class Installation {
    private static String sID = null;
    private static final String INSTALLATION = "INSTALLATION";

    public synchronized static String id(Context context) {
        if (context != null && sID == null) {  
            File installation = new File(context.getFilesDir(), INSTALLATION);
            
            try {
                if (!installation.exists()) {
                    writeInstallationFile(installation);
                }
                
                sID = readInstallationFile(installation);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        
        return sID;
    }

    private static String readInstallationFile(File installation) throws IOException {
    	if (installation != null) {
	        RandomAccessFile f = new RandomAccessFile(installation, "r");
	        byte[] bytes = new byte[(int) f.length()];
	        f.readFully(bytes);
	        f.close();
	        return new String(bytes);
    	}
    	
    	return null;
    }

    private static void writeInstallationFile(File installation) throws IOException {
    	if (installation != null) {
	        FileOutputStream out = new FileOutputStream(installation);
	        String id = UUID.randomUUID().toString();
	        out.write(id.getBytes());
	        out.close();
    	}
    }
}
