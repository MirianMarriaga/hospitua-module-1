package com.hospitua.habitaciones.infrastructure.config;

import com.hospitua.habitaciones.infrastructure.adapters.out.rest.Module2Properties;
import com.hospitua.habitaciones.infrastructure.adapters.out.rest.Module3Properties;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configuración de clientes REST con timeouts estrictos para Módulo 2 y Módulo 3 (Regla 5).
 */
@Configuration
@EnableConfigurationProperties({Module2Properties.class, Module3Properties.class})
public class RestClientConfig {

    @Bean
    public RestClient module2RestClient(Module2Properties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        String baseUrl = properties.baseUrl() != null ? properties.baseUrl() : "";
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public RestClient module3RestClient(Module3Properties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        String baseUrl = properties.baseUrl() != null ? properties.baseUrl() : "";
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
