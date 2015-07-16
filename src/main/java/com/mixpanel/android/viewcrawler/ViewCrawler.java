package com.mixpanel.android.viewcrawler;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.JsonWriter;
import android.util.Log;
import android.util.Pair;

import com.mixpanel.android.mpmetrics.MPConfig;
import com.mixpanel.android.mpmetrics.MixpanelAPI;
import com.mixpanel.android.mpmetrics.ResourceIds;
import com.mixpanel.android.mpmetrics.ResourceReader;
import com.mixpanel.android.mpmetrics.SuperPropertyUpdate;
import com.mixpanel.android.mpmetrics.Tweaks;
import com.mixpanel.android.util.JSONUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.SSLSocketFactory;

/**
 * This class is for internal use by the Mixpanel API, and should
 * not be called directly by your code.
 */
@TargetApi(MPConfig.UI_FEATURES_MIN_API)
public class ViewCrawler implements UpdatesFromMixpanel, TrackingDebug, ViewVisitor.OnLayoutErrorListener {

    public ViewCrawler(Context context, String token, MixpanelAPI mixpanel, Tweaks tweaks) {
        mConfig = MPConfig.getInstance(context);

        mEditState = new EditState();
        mTweaks = tweaks;
        mDeviceInfo = mixpanel.getDeviceInfo();
        mScaledDensity = Resources.getSystem().getDisplayMetrics().scaledDensity;

        final Application app = (Application) context.getApplicationContext();
        app.registerActivityLifecycleCallbacks(new LifecycleCallbacks());

        final HandlerThread thread = new HandlerThread(ViewCrawler.class.getCanonicalName());
        thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mMessageThreadHandler = new ViewCrawlerHandler(context, token, thread.getLooper(), this);

        mDynamicEventTracker = new DynamicEventTracker(mixpanel, mMessageThreadHandler);
        mMixpanel = mixpanel;
        mTweaks.addOnTweakDeclaredListener(new Tweaks.OnTweakDeclaredListener() {
            @Override
            public void onTweakDeclared() {
                final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_SEND_DEVICE_INFO);
                mMessageThreadHandler.sendMessage(msg);
            }
        });
    }

    @Override
    public void startUpdates() {
        mMessageThreadHandler.start();
        mMessageThreadHandler.sendMessage(mMessageThreadHandler.obtainMessage(MESSAGE_INITIALIZE_CHANGES));
    }

    @Override
    public Tweaks getTweaks() {
        return mTweaks;
    }

    @Override
    public void setEventBindings(JSONArray bindings) {
        final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_EVENT_BINDINGS_RECEIVED);
        msg.obj = bindings;
        mMessageThreadHandler.sendMessage(msg);
    }

    @Override
    public void setVariants(JSONArray variants) {
        final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_VARIANTS_RECEIVED);
        msg.obj = variants;
        mMessageThreadHandler.sendMessage(msg);
    }

    @Override
    public void reportTrack(String eventName) {
        final Message m = mMessageThreadHandler.obtainMessage();
        m.what = MESSAGE_SEND_EVENT_TRACKED;
        m.obj = eventName;

        mMessageThreadHandler.sendMessage(m);
    }

    @Override
    public void onLayoutError(ViewVisitor.LayoutErrorMessage e) {
        final Message m = mMessageThreadHandler.obtainMessage();
        m.what = MESSAGE_SEND_LAYOUT_ERROR;
        m.obj = e;
        mMessageThreadHandler.sendMessage(m);
    }

    private class EmulatorConnector implements Runnable {
        public EmulatorConnector() {
            mStopped = true;
        }

        @Override
        public void run() {
            if (! mStopped) {
                final Message message = mMessageThreadHandler.obtainMessage(MESSAGE_CONNECT_TO_EDITOR);
                mMessageThreadHandler.sendMessage(message);
            }

            mMessageThreadHandler.postDelayed(this, EMULATOR_CONNECT_ATTEMPT_INTERVAL_MILLIS);
        }

        public void start() {
            mStopped = false;
            mMessageThreadHandler.post(this);
        }

        public void stop() {
            mStopped = true;
            mMessageThreadHandler.removeCallbacks(this);
        }

        private volatile boolean mStopped;
    }

    private class LifecycleCallbacks implements Application.ActivityLifecycleCallbacks, FlipGesture.OnFlipGestureListener {

        public LifecycleCallbacks() {
            mFlipGesture = new FlipGesture(this);
            mEmulatorConnector = new EmulatorConnector();
        }

        @Override
        public void onFlipGesture() {
            final Message message = mMessageThreadHandler.obtainMessage(MESSAGE_CONNECT_TO_EDITOR);
            mMessageThreadHandler.sendMessage(message);
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle bundle) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityResumed(Activity activity) {
            installConnectionSensor(activity);
            mEditState.add(activity);
        }

        @Override
        public void onActivityPaused(Activity activity) {
            mEditState.remove(activity);
            if (mEditState.isEmpty()) {
                uninstallConnectionSensor(activity);
            }
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }

        private void installConnectionSensor(final Activity activity) {
            if (isInEmulator() && !mConfig.getDisableEmulatorBindingUI()) {
                mEmulatorConnector.start();
            } else if (!mConfig.getDisableGestureBindingUI()) {
                final SensorManager sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
                final Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                sensorManager.registerListener(mFlipGesture, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

        private void uninstallConnectionSensor(final Activity activity) {
            if (isInEmulator() && !mConfig.getDisableEmulatorBindingUI()) {
                mEmulatorConnector.stop();
            } else if (!mConfig.getDisableGestureBindingUI()) {
                final SensorManager sensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
                sensorManager.unregisterListener(mFlipGesture);
            }
        }

        private boolean isInEmulator() {
            if (!Build.HARDWARE.equals("goldfish")) {
                return false;
            }

            if (!Build.BRAND.startsWith("generic")) {
                return false;
            }

            if (!Build.DEVICE.startsWith("generic")) {
                return false;
            }

            if (!Build.PRODUCT.contains("sdk")) {
                return false;
            }

            if (!Build.MODEL.toLowerCase(Locale.US).contains("sdk")) {
                return false;
            }

            return true;
        }

        private final FlipGesture mFlipGesture;
        private final EmulatorConnector mEmulatorConnector;
    }

    private class ViewCrawlerHandler extends Handler {

        public ViewCrawlerHandler(Context context, String token, Looper looper, ViewVisitor.OnLayoutErrorListener layoutErrorListener) {
            super(looper);
            mContext = context;
            mToken = token;
            mSnapshot = null;

            String resourcePackage = mConfig.getResourcePackageName();
            if (null == resourcePackage) {
                resourcePackage = context.getPackageName();
            }

            final ResourceIds resourceIds = new ResourceReader.Ids(resourcePackage, context);

            mImageStore = new ImageStore(context);
            mProtocol = new EditProtocol(resourceIds, mImageStore, layoutErrorListener);
            mEditorChanges = new HashMap<String, Pair<String, JSONObject>>();
            mEditorTweaks = new ArrayList<JSONObject>();
            mEditorAssetUrls = new ArrayList<String>();
            mEditorEventBindings = new ArrayList<Pair<String, JSONObject>>();
            mPersistentChanges = new ArrayList<VariantChange>();
            mPersistentTweaks = new ArrayList<VariantTweak>();
            mPersistentEventBindings = new ArrayList<Pair<String, JSONObject>>();
            mSeenExperiments = new HashSet<Pair<Integer, Integer>>();
            mStartLock = new ReentrantLock();
            mStartLock.lock();
        }

        public void start() {
            mStartLock.unlock();
        }

        @Override
        public void handleMessage(Message msg) {
            mStartLock.lock();
            try {

                final int what = msg.what;
                switch (what) {
                    case MESSAGE_INITIALIZE_CHANGES:
                        loadKnownChanges();
                        initializeChanges();
                        break;
                    case MESSAGE_CONNECT_TO_EDITOR:
                        connectToEditor();
                        break;
                    case MESSAGE_SEND_DEVICE_INFO:
                        sendDeviceInfo();
                        break;
                    case MESSAGE_SEND_STATE_FOR_EDITING:
                        sendSnapshot((JSONObject) msg.obj);
                        break;
                    case MESSAGE_SEND_EVENT_TRACKED:
                        sendReportTrackToEditor((String) msg.obj);
                        break;
                    case MESSAGE_SEND_LAYOUT_ERROR:
                        sendLayoutError((ViewVisitor.LayoutErrorMessage) msg.obj);
                        break;
                    case MESSAGE_VARIANTS_RECEIVED:
                        handleVariantsReceived((JSONArray) msg.obj);
                        break;
                    case MESSAGE_HANDLE_EDITOR_CHANGES_RECEIVED:
                        handleEditorChangeReceived((JSONObject) msg.obj);
                        break;
                    case MESSAGE_EVENT_BINDINGS_RECEIVED:
                        handleEventBindingsReceived((JSONArray) msg.obj);
                        break;
                    case MESSAGE_HANDLE_EDITOR_BINDINGS_RECEIVED:
                        handleEditorBindingsReceived((JSONObject) msg.obj);
                        break;
                    case MESSAGE_HANDLE_EDITOR_CHANGES_CLEARED:
                        handleEditorBindingsCleared((JSONObject) msg.obj);
                        break;
                    case MESSAGE_HANDLE_EDITOR_TWEAKS_RECEIVED:
                        handleEditorTweaksReceived((JSONObject) msg.obj);
                        break;
                    case MESSAGE_HANDLE_EDITOR_CLOSED:
                        handleEditorClosed();
                        break;
                }
            } finally {
                mStartLock.unlock();
            }
        }

        /**
         * Load the experiment ids and variants already in persistent storage into
         * into our set of seen experiments, so we don't double track them.
         */
        private void loadKnownChanges() {
            final SharedPreferences preferences = getSharedPreferences();
            final String storedChanges = preferences.getString(SHARED_PREF_CHANGES_KEY, null);

            if (null != storedChanges) {
                try {
                    final JSONArray variants = new JSONArray(storedChanges);
                    final int variantsLength = variants.length();
                    for (int i = 0; i < variantsLength; i++) {
                        final JSONObject variant = variants.getJSONObject(i);
                        final int variantId = variant.getInt("id");
                        final int experimentId = variant.getInt("experiment_id");
                        final Pair<Integer,Integer> sight = new Pair<Integer,Integer>(experimentId, variantId);
                        mSeenExperiments.add(sight);
                    }
                } catch (JSONException e) {
                    Log.e(LOGTAG, "Malformed variants found in persistent storage, clearing all variants", e);
                    final SharedPreferences.Editor editor = preferences.edit();
                    editor.remove(SHARED_PREF_CHANGES_KEY);
                    editor.remove(SHARED_PREF_BINDINGS_KEY);
                    editor.apply();
                }
            }

        }

        /**
         * Load stored changes from persistent storage and apply them to the application.
         */
        private void initializeChanges() {
            final SharedPreferences preferences = getSharedPreferences();
            final String storedChanges = preferences.getString(SHARED_PREF_CHANGES_KEY, null);
            final String storedBindings = preferences.getString(SHARED_PREF_BINDINGS_KEY, null);
            try {
                if (null != storedChanges) {
                    mPersistentChanges.clear();
                    mPersistentTweaks.clear();

                    final JSONArray variants = new JSONArray(storedChanges);
                    final int variantsLength = variants.length();
                    for (int variantIx = 0; variantIx < variantsLength; variantIx++) {
                        final JSONObject nextVariant = variants.getJSONObject(variantIx);
                        final int variantIdPart = nextVariant.getInt("id");
                        final int experimentIdPart = nextVariant.getInt("experiment_id");
                        final Pair<Integer, Integer> variantId = new Pair<Integer, Integer>(experimentIdPart, variantIdPart);

                        final JSONArray actions = nextVariant.getJSONArray("actions");
                        for (int i = 0; i < actions.length(); i++) {
                            final JSONObject change = actions.getJSONObject(i);
                            final String targetActivity = JSONUtils.optionalStringKey(change, "target_activity");
                            final VariantChange variantChange = new VariantChange(targetActivity, change, variantId);
                            mPersistentChanges.add(variantChange);
                        }

                        final JSONArray tweaks = nextVariant.getJSONArray("tweaks");
                        final int length = tweaks.length();
                        for (int i = 0; i < length; i++) {
                            final JSONObject tweakDesc = tweaks.getJSONObject(i);
                            final VariantTweak variantTweak = new VariantTweak(tweakDesc, variantId);
                            mPersistentTweaks.add(variantTweak);
                        }
                    }
                }

                if (null != storedBindings) {
                    final JSONArray bindings = new JSONArray(storedBindings);

                    mPersistentEventBindings.clear();
                    for (int i = 0; i < bindings.length(); i++) {
                        final JSONObject event = bindings.getJSONObject(i);
                        final String targetActivity = JSONUtils.optionalStringKey(event, "target_activity");
                        mPersistentEventBindings.add(new Pair<String, JSONObject>(targetActivity, event));
                    }
                }
            } catch (final JSONException e) {
                Log.i(LOGTAG, "JSON error when initializing saved changes, clearing persistent memory", e);
                final SharedPreferences.Editor editor = preferences.edit();
                editor.remove(SHARED_PREF_CHANGES_KEY);
                editor.remove(SHARED_PREF_BINDINGS_KEY);
                editor.apply();
            }

            applyVariantsAndEventBindings();
        }

        /**
         * Try to connect to the remote interactive editor, if a connection does not already exist.
         */
        private void connectToEditor() {
            if (MPConfig.DEBUG) {
                Log.v(LOGTAG, "connecting to editor");
            }

            if (mEditorConnection != null && mEditorConnection.isValid()) {
                if (MPConfig.DEBUG) {
                    Log.v(LOGTAG, "There is already a valid connection to an events editor.");
                }
                return;
            }

            final SSLSocketFactory socketFactory = mConfig.getSSLSocketFactory();
            if (null == socketFactory) {
                if (MPConfig.DEBUG) {
                    Log.v(LOGTAG, "SSL is not available on this device, no connection will be attempted to the events editor.");
                }
                return;
            }

            final String url = MPConfig.getInstance(mContext).getEditorUrl() + mToken;
            try {
                final Socket sslSocket = socketFactory.createSocket();
                mEditorConnection = new EditorConnection(new URI(url), new Editor(), sslSocket);
            } catch (final URISyntaxException e) {
                Log.e(LOGTAG, "Error parsing URI " + url + " for editor websocket", e);
            } catch (final EditorConnection.EditorConnectionException e) {
                Log.e(LOGTAG, "Error connecting to URI " + url, e);
            } catch (final IOException e) {
                Log.i(LOGTAG, "Can't create SSL Socket to connect to editor service", e);
            }
        }

        /**
         * Send a string error message to the connected web UI.
         */
        private void sendError(String errorMessage) {
            if (mEditorConnection == null) {
                return;
            }

            final JSONObject errorObject = new JSONObject();
            try {
                errorObject.put("error_message", errorMessage);
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Apparently impossible JSONException", e);
            }

            final OutputStreamWriter writer = new OutputStreamWriter(mEditorConnection.getBufferedOutputStream());
            try {
                writer.write("{\"type\": \"error\", ");
                writer.write("\"payload\": ");
                writer.write(errorObject.toString());
                writer.write("}");
            } catch (final IOException e) {
                Log.e(LOGTAG, "Can't write error message to editor", e);
            } finally {
                try {
                    writer.close();
                } catch (final IOException e) {
                    Log.e(LOGTAG, "Could not close output writer to editor", e);
                }
            }
        }

        /**
         * Report on device info to the connected web UI.
         */
        private void sendDeviceInfo() {
            if (mEditorConnection == null) {
                return;
            }

            final OutputStream out = mEditorConnection.getBufferedOutputStream();
            final JsonWriter j = new JsonWriter(new OutputStreamWriter(out));

            try {
                j.beginObject();
                j.name("type").value("device_info_response");
                j.name("payload").beginObject();
                    j.name("device_type").value("Android");
                    j.name("device_name").value(Build.BRAND + "/" + Build.MODEL);
                    j.name("scaled_density").value(mScaledDensity);
                    for (final Map.Entry<String, String> entry : mDeviceInfo.entrySet()) {
                        j.name(entry.getKey()).value(entry.getValue());
                    }

                    final Map<String, Tweaks.TweakValue> tweakDescs = mTweaks.getAllValues();
                    j.name("tweaks").beginArray();
                    for (Map.Entry<String, Tweaks.TweakValue> tweak:tweakDescs.entrySet()) {
                        final Tweaks.TweakValue desc = tweak.getValue();
                        final String tweakName = tweak.getKey();
                        j.beginObject();
                        j.name("name").value(tweakName);
                        j.name("minimum").value((Number) null);
                        j.name("maximum").value((Number) null);
                        switch (desc.type) {
                            case Tweaks.BOOLEAN_TYPE:
                                j.name("type").value("boolean");
                                j.name("value").value(desc.getBooleanValue());
                                break;
                            case Tweaks.DOUBLE_TYPE:
                                j.name("type").value("number");
                                j.name("encoding").value("d");
                                j.name("value").value(desc.getNumberValue().doubleValue());
                                break;
                            case Tweaks.LONG_TYPE:
                                j.name("type").value("number");
                                j.name("encoding").value("l");
                                j.name("value").value(desc.getNumberValue().longValue());
                                break;
                            case Tweaks.STRING_TYPE:
                                j.name("type").value("string");
                                j.name("value").value(desc.getStringValue());
                                break;
                            default:
                                Log.wtf(LOGTAG, "Unrecognized Tweak Type " + desc.type + " encountered.");
                        }
                        j.endObject();
                    }
                    j.endArray();
                j.endObject(); // payload
                j.endObject();
            } catch (final IOException e) {
                Log.e(LOGTAG, "Can't write device_info to server", e);
            } finally {
                try {
                    j.close();
                } catch (final IOException e) {
                    Log.e(LOGTAG, "Can't close websocket writer", e);
                }
            }
        }

        /**
         * Send a snapshot response, with crawled views and screenshot image, to the connected web UI.
         */
        private void sendSnapshot(JSONObject message) {
            final long startSnapshot = System.currentTimeMillis();
            try {
                final JSONObject payload = message.getJSONObject("payload");
                if (payload.has("config")) {
                    mSnapshot = mProtocol.readSnapshotConfig(payload);
                    if (MPConfig.DEBUG) {
                        Log.v(LOGTAG, "Initializing snapshot with configuration");
                    }
                }
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Payload with snapshot config required with snapshot request", e);
                sendError("Payload with snapshot config required with snapshot request");
                return;
            } catch (final EditProtocol.BadInstructionsException e) {
                Log.e(LOGTAG, "Editor sent malformed message with snapshot request", e);
                sendError(e.getMessage());
                return;
            }

            if (null == mSnapshot) {
                sendError("No snapshot configuration (or a malformed snapshot configuration) was sent.");
                Log.w(LOGTAG, "Mixpanel editor is misconfigured, sent a snapshot request without a valid configuration.");
                return;
            }
            // ELSE config is valid:

            final OutputStream out = mEditorConnection.getBufferedOutputStream();
            final OutputStreamWriter writer = new OutputStreamWriter(out);

            try {
                writer.write("{");
                writer.write("\"type\": \"snapshot_response\",");
                writer.write("\"payload\": {");
                {
                    writer.write("\"activities\":");
                    writer.flush();
                    mSnapshot.snapshots(mEditState, out);
                }

                final long snapshotTime = System.currentTimeMillis() - startSnapshot;
                writer.write(",\"snapshot_time_millis\": ");
                writer.write(Long.toString(snapshotTime));

                writer.write("}"); // } payload
                writer.write("}"); // } whole message
            } catch (final IOException e) {
                Log.e(LOGTAG, "Can't write snapshot request to server", e);
            } finally {
                try {
                    writer.close();
                } catch (final IOException e) {
                    Log.e(LOGTAG, "Can't close writer.", e);
                }
            }
        }

        /**
         * Report that a track has occurred to the connected web UI.
         */
        private void sendReportTrackToEditor(String eventName) {
            if (mEditorConnection == null) {
                return;
            }

            final OutputStream out = mEditorConnection.getBufferedOutputStream();
            final OutputStreamWriter writer = new OutputStreamWriter(out);
            final JsonWriter j = new JsonWriter(writer);

            try {
                j.beginObject();
                j.name("type").value("track_message");
                j.name("payload");
                {
                    j.beginObject();
                    j.name("event_name").value(eventName);
                    j.endObject();
                }
                j.endObject();
                j.flush();
            } catch (final IOException e) {
                Log.e(LOGTAG, "Can't write track_message to server", e);
            } finally {
                try {
                    j.close();
                } catch (final IOException e) {
                    Log.e(LOGTAG, "Can't close writer.", e);
                }
            }
        }

        private void sendLayoutError(ViewVisitor.LayoutErrorMessage exception) {
            if (mEditorConnection == null ) {
                return;
            }

            final OutputStream out = mEditorConnection.getBufferedOutputStream();
            final OutputStreamWriter writer = new OutputStreamWriter(out);
            final JsonWriter j = new JsonWriter(writer);

            try {
                j.beginObject();
                j.name("type").value("layout_error");
                j.name("exception_type").value(exception.getErrorType());
                j.name("cid").value(exception.getName());
                j.endObject();
            } catch (final IOException e) {
                Log.e(LOGTAG, "Can't write track_message to server", e);
            } finally {
                try {
                    j.close();
                } catch (final IOException e) {
                    Log.e(LOGTAG, "Can't close writer.", e);
                }
            }
        }

        /**
         * Accept and apply a change from the connected UI.
         */
        private void handleEditorChangeReceived(JSONObject changeMessage) {
            try {
                final JSONObject payload = changeMessage.getJSONObject("payload");
                final JSONArray actions = payload.getJSONArray("actions");

                for (int i = 0; i < actions.length(); i++) {
                    final JSONObject change = actions.getJSONObject(i);
                    final String targetActivity = JSONUtils.optionalStringKey(change, "target_activity");
                    final String name = change.getString("name");
                    mEditorChanges.put(name, new Pair<String, JSONObject>(targetActivity, change));
                }

                applyVariantsAndEventBindings();
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Bad change request received", e);
            }
        }

        /**
         * Remove a change from the connected UI.
         */
        private void handleEditorBindingsCleared(JSONObject clearMessage) {
            try {
                final JSONObject payload = clearMessage.getJSONObject("payload");
                final JSONArray actions = payload.getJSONArray("actions");

                // Don't throw any JSONExceptions after this, or you'll leak the item
                for (int i = 0; i < actions.length(); i++) {
                    final String changeId = actions.getString(i);
                    mEditorChanges.remove(changeId);
                }
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Bad clear request received", e);
            }

            applyVariantsAndEventBindings();
        }

        private void handleEditorTweaksReceived(JSONObject tweaksMessage) {
            try {
                mEditorTweaks.clear();
                final JSONObject payload = tweaksMessage.getJSONObject("payload");
                final JSONArray tweaks = payload.getJSONArray("tweaks");
                final int length = tweaks.length();
                for (int i = 0; i < length; i++) {
                    final JSONObject tweakDesc = tweaks.getJSONObject(i);
                    mEditorTweaks.add(tweakDesc);
                }
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Bad tweaks received", e);
            }

            applyVariantsAndEventBindings();
        }

        /**
         * Accept and apply variant changes from a non-interactive source.
         */
        private void handleVariantsReceived(JSONArray variants) {
            final SharedPreferences preferences = getSharedPreferences();
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putString(SHARED_PREF_CHANGES_KEY, variants.toString());
            editor.apply();

            initializeChanges();
        }

        /**
         * Accept and apply a persistent event binding from a non-interactive source.
         */
        private void handleEventBindingsReceived(JSONArray eventBindings) {
            final SharedPreferences preferences = getSharedPreferences();
            final SharedPreferences.Editor editor = preferences.edit();
            editor.putString(SHARED_PREF_BINDINGS_KEY, eventBindings.toString());
            editor.apply();
            initializeChanges();
        }

        /**
         * Accept and apply a temporary event binding from the connected UI.
         */
        private void handleEditorBindingsReceived(JSONObject message) {
            final JSONArray eventBindings;
            try {
                final JSONObject payload = message.getJSONObject("payload");
                eventBindings = payload.getJSONArray("events");
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Bad event bindings received", e);
                return;
            }

            final int eventCount = eventBindings.length();

            mEditorEventBindings.clear();
            for (int i = 0; i < eventCount; i++) {
                try {
                    final JSONObject event = eventBindings.getJSONObject(i);
                    final String targetActivity = JSONUtils.optionalStringKey(event, "target_activity");
                    mEditorEventBindings.add(new Pair<String, JSONObject>(targetActivity, event));
                } catch (final JSONException e) {
                    Log.e(LOGTAG, "Bad event binding received from editor in " + eventBindings.toString(), e);
                }
            }

            applyVariantsAndEventBindings();
        }

        /**
         * Clear state associated with the editor now that the editor is gone.
         */
        private void handleEditorClosed() {
            mEditorChanges.clear();
            mEditorEventBindings.clear();

            // Free (or make available) snapshot memory
            mSnapshot = null;

            if (MPConfig.DEBUG) {
                Log.v(LOGTAG, "Editor closed- freeing snapshot");
            }

            applyVariantsAndEventBindings();
            for (final String assetUrl:mEditorAssetUrls) {
                mImageStore.deleteStorage(assetUrl);
            }
        }

        /**
         * Reads our JSON-stored edits from memory and submits them to our EditState. Overwrites
         * any existing edits at the time that it is run.
         *
         * applyVariantsAndEventBindings should be called any time we load new edits, event bindings,
         * or tweaks from disk or when we receive new edits from the interactive UI editor.
         * Changes and event bindings from our persistent storage and temporary changes
         * received from interactive editing will all be submitted to our EditState, tweaks
         * will be updated, and experiment statuses will be tracked.
         */
        private void applyVariantsAndEventBindings() {
            final List<Pair<String, ViewVisitor>> newVisitors = new ArrayList<Pair<String, ViewVisitor>>();
            final Set<Pair<Integer, Integer>> toTrack = new HashSet<Pair<Integer, Integer>>();

            {
                final int size = mPersistentChanges.size();
                for (int i = 0; i < size; i++) {
                    final VariantChange changeInfo = mPersistentChanges.get(i);
                    try {
                        final EditProtocol.Edit edit = mProtocol.readEdit(changeInfo.change);
                        newVisitors.add(new Pair<String, ViewVisitor>(changeInfo.activityName, edit.visitor));
                        if (!mSeenExperiments.contains(changeInfo.variantId)) {
                            toTrack.add(changeInfo.variantId);
                        }
                    } catch (final EditProtocol.CantGetEditAssetsException e) {
                        Log.v(LOGTAG, "Can't load assets for an edit, won't apply the change now", e);
                    } catch (final EditProtocol.InapplicableInstructionsException e) {
                        Log.i(LOGTAG, e.getMessage());
                    } catch (final EditProtocol.BadInstructionsException e) {
                        Log.e(LOGTAG, "Bad persistent change request cannot be applied.", e);
                    }
                }
            }

            {
                final int size = mPersistentTweaks.size();
                for (int i = 0; i < size; i++) {
                    final VariantTweak tweakInfo = mPersistentTweaks.get(i);
                    try {
                        final Pair<String, Object> tweakValue = mProtocol.readTweak(tweakInfo.tweak);
                        mTweaks.set(tweakValue.first, tweakValue.second);
                        if (!mSeenExperiments.contains(tweakInfo.variantId)) {
                            toTrack.add(tweakInfo.variantId);
                        }
                    } catch (EditProtocol.BadInstructionsException e) {
                        Log.e(LOGTAG, "Bad editor tweak cannot be applied.", e);
                    }
                }
            }

            {
                for (Pair<String, JSONObject> changeInfo:mEditorChanges.values()) {
                    try {
                        final EditProtocol.Edit edit = mProtocol.readEdit(changeInfo.second);
                        newVisitors.add(new Pair<String, ViewVisitor>(changeInfo.first, edit.visitor));
                        mEditorAssetUrls.addAll(edit.imageUrls);
                    } catch (final EditProtocol.CantGetEditAssetsException e) {
                        Log.v(LOGTAG, "Can't load assets for an edit, won't apply the change now", e);
                    } catch (final EditProtocol.InapplicableInstructionsException e) {
                        Log.i(LOGTAG, e.getMessage());
                    } catch (final EditProtocol.BadInstructionsException e) {
                        Log.e(LOGTAG, "Bad editor change request cannot be applied.", e);
                    }
                }
            }

            {
                final int size = mEditorTweaks.size();
                for (int i = 0; i < size; i++) {
                    final JSONObject tweakDesc = mEditorTweaks.get(i);

                    try {
                        final Pair<String, Object> tweakValue = mProtocol.readTweak(tweakDesc);
                        mTweaks.set(tweakValue.first, tweakValue.second);
                    } catch (final EditProtocol.BadInstructionsException e) {
                        Log.e(LOGTAG, "Strange tweaks received", e);
                    }
                }
            }

            {
                final int size = mPersistentEventBindings.size();
                for (int i = 0; i < size; i++) {
                    final Pair<String, JSONObject> changeInfo = mPersistentEventBindings.get(i);
                    try {
                        final ViewVisitor visitor = mProtocol.readEventBinding(changeInfo.second, mDynamicEventTracker);
                        newVisitors.add(new Pair<String, ViewVisitor>(changeInfo.first, visitor));
                    } catch (final EditProtocol.InapplicableInstructionsException e) {
                        Log.i(LOGTAG, e.getMessage());
                    } catch (final EditProtocol.BadInstructionsException e) {
                        Log.e(LOGTAG, "Bad persistent event binding cannot be applied.", e);
                    }
                }
            }

            {
                final int size = mEditorEventBindings.size();
                for (int i = 0; i < size; i++) {
                    final Pair<String, JSONObject> changeInfo = mEditorEventBindings.get(i);
                    try {
                        final ViewVisitor visitor = mProtocol.readEventBinding(changeInfo.second, mDynamicEventTracker);
                        newVisitors.add(new Pair<String, ViewVisitor>(changeInfo.first, visitor));
                    } catch (final EditProtocol.InapplicableInstructionsException e) {
                        Log.i(LOGTAG, e.getMessage());
                    } catch (final EditProtocol.BadInstructionsException e) {
                        Log.e(LOGTAG, "Bad editor event binding cannot be applied.", e);
                    }
                }
            }

            final Map<String, List<ViewVisitor>> editMap = new HashMap<String, List<ViewVisitor>>();
            final int totalEdits = newVisitors.size();
            for (int i = 0; i < totalEdits; i++) {
                final Pair<String, ViewVisitor> next = newVisitors.get(i);
                final List<ViewVisitor> mapElement;
                if (editMap.containsKey(next.first)) {
                    mapElement = editMap.get(next.first);
                } else {
                    mapElement = new ArrayList<ViewVisitor>();
                    editMap.put(next.first, mapElement);
                }
                mapElement.add(next.second);
            }

            mEditState.setEdits(editMap);
            mSeenExperiments.addAll(toTrack);

            if (toTrack.size() > 0) {
                final JSONObject variantObject = new JSONObject();

                try {
                    for (Pair<Integer, Integer> variant : toTrack) {
                        final int experimentId = variant.first;
                        final int variantId = variant.second;

                        final JSONObject trackProps = new JSONObject();
                        trackProps.put("$experiment_id", experimentId);
                        trackProps.put("$variant_id", variantId);
                        mMixpanel.track("$experiment_started", trackProps);

                        variantObject.put(Integer.toString(experimentId), variantId);
                    }
                } catch (JSONException e) {
                    Log.wtf(LOGTAG, "Could not build JSON for reporting experiment start", e);
                }

                mMixpanel.getPeople().merge("$experiments", variantObject);
                mMixpanel.updateSuperProperties(new SuperPropertyUpdate() {
                    public JSONObject update(JSONObject in) {
                        try {
                            in.put("$experiments", variantObject);
                        } catch (JSONException e) {
                            Log.wtf(LOGTAG, "Can't write $experiments super property", e);
                        }
                        return in;
                    }
                });
            }
        }

        private SharedPreferences getSharedPreferences() {
            final String sharedPrefsName = SHARED_PREF_EDITS_FILE + mToken;
            return mContext.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);
        }

        private EditorConnection mEditorConnection;
        private ViewSnapshot mSnapshot;
        private final Context mContext;
        private final String mToken;
        private final Lock mStartLock;
        private final EditProtocol mProtocol;
        private final ImageStore mImageStore;

        private final Map<String, Pair<String,JSONObject>> mEditorChanges;
        private final List<JSONObject> mEditorTweaks;
        private final List<String> mEditorAssetUrls;
        private final List<Pair<String,JSONObject>> mEditorEventBindings;
        private final List<VariantChange> mPersistentChanges;
        private final List<VariantTweak> mPersistentTweaks;
        private final List<Pair<String,JSONObject>> mPersistentEventBindings;
        private final Set<Pair<Integer, Integer>> mSeenExperiments;
    }

    private class Editor implements EditorConnection.Editor {

        @Override
        public void sendSnapshot(JSONObject message) {
            final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_SEND_STATE_FOR_EDITING);
            msg.obj = message;
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void performEdit(JSONObject message) {
            final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_HANDLE_EDITOR_CHANGES_RECEIVED);
            msg.obj = message;
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void clearEdits(JSONObject message) {
            final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_HANDLE_EDITOR_CHANGES_CLEARED);
            msg.obj = message;
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void setTweaks(JSONObject message) {
            final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_HANDLE_EDITOR_TWEAKS_RECEIVED);
            msg.obj = message;
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void bindEvents(JSONObject message) {
            final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_HANDLE_EDITOR_BINDINGS_RECEIVED);
            msg.obj = message;
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void sendDeviceInfo() {
            final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_SEND_DEVICE_INFO);
            mMessageThreadHandler.sendMessage(msg);
        }

        @Override
        public void cleanup() {
            final Message msg = mMessageThreadHandler.obtainMessage(ViewCrawler.MESSAGE_HANDLE_EDITOR_CLOSED);
            mMessageThreadHandler.sendMessage(msg);
        }
    }

    private static class VariantChange {
        public VariantChange(String anActivityName, JSONObject someChange, Pair<Integer, Integer> aVariantId) {
            activityName = anActivityName;
            change = someChange;
            variantId = aVariantId;
        }

        public final String activityName;
        public final JSONObject change;
        public final Pair<Integer, Integer> variantId;
    }

    private static class VariantTweak {
        public VariantTweak(JSONObject aTweak, Pair<Integer, Integer> aVariantId) {
            tweak = aTweak;
            variantId = aVariantId;
        }

        public final JSONObject tweak;
        public final Pair<Integer, Integer> variantId;
    }

    private final MPConfig mConfig;
    private final MixpanelAPI mMixpanel;
    private final DynamicEventTracker mDynamicEventTracker;
    private final EditState mEditState;
    private final Tweaks mTweaks;
    private final Map<String, String> mDeviceInfo;
    private final ViewCrawlerHandler mMessageThreadHandler;
    private final float mScaledDensity;

    private static final String SHARED_PREF_EDITS_FILE = "mixpanel.viewcrawler.changes";
    private static final String SHARED_PREF_CHANGES_KEY = "mixpanel.viewcrawler.changes";
    private static final String SHARED_PREF_BINDINGS_KEY = "mixpanel.viewcrawler.bindings";

    private static final int MESSAGE_INITIALIZE_CHANGES = 0;
    private static final int MESSAGE_CONNECT_TO_EDITOR = 1;
    private static final int MESSAGE_SEND_STATE_FOR_EDITING = 2;
    private static final int MESSAGE_HANDLE_EDITOR_CHANGES_RECEIVED = 3;
    private static final int MESSAGE_SEND_DEVICE_INFO = 4;
    private static final int MESSAGE_EVENT_BINDINGS_RECEIVED = 5;
    private static final int MESSAGE_HANDLE_EDITOR_BINDINGS_RECEIVED = 6;
    private static final int MESSAGE_SEND_EVENT_TRACKED = 7;
    private static final int MESSAGE_HANDLE_EDITOR_CLOSED = 8;
    private static final int MESSAGE_VARIANTS_RECEIVED = 9;
    private static final int MESSAGE_HANDLE_EDITOR_CHANGES_CLEARED = 10;
    private static final int MESSAGE_HANDLE_EDITOR_TWEAKS_RECEIVED = 11;
    private static final int MESSAGE_SEND_LAYOUT_ERROR = 12;

    private static final int EMULATOR_CONNECT_ATTEMPT_INTERVAL_MILLIS = 1000 * 30;

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.ViewCrawler";
}
