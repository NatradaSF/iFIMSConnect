package sf.sfis.ifimsconnect.repository;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import sf.sfis.ifimsconnect.model.FidsCcatab;
import sf.sfis.ifimsconnect.model.FidsCcatabId;

@Repository
public interface FidsCcatabRepository extends JpaRepository<FidsCcatab, FidsCcatabId> {
    @Transactional
    @Modifying
    void deleteByUrno(BigDecimal urno);

    @Modifying
    @Transactional
    void deleteByFlnuAndCkicAndUrno(BigDecimal flnu, String ckic, BigDecimal urno);
}
