package sf.sfis.ifimsconnect.service;

import java.math.BigDecimal;
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

	@Transactional
	public void deleteByFlnuAndCkicAndUrno(FidsCcatab fidsCcatab) {
		try {
			log.info("delete fidsCcatab: flnu={}, ckic={}, urno={}",
					fidsCcatab.getFlnu(), fidsCcatab.getCkic(), fidsCcatab.getUrno());
			fidsCcatabRepository.deleteByFlnuAndCkicAndUrno(
					fidsCcatab.getFlnu(),
					fidsCcatab.getCkic(),
					fidsCcatab.getUrno());
		} catch (Exception e) {
			log.error("deleteCcatab: ", e);
		}
	}

	@Transactional
	public void deleteByUrno(BigDecimal urno) {
		try {
			log.info("delete fidsCcatab by URNO: " + urno);
			fidsCcatabRepository.deleteByUrno(urno);
		} catch (Exception e) {
			log.error("deleteCcatab: ", e);
		}
	}

	public void updateCcatab(FidsAfttab fidsAfttab) throws SQLException {
		if (fidsAfttab.getLstFidsCcatab() != null) {
			boolean isCtype = fidsAfttab.getLstFidsCcatab().stream()
					.anyMatch(item -> "C".equalsIgnoreCase(item.getCtyp()));
			// ตรวจสอบก่อนว่าเป็น Common มั้ย ถ้าเป็นให้ล้างข้อมูล Counter ด้วย URNO
			// ออกให้หมดก่อน แล้วค่อยบันทึกเข้าไปใหม่
			if (isCtype && fidsAfttab.getUrno() != null) {
				deleteByUrno(fidsAfttab.getUrno());
			}

			for (FidsCcatab ccatab : fidsAfttab.getLstFidsCcatab()) {
				// ถ้าเป็น Dedicated และลบข้อมูล ให้ลบตาม FLNU,CKIC แล้ววนลูปลบจนหมด
				isCtype = "C".equalsIgnoreCase(ccatab.getCtyp());
				if (!isCtype && "delete".equalsIgnoreCase(ccatab.getAction())) {
					deleteByFlnuAndCkicAndUrno(ccatab);
					continue;
				}

				FidsCcatab fidsCcatab = new FidsCcatab();
				fidsCcatab.setFlnu(ccatab.getFlnu());
				fidsCcatab.setUrno(ccatab.getUrno());
				fidsCcatab.setCkic(String.format("%-5s", ccatab.getCkic()));
				log.info("CKIC : " + ccatab.getCkic());

				fidsCcatab.setFlno(isCtype ? ccatab.getFlno() : fidsAfttab.getFlno());
				fidsCcatab.setHopo(fidsAfttab.getHopo());
				fidsCcatab.setAct3(isCtype ? ccatab.getAct3() : fidsAfttab.getAct3());
				fidsCcatab.setStod(fidsAfttab.getStod());
				fidsCcatab.setLstu(isCtype ? ccatab.getLstu() : fidsAfttab.getLstu());
				fidsCcatab.setCdat(fidsAfttab.getCdat());
				fidsCcatab.setPrfl(fidsAfttab.getPrfl());
				fidsCcatab.setStat(fidsAfttab.getStat());
				fidsCcatab.setUsec(isCtype ? ccatab.getUsec() : fidsAfttab.getUsec());
				fidsCcatab.setUseu(isCtype ? ccatab.getUsec() : fidsAfttab.getUseu());
				fidsCcatab.setCtyp(isCtype ? "C" : " ");
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
