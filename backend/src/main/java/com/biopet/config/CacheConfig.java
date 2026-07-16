package com.biopet.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class CacheConfig {

    public static final String CACHE_MASCOTAS_LISTADO = "mascotas-listado";
    private static final String PREFIJO_MASCOTAS_LISTADO = "mascotas:listado:";

    @Bean
    public RedisCacheManagerBuilderCustomizer mascotasCacheCustomizer(
            @Value("${app.cache.mascotas-listado.ttl-seconds:300}") long ttlSeconds) {

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build();
        // EVERYTHING (no NON_FINAL): PaginaResponse/MascotaResponse son records (clases
        // final), y GenericJackson2JsonRedisSerializer siempre deserializa hacia
        // Object.class sin conocer el tipo de antemano, por lo que necesita el
        // marcador "@class" tambien en la raiz, no solo en tipos no-final.
        mapper.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration mascotasListadoConfig = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> PREFIJO_MASCOTAS_LISTADO)
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        return builder -> builder.withCacheConfiguration(CACHE_MASCOTAS_LISTADO, mascotasListadoConfig);
    }
}
