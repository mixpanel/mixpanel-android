package com.mixpanel.android.util;

/**
 * Mixpanel Constants
 */

public class MPConstants {
    public static class SessionReplay {
        public static final String REGISTER_ACTION = "com.mixpanel.properties.register";
        public static final String UNREGISTER_ACTION = "com.mixpanel.properties.unregister";
        public static final String REPLAY_ID_KEY = "$mp_replay_id";
    }
    public static class URL {
        public static final String DEFAULT_SERVER_HOST = "api.mixpanel.com";
        public static final String MIXPANEL_API = "https://api.mixpanel.com";
        public static final String EVENT = "/track/";
        public static final String PEOPLE = "/engage/";
        public static final String GROUPS = "/groups/";
        public static final String FLAGS = "/flags/";
    }
    public static class Flags {
        public static final String FLAGS_KEY = "flags";
        public static final String VARIANT_KEY = "variant_key";
        public static final String VARIANT_VALUE = "variant_value";
        public static final String EXPERIMENT_ID = "experiment_id";
        public static final String IS_EXPERIMENT_ACTIVE = "is_experiment_active";
        public static final String IS_QA_TESTER = "is_qa_tester";

        // First-time event targeting
        public static final String PENDING_FIRST_TIME_EVENTS = "pending_first_time_events";
        public static final String FLAG_KEY = "flag_key";
        public static final String FLAG_ID = "flag_id";
        public static final String PROJECT_ID = "project_id";
        public static final String FIRST_TIME_EVENT_HASH = "first_time_event_hash";
        public static final String EVENT_NAME = "event_name";
        public static final String PROPERTY_FILTERS = "property_filters";
        public static final String PENDING_VARIANT = "pending_variant";
    }
}
