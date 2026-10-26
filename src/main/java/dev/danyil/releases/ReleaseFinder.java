package dev.danyil.releases;

import java.util.Objects;

import org.springframework.stereotype.Component;

import dev.danyil.releases.exceptions.ReleaseNotFoundException;
import dev.danyil.releases.exceptions.ReleaseOwnershipException;
import lombok.RequiredArgsConstructor;

/** The one place that decides whether a user may access a release; shared by release and song services. */
@Component
@RequiredArgsConstructor
public class ReleaseFinder {

    private final ReleaseRepository releaseRepository;

    public ReleaseEntity findOwned(Long userId, Long releaseId) {
        ReleaseEntity release = releaseRepository.findById(releaseId)
            .orElseThrow(() -> new ReleaseNotFoundException(releaseId));

        if (!Objects.equals(release.getUser().getId(), userId)) {
            throw new ReleaseOwnershipException();
        }
        return release;
    }
}