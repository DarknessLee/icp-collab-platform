package com.icp.common.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间序列化对齐 docs/03 §1.1：日期 yyyy-MM-dd，时间 yyyy-MM-dd HH:mm:ss（+08:00 时区由全局配置指定）。
 *
 * <p>ID 仍由前端按字符串处理（docs/03 §1.1 允许 number/string 两种），此处不改 Long 输出。</p>
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer icpTimeFormatCustomizer() {
        return builder -> {
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME));
            builder.serializerByType(LocalDate.class, new LocalDateSerializer(DATE));
            builder.deserializerByType(LocalDate.class, new LocalDateDeserializer(DATE));
            builder.serializerByType(LocalTime.class, new LocalTimeSerializer(TIME));
            builder.deserializerByType(LocalTime.class, new LocalTimeDeserializer(TIME));
        };
    }
}
