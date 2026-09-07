package sf.sfis.ifimsconnect.service;

import java.sql.SQLException;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sf.sfis.ifimsconnect.model.FidsAfttab;
import sf.sfis.ifimsconnect.model.FidsCcatab;
import sf.sfis.ifimsconnect.repository.FidsCcatabRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class FidsCcatabService {
	private final FidsCcatabRepository fidsCcatabRepository;
	
	@Transactional
	public FidsCcatab saveFidsCcatab(FidsCcatab fidsCcatab) {
		try {
			log.info(fidsCcatab.toString());
			fidsCcatab = fidsCcatabRepository.save(fidsCcatab);
		} catch (Exception e) {
			log.error(fidsCcatab.toString());
			log.error("saveFidsCcatab: ", e);
		}
		return fidsCcatab;
	}
	
//	@Transactional
//	public void deleteCcatab(FidsCcatab fidsCcatab) {
//		try {
//			log.info("delete fidsCcatab: "+ fidsCcatab.getFlnu()+", "+fidsCcatab.getCkic());
//			fidsCcatabRepository.delete(fidsCcatab);
//		} catch (Exception e) {
//			log.error("deleteCcatab: ", e);
//		}
//	}
	
	public void updateCcatab(FidsAfttab fidsAfttab) throws SQLException {
		if(fidsAfttab.getLstFidsCcatab() != null) {
			for(FidsCcatab ccatab : fidsAfttab.getLstFidsCcatab()) {
				FidsCcatab fidsCcatab = new FidsCcatab();
				fidsCcatab.setFlnu(ccatab.getFlnu()!=null?ccatab.getFlnu():fidsAfttab.getUrno());
				fidsCcatab.setCkic(String.format("%-5s", ccatab.getCkic()));
				log.info("CKIC : "+ccatab.getCkic());
				boolean isCtype = "C".equals(ccatab.getCtyp());
//				deleteCcatab(fidsCcatab);
				
				fidsCcatab.setFlno(isCtype?ccatab.getFlno():fidsAfttab.getFlno());
				fidsCcatab.setHopo(fidsAfttab.getHopo());
				fidsCcatab.setAct3(isCtype?ccatab.getAct3():fidsAfttab.getAct3());
				fidsCcatab.setStod(fidsAfttab.getStod());
				fidsCcatab.setLstu(isCtype?ccatab.getLstu():fidsAfttab.getLstu());
				fidsCcatab.setCdat(fidsAfttab.getCdat());
				fidsCcatab.setPrfl(fidsAfttab.getPrfl());
				fidsCcatab.setStat(fidsAfttab.getStat());
				fidsCcatab.setUsec(isCtype?ccatab.getUsec():fidsAfttab.getUsec());
				fidsCcatab.setUseu(isCtype?ccatab.getUsec():fidsAfttab.getUseu());
				fidsCcatab.setCtyp(isCtype?"C":" ");
				fidsCcatab.setCkbs(ccatab.getCkbs());
			    fidsCcatab.setCkes(ccatab.getCkes());
			    fidsCcatab.setCkba(ccatab.getCkba());
			    fidsCcatab.setCkea(ccatab.getCkea());
				fidsCcatab.setCkit(ccatab.getCkit());
				saveFidsCcatab(fidsCcatab);
			}
		}
	}

}
