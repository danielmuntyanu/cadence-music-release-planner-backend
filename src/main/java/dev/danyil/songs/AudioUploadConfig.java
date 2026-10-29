package dev.danyil.songs;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration 
@EnableConfigurationProperties(AudioUploadProperties.class)
public class AudioUploadConfig {
}
