package com.mixpanel.example.hello;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;


/**
 * This class serves as an example of how session tracking may be done on Android. The length of a session
 * is defined as the time between a call to startSession() and a call to endSession() after which there is
 * not another call to startSession() for at least 15 seconds. If a session has been started and
 * another startSession() function is called, it is a no op.
 *
 * This class is not officially supported by Mixpanel, and you may need to modify it for your own application.
 *
 * Example Usage:
 *
 * <pre>
 * {@code
 *  public class MainActivity extends ActionBarActivity {
 *      @Override
 *      protected void onCreate(Bundle savedInstanceState) {
 *          super.onCreate(savedInstanceState);
 *          setContentView(R.layout.activity_main);
 *
 *          this._sessionManager = SessionManager.getInstance(this, new SessionManager.SessionCompleteCallback() {
 *              @Override
 *              public void onSessionComplete(SessionManager.Session session) {
 *                  // You may send the session time to Mixpanel in here.
 *                  Log.d("MY APP", "session " + session.getUuid() + " lasted for " +
 *                                  session.getSessionLength()/1000 + " seconds and is now closed");
 *              }
 *          });
 *          this._sessionManager.startSession();
 *      }
 *
 *      @Override
 *      public void onResume()
 *      {
 *          super.onResume();
 *          this._sessionManager.startSession();
 *      }
 *
 *      @Override
 *      public void onPause()
 *      {
 *          super.onPause();
 *          this._sessionManager.endSession();
 *      }
 *
 *      private SessionManager _sessionManager;
 *  }
 * }
 * </pre>
 *
 */
public class SessionManager {
    /**
     * Instantiate a new SessionManager object
     * @param context
     * @param callback
     */
    private SessionManager(Context context, SessionCompleteCallback callback) {
        this._appContext = context.getApplicationContext();
        this._sessionCompleteCallback = callback; // this will be called any time a session is complete
        HandlerThread handlerThread = new HandlerThread(getClass().getCanonicalName());
        handlerThread.start();
        this._sessionHandler = new SessionHandler(this, handlerThread.getLooper());
        this._sessionHandler.sendEmptyMessage(MESSAGE_INIT);
    }

    /**
     * Get the SessionManager singleton object, create on if one doesn't exist
     * @param context
     * @param callback
     * @return
     */
    public static SessionManager getInstance(Context context, SessionCompleteCallback callback) {
        if (_instance == null) {
            _instance = new SessionManager(context, callback);
        }
        return _instance;
    }

    /**
     * Dispatch request to handler thread to start a session
     */
    public void startSession() {
        _sessionHandler.sendEmptyMessage(MESSAGE_START_SESSION);
    }


    /**
     * Dispatch request to handler thread to end a session
     */
    public void endSession() {
        _sessionHandler.sendEmptyMessage(MESSAGE_END_SESSION);
    }

    /**
     * Called by the handler thread, this will resume the previous session if it ended within
     * the given threshold otherwise it'll create a new session. If a session already exists,
     * it will be a noop.
     */
    private void _startSession() {
        if (_curSession == null) {
            if (_prevSession != null && !_prevSession.isExpired()) {
                Log.d(LOGTAG, "resuming session " + _prevSession.getUuid());
                _curSession = _prevSession;
                _curSession.resume();
                _prevSession = null;
            } else {
                _curSession = new Session();
                Log.d(LOGTAG, "creating new session " + _curSession.getUuid());
                synchronized (_sessionsLock) {
                    _sessions.add(_curSession);
                    _writeSessionsToFile();
                    this._initSessionCompleter();
                }
            }
        }
    }

    /**
     * Takes the current session, sets the end time, and sets it as the previous session.
     */
    private void _endSession() {
        if (_curSession != null) {
            _curSession.end();
            _prevSession = _curSession;
            _curSession = null;
        }
    }

    /**
     * Spawns a thread to monitor for sessions that need to be completed (ended sessions that are
     * guaranteed not to be resumed). If one is already running, this will be a noop.
     */
    private void _initSessionCompleter() {
        if (_sessionCompleterThread == null || !_sessionCompleterThread.isAlive()) {
            _sessionCompleterThread = new Thread() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            _completeExpiredSessions();
                            sleep(1000);
                        }
                    } catch (InterruptedException e) {
                        Log.e(LOGTAG, "expiration watcher thread interrupted", e);
                    }
                }

                private void _completeExpiredSessions() {
                    Log.d(LOGTAG, "checking for expired sessions...");
                    synchronized(_sessionsLock) {
                        Iterator<Session> iterator = _sessions.iterator();
                        while (iterator.hasNext()) {
                            Session session = iterator.next();
                            if (session.isExpired()) {
                                Log.d(LOGTAG, "expiring session id " + session.getUuid());
                                iterator.remove();
                                _writeSessionsToFile();
                                _sessionCompleteCallback.onSessionComplete(session);
                            } else {
                                Log.d(LOGTAG, "session id " + session.getUuid() + " not yet expired...");
                            }
                        }

                    }
                }
            };
            _sessionCompleterThread.start();
        }
    }

    /**
     * Loads any previously non-completed sessions from local disk. This is necessary to guarantee
     * that sessions are eventually completed when an app is hard-killed or crashes
     */
    private void _loadSessionsFromFile() {
        FileInputStream fis = null;
        try {
            fis = _appContext.openFileInput(SESSIONS_FILE_NAME);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader bufferedReader = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }
            JSONArray sessionsJson = new JSONArray(sb.toString());

            synchronized(_sessionsLock) {
                for (int i = 0; i < sessionsJson.length(); i++) {
                    JSONObject sessionsObj = sessionsJson.getJSONObject(i);
                    Session session = new Session(sessionsObj);

                    // if there are sessions that don't have an end time we must assume that the
                    // app was killed mid session so we'll just send now as the end time. The better
                    // solution would be to periodically mark a "lastAccessTime" that can be used
                    // in such a case.
                    if (session.getEndTime() == null) {
                        session.end();
                    }

                    _sessions.add(session);
                }
                if (_sessions.size() > 0) {
                    this._initSessionCompleter();
                }
            }
        } catch (FileNotFoundException e) {
            Log.e(LOGTAG, "Could not find sessions file", e);
        } catch (IOException e) {
            Log.e(LOGTAG, "Could not read from sessions file", e);
        } catch (JSONException e) {
            Log.e(LOGTAG, "Could not serialize json string from file", e);
        }
    }

    /**
     * Writes the current sessions list to local disk. This is so we have a persistent snapshot
     * of non-completed sessions that can be reloaded in case of app shutdown / crash.
     */
    private void _writeSessionsToFile() {
        FileOutputStream fos = null;
        try {
            fos = _appContext.openFileOutput(SESSIONS_FILE_NAME, Context.MODE_PRIVATE);
            JSONArray jsonArray = new JSONArray();
            for (Session session : _sessions) {
                jsonArray.put(session.toJSON());
            }
            fos.write(jsonArray.toString().getBytes());
            fos.close();
        } catch (FileNotFoundException e) {
            Log.e(LOGTAG, "Could not find sessions file", e);
        } catch (IOException e) {
            Log.e(LOGTAG, "Could not write to sessions file", e);
        } catch (JSONException e) {
            Log.e(LOGTAG, "Could not turn session to JSON", e);
        }
    }

    public class Session {
        private String uuid = null;
        private Long startTime = null;
        private Long endTime = null;
        private Long sessionExpirationGracePeriod = 15000L;

        public Session() {
            this.uuid = UUID.randomUUID().toString();
            this.startTime = System.currentTimeMillis();
        }

        public Session(JSONObject jsonObject) throws JSONException {
            this.uuid = jsonObject.getString("uuid");
            this.startTime = jsonObject.getLong("startTime");
            if (jsonObject.has("endTime")) {
                this.endTime = jsonObject.getLong("endTime");
            }
            this.sessionExpirationGracePeriod = jsonObject.getLong("sessionExpirationGracePeriod");
        }

        public JSONObject toJSON() throws JSONException {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("uuid", this.uuid);
            jsonObject.put("startTime", this.startTime);
            jsonObject.put("endTime", this.endTime);
            jsonObject.put("sessionExpirationGracePeriod", this.sessionExpirationGracePeriod);
            return jsonObject;
        }

        public void resume() {
            this.endTime = null;
        }

        public void end() {
            this.endTime = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return this.endTime != null && System.currentTimeMillis() > this.endTime + this.sessionExpirationGracePeriod;
        }

        public Long getSessionLength() {
            if (this.endTime != null) {
                return this.endTime - this.startTime;
            } else {
                return System.currentTimeMillis() - this.startTime;
            }
        }

        public String getUuid() {
            return uuid;
        }

        public Long getStartTime() {
            return startTime;
        }

        public Long getEndTime() {
            return endTime;
        }

        public Long getSessionExpirationGracePeriod() {
            return sessionExpirationGracePeriod;
        }
    }

    public interface SessionCompleteCallback {
        public void onSessionComplete(Session session);
    }

    /**
     * Handler thread responsible for all session interaction
     */
    public class SessionHandler extends Handler {
        private SessionManager sessionManager;

        public SessionHandler(SessionManager sessionManager, Looper looper) {
            super(looper);
            this.sessionManager = sessionManager;
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MESSAGE_INIT:
                    sessionManager._loadSessionsFromFile();
                    break;
                case MESSAGE_START_SESSION:
                    sessionManager._startSession();
                    break;
                case MESSAGE_END_SESSION:
                    sessionManager._endSession();
                    break;
            }
        }
    }

    private static String LOGTAG = "SessionManager";
    private static String SESSIONS_FILE_NAME = "user_sessions";
    private static final int MESSAGE_INIT = 0;
    private static final int MESSAGE_START_SESSION = 1;
    private static final int MESSAGE_END_SESSION = 2;

    private static SessionManager _instance = null;
    private static final Object[] _sessionsLock = new Object[0];
    private List<Session> _sessions = new ArrayList<Session>();
    private Session _curSession = null;
    private Session _prevSession = null;
    private SessionHandler _sessionHandler;
    private Context _appContext = null;
    private Thread _sessionCompleterThread = null;
    private final SessionCompleteCallback _sessionCompleteCallback;
}
