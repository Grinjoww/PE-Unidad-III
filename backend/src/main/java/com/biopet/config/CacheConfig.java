package com.biopet.config;

import com.biopet.dto.MascotaResponse;
import com.biopet.dto.PaginaResponse;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;

@Configuration
public class CacheConfig {

    public static final String CACHE_MASCOTAS_LISTADO = "mascotas-listado";
    private static final String PREFIJO_MASCOTAS_LISTADO = "mascotas:listado:";

    @Bean
    public RedisCacheManagerBuilderCustomizer mascotasCacheCustomizer(
            @Value("${app.cache.mascotas-listado.ttl-seconds:300}") long ttlSeconds) {

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Solo los tipos concretos que este cache realmente almacena. No se permite
        // Object.class ni ninguna otra clase del classpath: una validacion permisiva
        // en deserializacion polimorfica es una superficie de ataque conocida
        // (gadget chains) si el contenido de Redis llegara a ser manipulable.
        BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(PaginaResponse.class)
                .allowIfSubType(MascotaResponse.class)
                .allowIfSubType(ArrayList.class)
                .allowIfSubType(LocalDate.class)
                .allowIfSubType(Instant.class)
                .allowIfSubType(Long.class)
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
