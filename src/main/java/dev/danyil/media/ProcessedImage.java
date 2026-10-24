package dev.danyil.media;

public record ProcessedImage(
    byte[] content, 
    String contentType, 
    String extension
) {
}