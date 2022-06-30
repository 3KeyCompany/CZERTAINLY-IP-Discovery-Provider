package com.czertainly.discovery.ip.repository;

import com.czertainly.discovery.ip.dao.DiscoveryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface DiscoveryHistoryRepository extends JpaRepository<DiscoveryHistory, Long>{
	Optional<DiscoveryHistory> findById(Long Id);
	Optional<DiscoveryHistory> findByUuid(String uuid);
}