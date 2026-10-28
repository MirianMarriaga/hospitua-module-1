package com.hospitua.habitaciones.infrastructure.config;

import com.hospitua.habitaciones.infrastructure.adapters.out.rest.Module2Properties;
import com.hospitua.habitaciones.infrastructure.adapters.out.rest.Module3Properties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Clientes REST hacia Módulo 2 y Módulo 3 con los timeouts de la regla 5 (T013). Las cabeceras propias de cada
 * contrato (por ejemplo {@code X-Client: MODULE1}) las agrega el adaptador que las usa.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient module2RestClient(Module2Properties properties) {
        return restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    public RestClient module3RestClient(Module3Properties properties) {
        return restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
    }

    private static RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
