package gov.usdot.cv.system.monitor.constants;

public enum IpType {
	
	IPv4("IPv4"),
	IPv6("IPv6"),
	UNKNOWN("unknown");
	
	private String type;
	
	private IpType(String type) {
		this.type = type;
	}
	
	public static IpType getByValue(String value) {
		for(IpType ipType : values()) {
			if(value.equalsIgnoreCase(ipType.type)) {
				return ipType;
			}
		}
		
		return UNKNOWN;
	}
	
	@Override
	public String toString() {
		return type;
	}
}
