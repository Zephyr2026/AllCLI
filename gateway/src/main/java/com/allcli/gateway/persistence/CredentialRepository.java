package com.allcli.gateway.persistence;

import com.allcli.gateway.model.Credential;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialRepository extends JpaRepository<Credential, Long> {

    Optional<Credential> findByCredId(String credId);

    Optional<Credential> findBySystemRef(String systemRef);
}
