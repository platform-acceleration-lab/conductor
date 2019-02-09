package com.netflix.conductor.elasticsearch;

import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
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

public class ElasticSearchRestClientProvider implements Provider<RestClient> {

    private static final Logger logger =
        LoggerFactory.getLogger(ElasticSearchRestClientProvider.class);

    private final ElasticSearchConfiguration configuration;

    @Inject
    public ElasticSearchRestClientProvider(ElasticSearchConfiguration configuration) {
        this.configuration = configuration;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public RestClient get() {
        RestClientBuilder builder = RestClient.builder(convertToHttpHosts(configuration.getURIs()));

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        convertToAuthScopeCredentials(configuration.getURIs())
            .forEach(credentialsProvider::setCredentials);

        builder.setHttpClientConfigCallback(
            httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));

        return builder.build();
    }

    private HttpHost[] convertToHttpHosts(List<URI> hosts) {
        List<HttpHost> list = hosts.stream()
                .map(host -> new HttpHost(host.getHost(), host.getPort(), host.getScheme()))
                .collect(Collectors.toList());

        return list.toArray(new HttpHost[list.size()]);
    }

    private Map<AuthScope, Credentials> convertToAuthScopeCredentials(List<URI> hosts) {
        // filter hosts to those with user info credentials
        List<URI> userInfoHosts = hosts.stream()
            .filter(host -> StringUtils.isNotEmpty(host.getUserInfo()))
            .collect(Collectors.toList());

        // build map keyed on hosts to credentials
        return userInfoHosts.stream()
            .collect(Collectors.toMap(
                host -> new AuthScope(host.getHost(), host.getPort()),
                host -> {
                    String usernamePassword =
                        Arrays.toString(Base64.getDecoder().decode(host.getUserInfo()));
                    final int atColon = usernamePassword.indexOf(':');
                    if (atColon >= 0) {
                        return new UsernamePasswordCredentials(
                            usernamePassword.substring(0, atColon),
                            usernamePassword.substring(atColon + 1));
                    } else {
                        return new UsernamePasswordCredentials(usernamePassword, null);
                    }
                }
            ));
    }

}
