package com.jobhunter.shared.domain;

import com.jobhunter.shared.api.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/** Entity edited from the UI: optimistic locking with an explicit client-supplied version check. */
@MappedSuperclass
public abstract class VersionedEntity extends BaseEntity {

    @Version
    @Column(nullable = false)
    private int version;

    public int getVersion() {
        return version;
    }

    /** Rejects stale edits early (JPA would also reject them on flush, but with a less precise message). */
    public void checkVersion(Integer expected) {
        if (expected != null && expected != version) {
            throw ApiException.conflict("El recurso fue modificado por otra operación; recarga y vuelve a intentar");
        }
    }
}
