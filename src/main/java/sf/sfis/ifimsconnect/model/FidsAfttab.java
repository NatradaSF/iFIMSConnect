package sf.sfis.ifimsconnect.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

@Entity
@Data
@Table(name = "FIDS_AFTTAB")
public class FidsAfttab implements Serializable {
	private static final long serialVersionUID = 1L;
	@Id
	@Column(name = "URNO")
	private BigDecimal urno;

	@Column(name = "ACT3", length = 14)
	private String act3;

	@Column(name = "ACT5", length = 5)
	private String act5;

	@Column(name = "ACTI", length = 5)
	private String acti;

	@Column(name = "ADER", length = 1)
	private String ader;

	@Column(name = "ADID", length = 1)
	private String adid;

	@Column(name = "AIRA", length = 14)
	private String aira;

	@Column(name = "AIRB", length = 14)
	private String airb;

	@Column(name = "AIRD", length = 14)
	private String aird;

	@Column(name = "AIRE", length = 14)
	private String aire;

	@Column(name = "AIRU", length = 14)
	private String airu;

	@Column(name = "ALC2", length = 2)
	private String alc2;

	@Column(name = "ALC3", length = 3)
	private String alc3;

	@Column(name = "ANNX", length = 2)
	private String annx;

	@Column(name = "AURN", length = 20)
	private String aurn;

	@Column(name = "B1BA", length = 14)
	private String b1ba;

	@Column(name = "B1BS", length = 14)
	private String b1bs;

	@Column(name = "B1EA", length = 14)
	private String b1ea;

	@Column(name = "B1ES", length = 14)
	private String b1es;

	@Column(name = "B2BA", length = 14)
	private String b2ba;

	@Column(name = "B2BS", length = 14)
	private String b2bs;

	@Column(name = "B2EA", length = 14)
	private String b2ea;

	@Column(name = "B2ES", length = 14)
	private String b2es;

	@Column(name = "B3FP", length = 1)
	private String b3fp;

	@Column(name = "BAA3", length = 4)
	private String baa3;

	@Column(name = "BAC1", length = 14)
	private String bac1;

	@Column(name = "BAC2", length = 14)
	private String bac2;

	@Column(name = "BAC3", length = 14)
	private String bac3;

	@Column(name = "BAC4", length = 14)
	private String bac4;

	@Column(name = "BAC5", length = 14)
	private String bac5;

	@Column(name = "BAC6", length = 14)
	private String bac6;

	@Column(name = "BAE1", length = 14)
	private String bae1;

	@Column(name = "BAE2", length = 14)
	private String bae2;

	@Column(name = "BAE3", length = 14)
	private String bae3;

	@Column(name = "BAE4", length = 14)
	private String bae4;

	@Column(name = "BAE5", length = 14)
	private String bae5;

	@Column(name = "BAE6", length = 14)
	private String bae6;

	@Column(name = "BAG0", length = 5)
	private String bag0;

	@Column(name = "BAGN", length = 6)
	private String bagn;

	@Column(name = "BAGW", length = 7)
	private String bagw;

	@Column(name = "BAO1", length = 14)
	private String bao1;

	@Column(name = "BAO2", length = 14)
	private String bao2;

	@Column(name = "BAO3", length = 14)
	private String bao3;

	@Column(name = "BAO4", length = 14)
	private String bao4;

	@Column(name = "BAO5", length = 14)
	private String bao5;

	@Column(name = "BAO6", length = 14)
	private String bao6;

	@Column(name = "BAS1", length = 14)
	private String bas1;

	@Column(name = "BAS2", length = 14)
	private String bas2;

	@Column(name = "BAS3", length = 14)
	private String bas3;

	@Column(name = "BAS4", length = 14)
	private String bas4;

	@Column(name = "BAS5", length = 14)
	private String bas5;

	@Column(name = "BAS6", length = 14)
	private String bas6;

	@Column(name = "BAST", length = 2)
	private String bast;

	@Column(name = "BAZ1", length = 3)
	private String baz1;

	@Column(name = "BAZ2", length = 3)
	private String baz2;

	@Column(name = "BAZ3", length = 3)
	private String baz3;

	@Column(name = "BAZ4", length = 3)
	private String baz4;

	@Column(name = "BAZ5", length = 3)
	private String baz5;

	@Column(name = "BAZ6", length = 3)
	private String baz6;

	@Column(name = "BBAA", length = 14)
	private String bbaa;

	@Column(name = "BBFA", length = 14)
	private String bbfa;

	@Column(name = "BLT1", length = 5)
	private String blt1;

	@Column(name = "BLT2", length = 5)
	private String blt2;

	@Column(name = "BOAC", length = 14)
	private String boac;

	@Column(name = "BOAO", length = 14)
	private String boao;

	@Column(name = "CDAT", length = 14)
	private String cdat;

	@Column(name = "CGOT", length = 6)
	private String cgot;

	@Column(name = "CHGI", length = 20)
	private String chgi;

	@Column(name = "CHT3", length = 3)
	private String cht3;

	@Column(name = "CKIF", length = 5)
	private String ckif;

	@Column(name = "CKIT", length = 5)
	private String ckit;

	@Column(name = "CONF", length = 6)
	private String conf;

	@Column(name = "CSGN", length = 8)
	private String csgn;

	@Column(name = "CTOT", length = 14)
	private String ctot;

	@Column(name = "DCD1", length = 2)
	private String dcd1;

	@Column(name = "DCD2", length = 2)
	private String dcd2;

	@Column(name = "DDLF", length = 6)
	private String ddlf;

	@Column(name = "DDLR", length = 6)
	private String ddlr;

	@Column(name = "DELA", length = 255)
	private String dela;

	@Column(name = "DELD", length = 255)
	private String deld;

	@Column(name = "DES3", length = 3)
	private String des3;

	@Column(name = "DES4", length = 4)
	private String des4;

	@Column(name = "DIVR", length = 14)
	private String divr;

	@Column(name = "DOOA", length = 1)
	private String dooa;

	@Column(name = "DOOD", length = 1)
	private String dood;

	@Column(name = "DSSF", length = 128)
	private String dssf;

	@Column(name = "DTD1", length = 4)
	private String dtd1;

	@Column(name = "DTD2", length = 4)
	private String dtd2;

	@Column(name = "ENTY", length = 5)
	private String enty;

	@Column(name = "ETAA", length = 14)
	private String etaa;

	@Column(name = "ETAC", length = 14)
	private String etac;

	@Column(name = "ETAE", length = 14)
	private String etae;

	@Column(name = "ETAI", length = 14)
	private String etai;

	@Column(name = "ETAS", length = 14)
	private String etas;

	@Column(name = "ETAU", length = 14)
	private String etau;

	@Column(name = "ETDA", length = 14)
	private String etda;

	@Column(name = "ETDC", length = 14)
	private String etdc;

	@Column(name = "ETDE", length = 14)
	private String etde;

	@Column(name = "ETDI", length = 14)
	private String etdi;

	@Column(name = "ETDS", length = 14)
	private String etds;

	@Column(name = "ETDU", length = 14)
	private String etdu;

	@Column(name = "ETOA", length = 14)
	private String etoa;

	@Column(name = "ETOD", length = 14)
	private String etod;

	@Column(name = "EXT1", length = 5)
	private String ext1;

	@Column(name = "EXT2", length = 5)
	private String ext2;

	@Column(name = "FCAL", length = 14)
	private String fcal;

	@Column(name = "FDAT", length = 14)
	private String fdat;

	@Column(name = "FKEY", length = 18)
	private String fkey;

	@Column(name = "FLDA", length = 8)
	private String flda;

	@Column(name = "FLNO", length = 9)
	private String flno;

	@Column(name = "FLNS", length = 1)
	private String flns;

	@Column(name = "FLTC", length = 14)
	private String fltc;

	@Column(name = "FLTI", length = 1)
	private String flti;

	@Column(name = "FLTN", length = 5)
	private String fltn;

	@Column(name = "FLTO", length = 14)
	private String flto;

	@Column(name = "FREA", length = 1)
	private String frea;

	@Column(name = "FRED", length = 1)
	private String fred;

	@Column(name = "FTYP", length = 1)
	private String ftyp;

	@Column(name = "GA1B", length = 14)
	private String ga1b;

	@Column(name = "GA1E", length = 14)
	private String ga1e;

	@Column(name = "GA1X", length = 14)
	private String ga1x;

	@Column(name = "GA1Y", length = 14)
	private String ga1y;

	@Column(name = "GA2B", length = 14)
	private String ga2b;

	@Column(name = "GA2E", length = 14)
	private String ga2e;

	@Column(name = "GA2F", length = 1)
	private String ga2f;

	@Column(name = "GA2X", length = 14)
	private String ga2x;

	@Column(name = "GA2Y", length = 14)
	private String ga2y;

	@Column(name = "GD1B", length = 14)
	private String gd1b;

	@Column(name = "GD1E", length = 14)
	private String gd1e;

	@Column(name = "GD1X", length = 14)
	private String gd1x;

	@Column(name = "GD1Y", length = 14)
	private String gd1y;

	@Column(name = "GD2B", length = 14)
	private String gd2b;

	@Column(name = "GD2E", length = 14)
	private String gd2e;

	@Column(name = "GD2X", length = 14)
	private String gd2x;

	@Column(name = "GD2Y", length = 14)
	private String gd2y;

	@Column(name = "GTA1", length = 5)
	private String gta1;

	@Column(name = "GTA2", length = 5)
	private String gta2;

	@Column(name = "GTD1", length = 5)
	private String gtd1;

	@Column(name = "GTD2", length = 5)
	private String gtd2;

	@Column(name = "HACA", length = 3)
	private String haca;

	@Column(name = "HACG", length = 3)
	private String hacg;

	@Column(name = "HACI", length = 3)
	private String haci;

	@Column(name = "HACL", length = 3)
	private String hacl;

	@Column(name = "HADL", length = 3)
	private String hadl;

	@Column(name = "HALF", length = 3)
	private String half;

	@Column(name = "HAMM", length = 3)
	private String hamm;

	@Column(name = "HAPX", length = 3)
	private String hapx;

	@Column(name = "HARA", length = 3)
	private String hara;

	@Column(name = "HDLL", length = 40)
	private String hdll;

	@Column(name = "HOPO", length = 3)
	private String hopo;

	@Column(name = "HTYP", length = 2)
	private String htyp;

	@Column(name = "IFRA", length = 1)
	private String ifra;

	@Column(name = "IFRD", length = 1)
	private String ifrd;

	@Column(name = "ISKD", length = 14)
	private String iskd;

	@Column(name = "ISRE", length = 1)
	private String isre;

	@Column(name = "JCNT", length = 2)
	private String jcnt;

	@Column(name = "JFNO", length = 450)
	private String jfno;

	@Column(name = "LAND", length = 14)
	private String land;

	@Column(name = "LNDA", length = 14)
	private String lnda;

	@Column(name = "LNDD", length = 14)
	private String lndd;

	@Column(name = "LNDU", length = 14)
	private String lndu;

	@Column(name = "LSTU", length = 14)
	private String lstu;

	@Column(name = "MAIL", length = 6)
	private String mail;

	@Column(name = "MING", length = 4)
	private String ming;

	@Column(name = "MTOW", length = 10)
	private String mtow;

	@Column(name = "NOSE", length = 3)
	private String nose;

	@Column(name = "NXTI", length = 14)
	private String nxti;

	@Column(name = "OFBD", length = 14)
	private String ofbd;

	@Column(name = "OFBL", length = 14)
	private String ofbl;

	@Column(name = "OFBS", length = 14)
	private String ofbs;

	@Column(name = "OFBU", length = 14)
	private String ofbu;

	@Column(name = "OFLB", length = 4)
	private String oflb;

	@Column(name = "OFLC", length = 4)
	private String oflc;

	@Column(name = "OFLM", length = 4)
	private String oflm;

	@Column(name = "ONBD", length = 14)
	private String onbd;

	@Column(name = "ONBE", length = 14)
	private String onbe;

	@Column(name = "ONBL", length = 14)
	private String onbl;

	@Column(name = "ONBS", length = 14)
	private String onbs;

	@Column(name = "ONBU", length = 14)
	private String onbu;

	@Column(name = "ORG3", length = 3)
	private String org3;

	@Column(name = "ORG4", length = 4)
	private String org4;

	@Column(name = "PABA", length = 14)
	private String paba;

	@Column(name = "PABS", length = 14)
	private String pabs;

	@Column(name = "PAEA", length = 14)
	private String paea;

	@Column(name = "PAES", length = 14)
	private String paes;

	@Column(name = "PAID", length = 1)
	private String paid;

	@Column(name = "PAX1", length = 3)
	private String pax1;

	@Column(name = "PAX2", length = 3)
	private String pax2;

	@Column(name = "PAX3", length = 3)
	private String pax3;

	@Column(name = "PAXF", length = 3)
	private String paxf;

	@Column(name = "PAXI", length = 3)
	private String paxi;

	@Column(name = "PAXT", length = 3)
	private String paxt;

	@Column(name = "PDBA", length = 14)
	private String pdba;

	@Column(name = "PDBS", length = 14)
	private String pdbs;

	@Column(name = "PDEA", length = 14)
	private String pdea;

	@Column(name = "PDES", length = 14)
	private String pdes;

	@Column(name = "PRFL", length = 1)
	private String prfl;

	@Column(name = "PSTA", length = 5)
	private String psta;

	@Column(name = "PSTD", length = 5)
	private String pstd;

	@Column(name = "RACO", length = 5)
	private String raco;

	@Column(name = "REGN", length = 12)
	private String regn;

	@Column(name = "REM1", length = 256)
	private String rem1;

	@Column(name = "REM2", length = 256)
	private String rem2;

	@Column(name = "REMP", length = 4)
	@Setter(AccessLevel.NONE)
	private String remp;

	@Column(name = "RKEY")
	private BigDecimal rkey;

	@Column(name = "RTYP", length = 1)
	private String rtyp;

	@Column(name = "RWYA", length = 4)
	private String rwya;

	@Column(name = "RWYD", length = 4)
	private String rwyd;

	@Column(name = "SEAS", length = 6)
	private String seas;

	@Column(name = "SFIF", length = 1)
	private String sfif;

	@Column(name = "SKEY")
	private BigDecimal skey;

	@Column(name = "SLOT", length = 14)
	private String slot;

	@Column(name = "SLOU", length = 14)
	private String slou;

	@Column(name = "SSRC", length = 4)
	private String ssrc;

	@Column(name = "STAB", length = 1)
	private String stab;

	@Column(name = "STAT", length = 10)
	private String stat;

	@Column(name = "STEA", length = 1)
	private String stea;

	@Column(name = "STED", length = 1)
	private String sted;

	@Column(name = "STEV", length = 1)
	private String stev;

	@Column(name = "STHT", length = 1)
	private String stht;

	@Column(name = "STLD", length = 1)
	private String stld;

	@Column(name = "STOA", length = 14)
	private String stoa;

	@Column(name = "STOD", length = 14)
	private String stod;

	@Column(name = "STOF", length = 1)
	private String stof;

	@Column(name = "STON", length = 1)
	private String ston;

	@Column(name = "STSL", length = 1)
	private String stsl;

	@Column(name = "STTM", length = 1)
	private String sttm;

	@Column(name = "STTY", length = 1)
	private String stty;

	@Column(name = "STYP", length = 2)
	private String styp;

	@Column(name = "TCCI", length = 1)
	private String tcci;

	@Column(name = "TET1", length = 1)
	private String tet1;

	@Column(name = "TET2", length = 1)
	private String tet2;

	@Column(name = "TGA1", length = 1)
	private String tga1;

	@Column(name = "TGA2", length = 1)
	private String tga2;

	@Column(name = "TGD1", length = 1)
	private String tgd1;

	@Column(name = "TGD2", length = 1)
	private String tgd2;

	@Column(name = "TIFA", length = 14)
	private String tifa;

	@Column(name = "TIFD", length = 14)
	private String tifd;

	@Column(name = "TISA", length = 1)
	private String tisa;

	@Column(name = "TISD", length = 1)
	private String tisd;

	@Column(name = "TMAA", length = 14)
	private String tmaa;

	@Column(name = "TMAU", length = 14)
	private String tmau;

	@Column(name = "TMB1", length = 1)
	private String tmb1;

	@Column(name = "TMB2", length = 1)
	private String tmb2;

	@Column(name = "TMB3", length = 1)
	private String tmb3;

	@Column(name = "TMOA", length = 14)
	private String tmoa;

	@Column(name = "TRKN", length = 4)
	private String trkn;

	@Column(name = "TTYP", length = 5)
	private String ttyp;

	@Column(name = "TWR1", length = 1)
	private String twr1;

	@Column(name = "USEC", length = 32)
	private String usec;

	@Column(name = "USEU", length = 32)
	private String useu;

	@Column(name = "VERS", length = 20)
	private String vers;

	@Column(name = "VIA3", length = 3)
	private String via3;

	@Column(name = "VIA4", length = 4)
	private String via4;

	@Column(name = "VIAL", length = 1024)
	private String vial;

	@Column(name = "VIAN", length = 2)
	private String vian;

	@Column(name = "W1BA", length = 14)
	private String w1ba;

	@Column(name = "W1BS", length = 14)
	private String w1bs;

	@Column(name = "W1EA", length = 14)
	private String w1ea;

	@Column(name = "W1ES", length = 14)
	private String w1es;

	@Column(name = "WR1F", length = 5)
	private String wr1f;

	@Column(name = "WRO1", length = 5)
	private String wro1;

	@Column(name = "ATRN", length = 12)
	private String atrn;

	@Column(name = "B1FP", length = 1)
	private String b1fp;

	@Column(name = "B2FP", length = 1)
	private String b2fp;

	@Column(name = "BAA1", length = 4)
	private String baa1;

	@Column(name = "BAA2", length = 4)
	private String baa2;

	@Column(name = "BAA4", length = 4)
	private String baa4;

	@Column(name = "BAA5", length = 4)
	private String baa5;

	@Column(name = "BAAA", length = 4)
	private String baaa;

	@Column(name = "BABS", length = 4)
	private String babs;

	@Column(name = "BADU", length = 6)
	private String badu;

	@Column(name = "BAEA", length = 4)
	private String baea;

	@Column(name = "BAGS", length = 4)
	private String bags;

	@Column(name = "BALG", length = 14)
	private String balg;

	@Column(name = "BALS", length = 14)
	private String bals;

	@Column(name = "BAMA", length = 4)
	private String bama;

	@Column(name = "BAOI", length = 4)
	private String baoi;

	@Column(name = "GA1F", length = 1)
	private String ga1f;

	@Column(name = "GD1F", length = 1)
	private String gd1f;

	@Column(name = "GD2F", length = 1)
	private String gd2f;

	@Column(name = "NADS", length = 1)
	private String nads;

	@Column(name = "PAFP", length = 1)
	private String pafp;

	@Column(name = "PDFP", length = 1)
	private String pdfp;

	@Column(name = "W2BA", length = 14)
	private String w2ba;

	@Column(name = "W2BS", length = 14)
	private String w2bs;

	@Column(name = "W2EA", length = 14)
	private String w2ea;

	@Column(name = "W2ES", length = 14)
	private String w2es;

	@Column(name = "WRO2", length = 5)
	private String wro2;

	@Column(name = "B3BA", length = 14)
	private String b3ba;

	@Column(name = "B3BS", length = 14)
	private String b3bs;

	@Column(name = "B3EA", length = 14)
	private String b3ea;

	@Column(name = "B3ES", length = 14)
	private String b3es;

	@Column(name = "BLT3", length = 5)
	private String blt3;

	@Column(name = "FCLS", length = 14)
	private String fcls;

	@Column(name = "FPSD", length = 1)
	private String fpsd;

	@Column(name = "FPSA", length = 1)
	private String fpsa;

	@Column(name = "VIPA", length = 2)
	private String vipa;

	@Column(name = "VIPD", length = 2)
	private String vipd;

	@Column(name = "PCOM", length = 200)
	private String pcom;

	@Column(name = "STTT")
	private BigDecimal sttt;

	@Column(name = "ACDC", length = 1)
	private String acdc;

	@Column(name = "ACGS", length = 14)
	private String acgs;

	@Column(name = "ACGT", length = 14)
	private String acgt;

	@Column(name = "ACGU", length = 14)
	private String acgu;

	@Column(name = "ACIS", length = 1)
	private String acis;

	@Column(name = "ACOR", length = 14)
	private String acor;

	@Column(name = "ACZT", length = 14)
	private String aczt;

	@Column(name = "ACZU", length = 14)
	private String aczu;

	@Column(name = "ADCN", length = 4)
	private String adcn;

	@Column(name = "ADHO", length = 1)
	private String adho;

	@Column(name = "ADIS", length = 10)
	private String adis;

	@Column(name = "ADIT", length = 10)
	private String adit;

	@Column(name = "ADIU", length = 10)
	private String adiu;

	@Column(name = "AEGS", length = 14)
	private String aegs;

	@Column(name = "AEGT", length = 14)
	private String aegt;

	@Column(name = "AEGU", length = 14)
	private String aegu;

	@Column(name = "AEZT", length = 14)
	private String aezt;

	@Column(name = "AEZU", length = 14)
	private String aezu;

	@Column(name = "AGHT")
	private BigDecimal aght;

	@Column(name = "AIBG", length = 14)
	private String aibg;

	@Column(name = "AIBM", length = 14)
	private String aibm;

	@Column(name = "AIBN", length = 14)
	private String aibn;

	@Column(name = "AIBR", length = 14)
	private String aibr;

	@Column(name = "AIBT", length = 14)
	private String aibt;

	@Column(name = "AIBU", length = 14)
	private String aibu;

	@Column(name = "ALDA", length = 14)
	private String alda;

	// Start
	@Column(name = "ALDF", length = 14)
	private String aldf;

	@Column(name = "ALDM", length = 14)
	private String aldm;

	@Column(name = "ALDN", length = 14)
	private String aldn;

	@Column(name = "ALDR", length = 14)
	private String aldr;

	@Column(name = "ALDT", length = 14)
	private String aldt;

	@Column(name = "ALDU", length = 14)
	private String aldu;

	@Column(name = "AOBG", length = 14)
	private String aobg;

	@Column(name = "AOBM", length = 14)
	private String aobm;

	@Column(name = "AOBN", length = 14)
	private String aobn;

	@Column(name = "AOBR", length = 14)
	private String aobr;

	@Column(name = "AOBS", length = 14)
	private String aobs;

	@Column(name = "AOBT", length = 14)
	private String aobt;

	@Column(name = "AOBU", length = 14)
	private String aobu;

	@Column(name = "APEA", length = 14)
	private String apea;

	@Column(name = "APET", length = 14)
	private String apet;

	@Column(name = "APEU", length = 14)
	private String apeu;

	@Column(name = "ARDT", length = 14)
	private String ardt;

	@Column(name = "ARZS", length = 14)
	private String arzs;

	@Column(name = "ARZT", length = 14)
	private String arzt;

	@Column(name = "ARZU", length = 14)
	private String arzu;

	@Column(name = "ASAA", length = 14)
	private String asaa;

	@Column(name = "ASAT", length = 14)
	private String asat;

	@Column(name = "ASAU", length = 14)
	private String asau;

	@Column(name = "ASBT", length = 14)
	private String asbt;

	@Column(name = "ASRA", length = 14)
	private String asra;

	@Column(name = "ASRT", length = 14)
	private String asrt;

	@Column(name = "ASRU", length = 14)
	private String asru;

	@Column(name = "ATOA", length = 14)
	private String atoa;

	@Column(name = "ATOF", length = 14)
	private String atof;

	@Column(name = "ATOM", length = 14)
	private String atom;

	@Column(name = "ATON", length = 14)
	private String aton;

	@Column(name = "ATOT", length = 14)
	private String atot;

	@Column(name = "ATOU", length = 14)
	private String atou;

	@Column(name = "ATOW", length = 1)
	private String atow;

	@Column(name = "ATRF", length = 10)
	private String atrf;

	@Column(name = "ATTT")
	private BigDecimal attt;

	@Column(name = "AXIT")
	private BigDecimal axit;

	@Column(name = "AXOT")
	private BigDecimal axot;

	@Column(name = "BDDT", length = 10)
	private String bddt;

	@Column(name = "BGDS", length = 14)
	private String bgds;

	@Column(name = "BGDT", length = 14)
	private String bgdt;

	@Column(name = "BGDU", length = 14)
	private String bgdu;

	@Column(name = "BLTO", length = 200)
	private String blto;

	@Column(name = "BPAC", length = 14)
	private String bpac;

	@Column(name = "BPAO", length = 14)
	private String bpao;

	@Column(name = "CHAA", length = 2)
	private String chaa;

	@Column(name = "CHAB", length = 2)
	private String chab;

	@Column(name = "CHAD", length = 2)
	private String chad;

	@Column(name = "CHF1", length = 14)
	private String chf1;

	@Column(name = "CHF2", length = 14)
	private String chf2;

	@Column(name = "CHFU", length = 14)
	private String chfu;

	@Column(name = "CHO1", length = 14)
	private String cho1;

	@Column(name = "CHO2", length = 14)
	private String cho2;

	@Column(name = "CHOF", length = 14)
	private String chof;

	@Column(name = "CHON", length = 14)
	private String chon;

	@Column(name = "CHOU", length = 14)
	private String chou;

	@Column(name = "CNAL")
	private BigDecimal cnal;

	@Column(name = "CNCR")
	private BigDecimal cncr;

	@Column(name = "CNNO")
	private BigDecimal cnno;

	@Column(name = "COBY", length = 20)
	private String coby;

	@Column(name = "CROW", length = 8)
	private String crow;

	@Column(name = "CTOA", length = 14)
	private String ctoa;

	@Column(name = "CTOF", length = 14)
	private String ctof;

	@Column(name = "CTOS", length = 14)
	private String ctos;

	@Column(name = "CTOU", length = 14)
	private String ctou;

	@Column(name = "CXXR", length = 5)
	private String cxxr;

	@Column(name = "DEPN", length = 255)
	private String depn;

	@Column(name = "DICE", length = 1)
	private String dice;

	@Column(name = "DIHO", length = 2)
	private String diho;

	@Column(name = "DQCS", length = 4)
	private String dqcs;

	@Column(name = "ECZS", length = 14)
	private String eczs;

	@Column(name = "ECZT", length = 14)
	private String eczt;

	@Column(name = "ECZU", length = 14)
	private String eczu;

	@Column(name = "EDIS", length = 10)
	private String edis;

	@Column(name = "EDIT", length = 10)
	private String edit;

	@Column(name = "EDIU", length = 10)
	private String ediu;

	@Column(name = "EEZS", length = 14)
	private String eezs;

	@Column(name = "EEZT", length = 14)
	private String eezt;

	@Column(name = "EEZU", length = 14)
	private String eezu;

	@Column(name = "EIBE", length = 14)
	private String eibe;

	@Column(name = "EIBG", length = 14)
	private String eibg;

	@Column(name = "EIBM", length = 14)
	private String eibm;

	@Column(name = "EIBN", length = 14)
	private String eibn;

	@Column(name = "EIBR", length = 14)
	private String eibr;

	@Column(name = "EIBS", length = 14)
	private String eibs;

	@Column(name = "EIBT", length = 14)
	private String eibt;

	@Column(name = "EIBU", length = 14)
	private String eibu;

	@Column(name = "EIBX", length = 14)
	private String eibx;

	@Column(name = "ELDA", length = 14)
	private String elda;

	@Column(name = "ELDF", length = 14)
	private String eldf;

	@Column(name = "ELDM", length = 14)
	private String eldm;

	@Column(name = "ELDN", length = 14)
	private String eldn;

	@Column(name = "ELDR", length = 14)
	private String eldr;

	@Column(name = "ELDS", length = 14)
	private String elds;

	@Column(name = "ELDT", length = 14)
	private String eldt;

	@Column(name = "ELDU", length = 14)
	private String eldu;

	@Column(name = "ENRC", length = 14)
	private String enrc;

	@Column(name = "EOBF", length = 14)
	private String eobf;

	@Column(name = "EOBG", length = 14)
	private String eobg;

	@Column(name = "EOBM", length = 14)
	private String eobm;

	@Column(name = "EOBN", length = 14)
	private String eobn;

	@Column(name = "EOBR", length = 14)
	private String eobr;

	@Column(name = "EOBS", length = 14)
	private String eobs;

	@Column(name = "EOBT", length = 14)
	private String eobt;

	@Column(name = "EOBU", length = 14)
	private String eobu;

	@Column(name = "ERZS", length = 14)
	private String erzs;

	@Column(name = "ERZT", length = 14)
	private String erzt;

	@Column(name = "ERZU", length = 14)
	private String erzu;

	@Column(name = "ESBB", length = 14)
	private String esbb;

	@Column(name = "ESBS", length = 14)
	private String esbs;

	@Column(name = "ESBT", length = 14)
	private String esbt;

	@Column(name = "ESBU", length = 14)
	private String esbu;

	@Column(name = "ETOM", length = 14)
	private String etom;

	@Column(name = "ETON", length = 14)
	private String eton;

	@Column(name = "ETOS", length = 14)
	private String etos;

	@Column(name = "ETOT", length = 14)
	private String etot;

	@Column(name = "ETOU", length = 14)
	private String etou;

	@Column(name = "ETTA", length = 14)
	private String etta;

	@Column(name = "ETTE", length = 14)
	private String ette;

	@Column(name = "ETTS")
	private BigDecimal etts;

	@Column(name = "ETTT", length = 10)
	private String ettt;

	@Column(name = "ETTU", length = 10)
	private String ettu;

	@Column(name = "EXDR", length = 10)
	private String exdr;

	@Column(name = "EXIT")
	private BigDecimal exit;

	@Column(name = "EXOT")
	private BigDecimal exot;

	@Column(name = "EXSD", length = 10)
	private String exsd;

	@Column(name = "FAOG", length = 1)
	private String faog;

	@Column(name = "FBL1", length = 1)
	private String fbl1;

	@Column(name = "FBL2", length = 1)
	private String fbl2;

	@Column(name = "FBLR", length = 5)
	private String fblr;

	@Column(name = "FGA1", length = 1)
	private String fga1;

	@Column(name = "FGA2", length = 1)
	private String fga2;

	@Column(name = "FGAR", length = 5)
	private String fgar;

	@Column(name = "FGD1", length = 1)
	private String fgd1;

	@Column(name = "FGD2", length = 1)
	private String fgd2;

	@Column(name = "FGDR", length = 5)
	private String fgdr;

	@Column(name = "FINN", length = 12)
	private String finn;

	@Column(name = "FLUT", length = 8)
	private String flut;

	@Column(name = "FPAR", length = 5)
	private String fpar;

	@Column(name = "FPDR", length = 5)
	private String fpdr;

	@Column(name = "FPLA", length = 14)
	private String fpla;

	@Column(name = "FPLD", length = 14)
	private String fpld;

	@Column(name = "FPLF", length = 14)
	private String fplf;

	@Column(name = "FWR1", length = 1)
	private String fwr1;

	@Column(name = "FWR2", length = 1)
	private String fwr2;

	@Column(name = "HANG", length = 14)
	private String hang;

	@Column(name = "HDAG", length = 100)
	private String hdag;

	@Column(name = "IROL", length = 16)
	private String irol;

	@Column(name = "IRTM", length = 14)
	private String irtm;

	@Column(name = "MOPA", length = 1)
	private String mopa;

	@Column(name = "MTTT")
	private BigDecimal mttt;

	@Column(name = "NFES", length = 14)
	private String nfes;

	@Column(name = "PALB", length = 9)
	private String palb;

	@Column(name = "PALL", length = 9)
	private String pall;

	@Column(name = "PALN", length = 9)
	private String paln;

	@Column(name = "PALP", length = 9)
	private String palp;

	@Column(name = "PAXB", length = 3)
	private String paxb;

	@Column(name = "PAXE", length = 3)
	private String paxe;

	@Column(name = "PFNO", length = 9)
	private String pfno;

	@Column(name = "PGD1", length = 10)
	private String pgd1;

	@Column(name = "PGD2", length = 10)
	private String pgd2;

	@Column(name = "PRMC", length = 2)
	private String prmc;

	@Column(name = "PRMK", length = 200)
	private String prmk;

	@Column(name = "PRMS", length = 1)
	private String prms;

	@Column(name = "PRMU", length = 32)
	private String prmu;

	@Column(name = "RDPD", length = 16)
	private String rdpd;

	@Column(name = "REGI", length = 12)
	private String regi;

	@Column(name = "RESC", length = 1)
	private String resc;

	@Column(name = "RESP", length = 12)
	private String resp;

	@Column(name = "ROTA", length = 1)
	private String rota;

	@Column(name = "ROUT", length = 14)
	private String rout;

	@Column(name = "RREQ", length = 1)
	private String rreq;

	@Column(name = "RRMK", length = 200)
	private String rrmk;

	@Column(name = "RSTA", length = 14)
	private String rsta;

	@Column(name = "RSTD", length = 14)
	private String rstd;

	@Column(name = "RTOS", length = 14)
	private String rtos;

	@Column(name = "RTOT", length = 14)
	private String rtot;

	@Column(name = "RTOW", length = 1)
	private String rtow;

	@Column(name = "SACG", length = 1)
	private String sacg;

	@Column(name = "SACZ", length = 1)
	private String sacz;

	@Column(name = "SADI", length = 1)
	private String sadi;

	@Column(name = "SAEG", length = 1)
	private String saeg;

	@Column(name = "SAEZ", length = 1)
	private String saez;

	@Column(name = "SAIB", length = 1)
	private String saib;

	@Column(name = "SALD", length = 1)
	private String sald;

	@Column(name = "SAOB", length = 1)
	private String saob;

	@Column(name = "SAPE", length = 1)
	private String sape;

	@Column(name = "SARZ", length = 1)
	private String sarz;

	@Column(name = "SASA", length = 1)
	private String sasa;

	@Column(name = "SASR", length = 1)
	private String sasr;

	@Column(name = "SATO", length = 1)
	private String sato;

	@Column(name = "SBGD", length = 14)
	private String sbgd;

	@Column(name = "SCTO", length = 1)
	private String scto;

	@Column(name = "SECZ", length = 1)
	private String secz;

	@Column(name = "SEDI", length = 1)
	private String sedi;

	@Column(name = "SEEZ", length = 1)
	private String seez;

	@Column(name = "SEIB", length = 1)
	private String seib;

	@Column(name = "SELD", length = 1)
	private String seld;

	@Column(name = "SEOB", length = 1)
	private String seob;

	@Column(name = "SERZ", length = 1)
	private String serz;

	@Column(name = "SESB", length = 1)
	private String sesb;

	@Column(name = "SETO", length = 1)
	private String seto;

	@Column(name = "SETT", length = 1)
	private String sett;

	@Column(name = "SIBT", length = 14)
	private String sibt;

	@Column(name = "SIDR", length = 12)
	private String sidr;

	@Column(name = "SLDT", length = 14)
	private String sldt;

	@Column(name = "SOBT", length = 14)
	private String sobt;

	@Column(name = "SRTO", length = 1)
	private String srto;

	@Column(name = "STAA", length = 4)
	private String staa;

	@Column(name = "STAD", length = 4)
	private String stad;

	@Column(name = "STAR", length = 12)
	private String star;

	@Column(name = "STCF", length = 1)
	private String stcf;

	@Column(name = "STCN", length = 1)
	private String stcn;

	@Column(name = "STLA", length = 1)
	private String stla;

	@Column(name = "STOB", length = 1)
	private String stob;

	@Column(name = "STOT", length = 14)
	private String stot;

	@Column(name = "STSA", length = 1)
	private String stsa;

	@Column(name = "STTO", length = 1)
	private String stto;

	@Column(name = "SUTI", length = 4)
	private String suti;

	@Column(name = "T1BA", length = 14)
	private String t1ba;

	@Column(name = "T1BS", length = 14)
	private String t1bs;

	@Column(name = "T1EA", length = 14)
	private String t1ea;

	@Column(name = "T1ES", length = 14)
	private String t1es;

	@Column(name = "T2BA", length = 14)
	private String t2ba;

	@Column(name = "T2BS", length = 14)
	private String t2bs;

	@Column(name = "T2EA", length = 14)
	private String t2ea;

	@Column(name = "T2ES", length = 14)
	private String t2es;

	@Column(name = "TBE1", length = 14)
	private String tbe1;

	@Column(name = "TBE2", length = 14)
	private String tbe2;

	@Column(name = "TBS1", length = 14)
	private String tbs1;

	@Column(name = "TBS2", length = 14)
	private String tbs2;

	@Column(name = "TLDA", length = 14)
	private String tlda;

	@Column(name = "TLDT", length = 14)
	private String tldt;

	@Column(name = "TLDU", length = 14)
	private String tldu;

	@Column(name = "TOB1", length = 14)
	private String tob1;

	@Column(name = "TOB2", length = 14)
	private String tob2;

	@Column(name = "TOB3", length = 14)
	private String tob3;

	@Column(name = "TOBC")
	private BigDecimal tobc;

	@Column(name = "TOBI", length = 12)
	private String tobi;

	@Column(name = "TOBM")
	private BigDecimal tobm;

	@Column(name = "TOBS", length = 14)
	private String tobs;

	@Column(name = "TOBT", length = 14)
	private String tobt;

	@Column(name = "TOBU", length = 14)
	private String tobu;

	@Column(name = "TRB1", length = 5)
	private String trb1;

	@Column(name = "TRB2", length = 5)
	private String trb2;

	@Column(name = "TRMA", length = 10)
	private String trma;

	@Column(name = "TRMD", length = 10)
	private String trmd;

	@Column(name = "TSAA", length = 14)
	private String tsaa;

	@Column(name = "TSAS", length = 14)
	private String tsas;

	@Column(name = "TSAT", length = 14)
	private String tsat;

	@Column(name = "TSAU", length = 14)
	private String tsau;

	@Column(name = "TTB1", length = 1)
	private String ttb1;

	@Column(name = "TTB2", length = 1)
	private String ttb2;

	@Column(name = "TTOA", length = 14)
	private String ttoa;

	@Column(name = "TTOS", length = 14)
	private String ttos;

	@Column(name = "TTOT", length = 14)
	private String ttot;

	@Column(name = "TTOU", length = 14)
	private String ttou;

	@Column(name = "VCNT")
	private BigDecimal vcnt;

	@Column(name = "VSUP", length = 14)
	private String vsup;

	@Column(name = "PTOB", length = 5)
	private String ptob;

	@Column(name = "PFRB", length = 5)
	private String pfrb;

	@Column(name = "SFIN", length = 14)
	private String sfin;

	@Column(name = "CTR1", length = 32)
	private String ctr1;

	@Column(name = "CTR2", length = 32)
	private String ctr2;

	@Column(name = "ISTA", length = 8)
	private String ista;

	@Column(name = "EOBP", length = 14)
	private String eobp;

	@Column(name = "EOBW", length = 14)
	private String eobw;

	@Column(name = "CXUD")
	private BigDecimal cxud;

	@Column(name = "CXBG")
	private BigDecimal cxbg;

	@Column(name = "CXPX")
	private BigDecimal cxpx;

	@Column(name = "ROID", length = 40)
	private String roid;

	@Column(name = "TLDR", length = 14)
	private String tldr;

	@Column(name = "TLDD", length = 14)
	private String tldd;

	@Column(name = "SXUD")
	private BigDecimal sxud;

	@Column(name = "SXPX")
	private BigDecimal sxpx;

	@Column(name = "SXBG")
	private BigDecimal sxbg;

	@Column(name = "SOOB", length = 4)
	private String soob;

	@Column(name = "SNDT", length = 14)
	private String sndt;

	@Column(name = "SEET", length = 1)
	private String seet;

	@Column(name = "SAST", length = 1)
	private String sast;

	@Column(name = "SAET", length = 1)
	private String saet;

	@Column(name = "MAST", length = 1)
	private String mast;

	@Column(name = "MAOC", length = 1)
	private String maoc;

	@Column(name = "MAGT", length = 5)
	private String magt;

	@Column(name = "MACL", length = 1)
	private String macl;

	@Column(name = "GDST", length = 4)
	private String gdst;

	@Column(name = "GAST", length = 4)
	private String gast;

	@Column(name = "FU05", length = 8)
	private String fu05;

	@Column(name = "FU04", length = 8)
	private String fu04;

	@Column(name = "FU03", length = 8)
	private String fu03;

	@Column(name = "FU02", length = 8)
	private String fu02;

	@Column(name = "FU01", length = 8)
	private String fu01;

	@Column(name = "FDUR", length = 4)
	private String fdur;

	@Column(name = "EOBD", length = 14)
	private String eobd;

	@Column(name = "EIBD", length = 14)
	private String eibd;

	@Column(name = "EETT", length = 14)
	private String eett;

	@Column(name = "EETD", length = 14)
	private String eetd;

	@Column(name = "DWAU")
	private BigDecimal dwau;

	@Column(name = "DL4U")
	private BigDecimal dl4u;

	@Column(name = "DL1U")
	private BigDecimal dl1u;

	@Column(name = "CXUT", length = 4)
	private String cxut;

	@Column(name = "CXUA", length = 1)
	private String cxua;

	@Column(name = "CXTA", length = 1)
	private String cxta;

	@Column(name = "CXPT", length = 4)
	private String cxpt;

	@Column(name = "CXPA", length = 1)
	private String cxpa;

	@Column(name = "CXBT", length = 4)
	private String cxbt;

	@Column(name = "CXBA", length = 1)
	private String cxba;

	@Column(name = "CTOD", length = 14)
	private String ctod;

	@Column(name = "ATOR", length = 14)
	private String ator;

	@Column(name = "ATOD", length = 14)
	private String atod;

	@Column(name = "ASTT", length = 14)
	private String astt;

	@Column(name = "ASTN", length = 14)
	private String astn;

	@Column(name = "ASTD", length = 14)
	private String astd;

	@Column(name = "AOOB", length = 4)
	private String aoob;

	@Column(name = "AOBD", length = 14)
	private String aobd;

	@Column(name = "ALDD", length = 14)
	private String aldd;

	@Column(name = "AIBD", length = 14)
	private String aibd;

	@Column(name = "AETT", length = 14)
	private String aett;

	@Column(name = "AETD", length = 14)
	private String aetd;

	@Column(name = "AEBT", length = 14)
	private String aebt;

	@Column(name = "RFCR", length = 5)
	private String rfcr;

	@Column(name = "RTCR", length = 5)
	private String rtcr;

	@Column(name = "ADE3", length = 3)
	private String ade3;

	@Column(name = "ADE4", length = 4)
	private String ade4;

	@Column(name = "NOSI", length = 3)
	private String nosi;

	@Column(name = "GOAF", length = 1)
	private String goaf;

	@Column(name = "RDTO", length = 1)
	private String rdto;

	@Transient
	private String counter;
	
	@Transient
	@JacksonXmlElementWrapper(localName = "lstFidsCcatab")
	@JacksonXmlProperty(localName = "fidsCcatab")
	private List<FidsCcatab> lstFidsCcatab;

	@Transient
	private String route;

	@Transient
	@JacksonXmlElementWrapper(localName = "lstRouting")
	@JacksonXmlProperty(localName = "routing")
	private List<Routing> lstRouting;

	@Data
	public static class Routing {
        private String iata;
        private String icao;
        private String action;
    }

	@Transient
	private List<String> fieldsNotNull = new ArrayList<String>();
	
	@Transient
	private String action;

	@Transient
	private String gateAction;

	@Transient
	private String beltAction;

	@Transient
	private String positionAction;

	@Transient
	private String toid;
	
	/* public void setRemp(String remp) {
		if(remp!=null) {
			if(remp.equalsIgnoreCase("BRD")) {
				this.remp = "BOA";
			}else if(remp.equalsIgnoreCase("2ND")) {
				this.remp = "FNC";
			}else {
				this.remp = remp;
			}
		}else {
			this.remp = remp;
		}
	} */
}