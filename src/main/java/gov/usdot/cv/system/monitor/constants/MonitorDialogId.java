package gov.usdot.cv.system.monitor.constants;

import gov.usdot.asn1.generated.j2735.semi.SemiDialogID;

public enum MonitorDialogId {
	
	ASD("asd", SemiDialogID.advSitDataDep),
	ISD("isd", SemiDialogID.intersectionSitDataDep),
	VSD("vsd", SemiDialogID.vehSitData),
	SUB("sub", SemiDialogID.dataSubscription),
	UNKNOWN("unknown", null);
	
	private String type;
	private SemiDialogID dialogId;
	
	private MonitorDialogId(String type, SemiDialogID dialogId) {
		this.type = type;
		this.dialogId = dialogId;
	}
	
	public static MonitorDialogId getByType(String type) {
		for(MonitorDialogId monitorDialogId : values()) {
			if(type.equalsIgnoreCase(monitorDialogId.type)) {
				return monitorDialogId;
			}
		}
		
		return UNKNOWN;
	}

	public static MonitorDialogId getByDialogId(long dialogId) {
		for(MonitorDialogId monitorDialogId : values()) {
			if(monitorDialogId != UNKNOWN) {
				if(dialogId == monitorDialogId.dialogId.longValue()) {
					return monitorDialogId;
				}
			}
		}
		
		return UNKNOWN;
	}
	
	public String getType() {
		return type;
	}
	
	public SemiDialogID getDialogId() {
		return dialogId;
	}
	
	@Override
	public String toString() {
		return String.format("%s(%d)", type, (dialogId == null)?(0):(dialogId.longValue()));
	}
}
