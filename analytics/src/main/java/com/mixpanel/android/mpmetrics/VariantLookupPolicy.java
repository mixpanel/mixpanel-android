package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;

import com.mixpanel.android.util.MPLog;

/**
 * Configures how the SDK resolves feature flag variants relative to the on-disk persistence
 * layer and the network.
 *
 * <p>Three strategies are supported:
 * <ul>
 *   <li>{@link NetworkOnly} - never read or write persisted variants. Variant lookups always
 *       wait for the network call. This is the default and matches behavior prior to the
 *       introduction of variant persistence. Construction also wipes any stale on-disk blob
 *       left over from a prior persisting configuration.</li>
 *   <li>{@link PersistenceUntilNetworkSuccess} - serve persisted variants immediately when available, and
 *       only block on the network if no persisted variants exist for the current user. The
 *       network call still runs in the background to refresh. Persisted entries older than
 *       {@code ttlMillis} are not served.</li>
 *   <li>{@link NetworkFirst} - prefer fresh values from the network, but fall back to persisted
 *       variants when the network call fails. Persisted entries older than {@code ttlMillis}
 *       are not served.</li>
 * </ul>
 *
 * <p>Construct instances via the static factories rather than {@code new}:
 * <pre>{@code
 * VariantLookupPolicy.networkOnly()
 * VariantLookupPolicy.persistenceUntilNetworkSuccess(TimeUnit.HOURS.toMillis(24))
 * VariantLookupPolicy.networkFirst(TimeUnit.HOURS.toMillis(24))
 * }</pre>
 *
 * <p>This class is intentionally Java 8 compatible (no {@code sealed} keyword) even though the
 * module compiles at source level 17. The package-private constructor prevents external
 * subclassing while leaving the type open for {@code instanceof} dispatch at consumer sites.
 *
 * @see FeatureFlagOptions.Builder#variantLookupPolicy(VariantLookupPolicy)
 */
public abstract class VariantLookupPolicy {

    private static final String LOGTAG = "MixpanelAPI.VariantLookupPolicy";

    /**
     * Default persistence TTL applied by {@link #persistenceUntilNetworkSuccess()} and {@link #networkFirst()}
     * when no explicit TTL is provided. 24 hours.
     */
    public static final long DEFAULT_PERSISTENCE_TTL_MILLIS = 24L * 60 * 60 * 1000;

    VariantLookupPolicy() {}

    /**
     * Resolves the policy the SDK should actually use given what the developer configured.
     * Substitutes {@link #networkOnly()} when the requested policy is a persisting one with a
     * non-positive TTL, since "persist on every fetch but expire immediately (or never accept
     * the entry)" does no useful work — the developer almost certainly meant "no persistence."
     * Logs a warning so the misconfiguration is visible without crashing the host app.
     *
     * <p>Called once at SDK init; downstream code can treat the returned policy as canonical.
     */
    @NonNull
    static VariantLookupPolicy effective(@NonNull VariantLookupPolicy requested) {
        final long ttl;
        if (requested instanceof PersistenceUntilNetworkSuccess) {
            ttl = ((PersistenceUntilNetworkSuccess) requested).ttlMillis;
        } else if (requested instanceof NetworkFirst) {
            ttl = ((NetworkFirst) requested).ttlMillis;
        } else {
            return requested;
        }
        if (ttl <= 0) {
            MPLog.w(LOGTAG, "Non-positive TTL (" + ttl + "ms) on "
                    + requested.getClass().getSimpleName()
                    + "; falling back to networkOnly since persistence with no meaningful TTL "
                    + "does no useful work.");
            return networkOnly();
        }
        return requested;
    }

    /**
     * Returns the singleton {@link NetworkOnly} strategy. Identity comparison is meaningful —
     * every call returns the same instance.
     */
    @NonNull
    public static NetworkOnly networkOnly() {
        return NetworkOnly.INSTANCE;
    }

    /**
     * Returns a {@link PersistenceUntilNetworkSuccess} strategy with the {@link #DEFAULT_PERSISTENCE_TTL_MILLIS}.
     */
    @NonNull
    public static PersistenceUntilNetworkSuccess persistenceUntilNetworkSuccess() {
        return persistenceUntilNetworkSuccess(DEFAULT_PERSISTENCE_TTL_MILLIS);
    }

    /**
     * Returns a {@link PersistenceUntilNetworkSuccess} strategy with the given persistence TTL.
     *
     * @param ttlMillis maximum age, in milliseconds, of a persisted variant set before it is
     *                  not served. Non-positive values are treated as a misconfiguration —
     *                  the SDK substitutes {@link #networkOnly()} at init (with a warning
     *                  logged), since persistence with no meaningful TTL does no useful work.
     */
    @NonNull
    public static PersistenceUntilNetworkSuccess persistenceUntilNetworkSuccess(long ttlMillis) {
        return new PersistenceUntilNetworkSuccess(ttlMillis);
    }

    /**
     * Returns a {@link NetworkFirst} strategy with the {@link #DEFAULT_PERSISTENCE_TTL_MILLIS}.
     */
    @NonNull
    public static NetworkFirst networkFirst() {
        return networkFirst(DEFAULT_PERSISTENCE_TTL_MILLIS);
    }

    /**
     * Returns a {@link NetworkFirst} strategy with the given persistence TTL.
     *
     * @param ttlMillis maximum age, in milliseconds, of a persisted variant set before it is
     *                  not served. Non-positive values are treated as a misconfiguration —
     *                  the SDK substitutes {@link #networkOnly()} at init (with a warning
     *                  logged), since persistence with no meaningful TTL does no useful work.
     */
    @NonNull
    public static NetworkFirst networkFirst(long ttlMillis) {
        return new NetworkFirst(ttlMillis);
    }

    /**
     * Strategy that never consults persisted variants. Variant lookups always await the
     * network call.
     */
    public static final class NetworkOnly extends VariantLookupPolicy {
        // Held inside the subclass so the outer class's <clinit> does not reference it,
        // sidestepping the "subclass referenced from superclass initializer" deadlock pattern.
        static final NetworkOnly INSTANCE = new NetworkOnly();

        NetworkOnly() {}
    }

    /**
     * Strategy that serves persisted variants immediately when available and only waits on
     * the network when no persisted entry exists.
     */
    public static final class PersistenceUntilNetworkSuccess extends VariantLookupPolicy {
        public final long ttlMillis;

        PersistenceUntilNetworkSuccess(long ttlMillis) {
            this.ttlMillis = ttlMillis;
        }
    }

    /**
     * Strategy that prefers a fresh network response but falls back to persisted variants
     * when the network call fails.
     */
    public static final class NetworkFirst extends VariantLookupPolicy {
        public final long ttlMillis;

        NetworkFirst(long ttlMillis) {
            this.ttlMillis = ttlMillis;
        }
    }
}
