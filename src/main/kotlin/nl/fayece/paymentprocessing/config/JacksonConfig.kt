package nl.fayece.paymentprocessing.config

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class JacksonConfig {

    @Bean
    fun objectMapper(): ObjectMapper = JsonMapper.builder().findAndAddModules().build()
}
