package com.bank.iso20022converter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Defines three ObjectMapper-family beans, all built from Spring Boot's own
 * {@link Jackson2ObjectMapperBuilder} (so each still gets Boot's standard modules -
 * JavaTimeModule etc. - rather than a bare {@code new ObjectMapper()}).
 *
 * <p>Important Spring Boot gotcha this works around: declaring ANY user {@code @Bean}
 * of type {@code ObjectMapper} suppresses Boot's own autoconfigured default entirely
 * (its bean is {@code @ConditionalOnMissingBean(ObjectMapper.class)}), which breaks
 * Spring MVC's JSON message converter and any unqualified {@code @Autowired
 * ObjectMapper} with a {@code NoUniqueBeanDefinitionException} once more than one
 * such bean exists. Re-declaring the default explicitly here, built from the same
 * builder and marked {@code @Primary}, keeps that default working for everyone else
 * while {@code typedJsonMapper}/{@code genericJsonMapper} remain available only via
 * explicit {@code @Qualifier}.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.build();
    }

    /**
     * Reads/writes the XJC-generated JAXB classes as JSON, honouring their
     * {@code @XmlElement} names via the JAXB annotation bridge module. Used for the
     * typed conversion path (known message types). Only injected via
     * {@code @Qualifier("typedJsonMapper")}.
     */
    @Bean
    public ObjectMapper typedJsonMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.build();
        mapper.registerModule(new JakartaXmlBindAnnotationModule());
        return mapper;
    }

    /** Plain ObjectMapper for generic JSON tree handling (the schema-agnostic fallback path). */
    @Bean
    public ObjectMapper genericJsonMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.build();
    }

    /** Generic, schema-agnostic XML tree mapper (the fallback path for unregistered message types). */
    @Bean
    public XmlMapper xmlMapper(Jackson2ObjectMapperBuilder builder) {
        XmlMapper mapper = new XmlMapper();
        builder.configure(mapper);
        return mapper;
    }
}
