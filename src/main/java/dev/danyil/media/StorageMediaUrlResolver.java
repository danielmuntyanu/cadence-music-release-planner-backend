package dev.danyil.media;

import org.springframework.stereotype.Component;

import dev.danyil.contracts.StorageService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StorageMediaUrlResolver implements MediaUrlResolver {

    private final StorageService storage;

    @Override
    public String signedUrl(String key) {
        return storage.signedReadUrl(key);
    }
}