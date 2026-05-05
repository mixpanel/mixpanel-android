package com.mixpanel.android.mpmetrics;

import androidx.annotation.NonNull;

/**
 * Configures how the SDK resolves feature flag variants relative to the
 * on-disk cache and the network.
 *
 * <p>Three strategies are supported:
 * <ul>
 *   <li>{@link NetworkOnly} - never read or write the on-disk cache. Variant
 *       lookups always wait for the network call. This is the default and
 *       matches behavior prior to the introduction of variant persistence.</li>
 *   <li>{@link CacheFirst} - serve cached variants immediately when available,
 *       and only block on the network if no cached variants exist for the
 *       current user context. Cached entries older than {@code cacheTtlMillis}
 *       are discarded.</li>
 *   <li>{@link NetworkFirst} - prefer fresh values from the network, but fall
 *       back to cached variants when the network call fails. Cached entries
 *       older than {@code cacheTtlMillis} are discarded.</li>
 * </ul>
 *
 * <p>Construct instances via the static factories rather than {@code new}:
 * <pre>{@code
 * VariantLookupPolicy.networkOnly()
 * VariantLookupPolicy.cacheFirst(TimeUnit.HOURS.toMillis(24))
 * VariantLookupPolicy.networkFirst(TimeUnit.HOURS.toMillis(24))
 * }</pre>
 *
 * <p>This class is intentionally Java 8 compatible (no {@code sealed} keyword)
 * even though the module compiles at source level 17. The package-private
 * constructor prevents external subclassing while leaving the type open for
 * {@code instanceof} dispatch at consumer sites.
 *
 * @see FeatureFlagOptions.Builder#variantLookupPolicy(VariantLookupPolicy)
 */
public abstract class VariantLookupPolicy {

    /**
     * Default cache TTL applied by {@link #cacheFirst()} and {@link #networkFirst()} when
     * no explicit TTL is provided. One hour.
     */
    public static final long DEFAULT_CACHE_TTL_MILLIS = 60L * 60 * 1000;

    VariantLookupPolicy() {}

    /**
     * Returns the singleton {@link NetworkOnly} strategy. Identity comparison is
     * meaningful — every call returns the same instance.
     */
    @NonNull
    public static NetworkOnly networkOnly() {
        return NetworkOnly.INSTANCE;
    }

    /**
     * Returns a {@link CacheFirst} strategy with the {@link #DEFAULT_CACHE_TTL_MILLIS}.
     */
    @NonNull
    public static CacheFirst cacheFirst() {
        return cacheFirst(DEFAULT_CACHE_TTL_MILLIS);
    }

    /**
     * Returns a {@link CacheFirst} strategy with the given cache TTL.
     *
     * @param cacheTtlMillis maximum age, in milliseconds, of a cached variant
     *                       set before it is discarded. Non-positive values
     *                       effectively disable the cache.
     */
    @NonNull
    public static CacheFirst cacheFirst(long cacheTtlMillis) {
        return new CacheFirst(cacheTtlMillis);
    }

    /**
     * Returns a {@link NetworkFirst} strategy with the {@link #DEFAULT_CACHE_TTL_MILLIS}.
     */
    @NonNull
    public static NetworkFirst networkFirst() {
        return networkFirst(DEFAULT_CACHE_TTL_MILLIS);
    }

    /**
     * Returns a {@link NetworkFirst} strategy with the given cache TTL.
     *
     * @param cacheTtlMillis maximum age, in milliseconds, of a cached variant
     *                       set before it is discarded. Non-positive values
     *                       effectively disable the cache.
     */
    @NonNull
    public static NetworkFirst networkFirst(long cacheTtlMillis) {
        return new NetworkFirst(cacheTtlMillis);
    }

    /**
     * Strategy that never consults the on-disk cache. Variant lookups always
     * await the network call.
     */
    public static final class NetworkOnly extends VariantLookupPolicy {
        // Held inside the subclass so the outer class's <clinit> does not reference it,
        // sidestepping the "subclass referenced from superclass initializer" deadlock pattern.
        static final NetworkOnly INSTANCE = new NetworkOnly();

        NetworkOnly() {}
    }

    /**
     * Strategy that serves cached variants immediately when available and only
     * waits on the network when the cache is empty.
     */
    public static final class CacheFirst extends VariantLookupPolicy {
        public final long cacheTtlMillis;

        CacheFirst(long cacheTtlMillis) {
            this.cacheTtlMillis = cacheTtlMillis;
        }
    }

    /**
     * Strategy that prefers a fresh network response but falls back to cached
     * variants when the network call fails.
     */
    public static final class NetworkFirst extends VariantLookupPolicy {
        public final long cacheTtlMillis;

        NetworkFirst(long cacheTtlMillis) {
            this.cacheTtlMillis = cacheTtlMillis;
        }
    }
}
