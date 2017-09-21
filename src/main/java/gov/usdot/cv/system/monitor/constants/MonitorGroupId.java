package gov.usdot.cv.system.monitor.constants;

public enum MonitorGroupId {
	
	SECURE_IPV4(0x10b01000, IpType.IPv4, true),
	NON_SECURE_IPV4(0x10b02000, IpType.IPv4, false),
	SECURE_IPV6(0x10b03000, IpType.IPv6, true),
	NON_SECURE_IPV6(0x10b04000, IpType.IPv6, false),
	UNKNOWN(-1, IpType.UNKNOWN, false);
	
	private int groupId;
	private IpType ipType;
	private boolean secure;
	
	private MonitorGroupId(int groupId, IpType ipType, boolean secure) {
		this.groupId = groupId;
		this.ipType = ipType;
		this.secure = secure;
	}

	public static MonitorGroupId getByGroupId(int groupId) {
		for(MonitorGroupId monitorGroupId : values()) {
			if(monitorGroupId.groupId == groupId) {
				return monitorGroupId;
			}
		}
		
		return UNKNOWN;
	}
	
	public static MonitorGroupId getByIpTypeAndSecurity(IpType ipType, boolean secure) {

		for(MonitorGroupId groupId : values()) {
			if(groupId.ipType == ipType && groupId.secure == secure) {
				return groupId;
			}
		}
		
		return UNKNOWN;
	}
	
	public int getId() {
		return groupId;
	}
	
	@Override
	public String toString() {
		return String.format("%s_%s", ipType.toString(), (secure)?("secure"):("non-secure"));
	}
}
