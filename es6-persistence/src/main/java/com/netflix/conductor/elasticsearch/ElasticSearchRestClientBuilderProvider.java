package com.netflix.conductor.elasticsearch;

import java.util.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import javax.inject.Inject;
import javax.inject.Provider;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchRestClientBuilderProvider implements Provider<RestClientBuilder> {

    private static final Logger logger =
        LoggerFactory.getLogger(ElasticSearchRestClientBuilderProvider.class);

    private final ElasticSearchConfiguration configuration;

    @Inject
    public ElasticSearchRestClientBuilderProvider(ElasticSearchConfiguration configuration) {
        this.configuration = configuration;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public RestClientBuilder get() {
        RestClientBuilder builder = RestClient.builder(convertToHttpHosts(configuration.getURIs()));

        // assumes credentials for all endpoints are same as userInfo from first URI in list
        URI endpointUri = configuration.getURIs().get(0);
        if (StringUtils.isNotEmpty(endpointUri.getUserInfo())) {
            String encodedUserInfo =
                Base64.getEncoder().encodeToString(endpointUri.getUserInfo().getBytes());
            String authHeaderValue = "Basic " + encodedUserInfo;
            Header authHeader = new BasicHeader(HttpHeaders.AUTHORIZATION, authHeaderValue);

            builder.setDefaultHeaders(new Header[]{authHeader});
            logger.info("Setting Elasticsearch Authorization header: {}", authHeaderValue);
        }

        return builder;
    }

    private HttpHost[] convertToHttpHosts(List<URI> hosts) {
        List<HttpHost> list = hosts.stream()
                .map(host -> new HttpHost(host.getHost(), host.getPort(), host.getScheme()))
                .collect(Collectors.toList());

        return list.toArray(new HttpHost[list.size()]);
    }
}
