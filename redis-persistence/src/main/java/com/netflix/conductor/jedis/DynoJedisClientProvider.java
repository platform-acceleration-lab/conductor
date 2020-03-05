package com.netflix.conductor.jedis;

import com.netflix.conductor.dyno.DynomiteConfiguration;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.dyno.jedis.DynoJedisClient;
import javax.inject.Inject;
import javax.inject.Provider;

public class DynoJedisClientProvider implements Provider<DynoJedisClient> {

    private final HostSupplier hostSupplier;
    private final TokenMapSupplier tokenMapSupplier;
    private final DynomiteConfiguration configuration;

    @Inject
    public DynoJedisClientProvider(
            DynomiteConfiguration configuration,
            HostSupplier hostSupplier,
            TokenMapSupplier tokenMapSupplier
    ) {
        this.configuration = configuration;
        this.hostSupplier = hostSupplier;
        this.tokenMapSupplier = tokenMapSupplier;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public DynoJedisClient get() {
        ConnectionPoolConfigurationImpl connectionPoolConfiguration =
                new ConnectionPoolConfigurationImpl(configuration.getClusterName())
                .withTokenSupplier(tokenMapSupplier)
                .setLocalRack(configuration.getAvailabilityZone())
                .setLocalDataCenter(configuration.getRegion())
                .setSocketTimeout(0)
                .setConnectTimeout(0)
                .setMaxConnsPerHost(
                        configuration.getMaxConnectionsPerHost()
                );

        return new DynoJedisClient.Builder()
                .withHostSupplier(hostSupplier)
                .withApplicationName(configuration.getAppId())
                .withDynomiteClusterName(configuration.getClusterName())
                .withCPConfig(connectionPoolConfiguration)
                .build();
    }
}
