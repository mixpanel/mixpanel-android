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
     * Negative TTL values are not meaningful (a persisted entry can't be older than the time it
     * was written) so we treat them as a developer mistake and substitute the default. We log a
     * warning rather than throwing because the SDK's contract is to never crash the host app.
     */
    private static long sanitizeTtl(long ttlMillis) {
        if (ttlMillis < 0) {
            MPLog.w(LOGTAG, "Negative TTL provided (" + ttlMillis + "ms); falling back to default of "
                    + DEFAULT_PERSISTENCE_TTL_MILLIS + "ms.");
            return DEFAULT_PERSISTENCE_TTL_MILLIS;
        }
        return ttlMillis;
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
     *                  not served. {@code 0} disables expiry. Negative values are treated as
     *                  a developer error and silently fall back to
     *                  {@link #DEFAULT_PERSISTENCE_TTL_MILLIS} (with a warning logged).
     */
    @NonNull
    public static PersistenceUntilNetworkSuccess persistenceUntilNetworkSuccess(long ttlMillis) {
        return new PersistenceUntilNetworkSuccess(sanitizeTtl(ttlMillis));
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
     *                  not served. {@code 0} disables expiry. Negative values are treated as
     *                  a developer error and silently fall back to
     *                  {@link #DEFAULT_PERSISTENCE_TTL_MILLIS} (with a warning logged).
     */
    @NonNull
    public static NetworkFirst networkFirst(long ttlMillis) {
        return new NetworkFirst(sanitizeTtl(ttlMillis));
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
