package dev.danyil.contracts;

import java.util.List;

import dev.danyil.songs.dtos.SongFileConfirmRequestDTO;
import dev.danyil.songs.dtos.SongFileRenameRequestDTO;
import dev.danyil.songs.dtos.SongFileResponseDTO;
import dev.danyil.songs.dtos.SongFileUploadRequestDTO;
import dev.danyil.songs.dtos.SongFileUploadResponseDTO;
import dev.danyil.songs.dtos.SongPrimaryFileRequestDTO;
import dev.danyil.storage.dtos.DownloadLinkDTO;

public interface SongFileService {

    List<SongFileResponseDTO> getAll(Long userId, Long releaseId, Long songId);

    SongFileUploadResponseDTO startUpload(Long userId, Long releaseId, Long songId, SongFileUploadRequestDTO request);

    SongFileResponseDTO confirmUpload(Long userId, Long releaseId, Long songId, SongFileConfirmRequestDTO request);

    SongFileResponseDTO rename(Long userId, Long releaseId, Long songId, Long fileId, SongFileRenameRequestDTO request);

    List<SongFileResponseDTO> setPrimary(Long userId, Long releaseId, Long songId, SongPrimaryFileRequestDTO request);

    DownloadLinkDTO downloadLink(Long userId, Long releaseId, Long songId, Long fileId);

    void delete(Long userId, Long releaseId, Long songId, Long fileId);
}