package com.czertainly.discovery.ip.repository;

import com.czertainly.discovery.ip.dao.Certificate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public interface CertificateRepository extends JpaRepository<Certificate, Long>{
	List<Certificate> findAllByDiscoveryId(Long discoveryId, Pageable pagable);
	List<Certificate> findByDiscoveryId(Long discoveryId);
	Optional<Certificate> findById(Long id);
}
