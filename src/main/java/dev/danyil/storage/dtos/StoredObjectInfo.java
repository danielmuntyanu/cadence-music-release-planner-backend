package dev.danyil.storage.dtos;

public record StoredObjectInfo(
    long size, 
    String contentType
) {}