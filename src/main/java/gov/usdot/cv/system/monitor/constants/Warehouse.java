package gov.usdot.cv.system.monitor.constants;

public enum Warehouse {
	
	SDW("SDW 2.3", "SDW"),
	SDPC("SDPC 2.3", "SDPC"),
	SUBSCRIPTION("SUBSCRIPTION", "SUB"),
	ALL("ALL", "ALL"),
	UNKNOWN("unknown", "UNK");
	
	private String id;
	private String shortId;
	
	private Warehouse(String id, String shortId) {
		this.id = id;
		this.shortId = shortId;
	}
	
	public static Warehouse getByValue(String value) {
		for(Warehouse warehouse : values()) {
			if(value.equalsIgnoreCase(warehouse.id)) {
				return warehouse;
			}
		}
		
		return UNKNOWN;
	}
	
	public static Warehouse getByShortValue(String value) {
		for(Warehouse warehouse : values()) {
			if(value.equalsIgnoreCase(warehouse.shortId)) {
				return warehouse;
			}
		}
		
		return UNKNOWN;
	}	
	
	@Override
	public String toString() {
		return id;
	}
	
	public String getShortValue() {
		return shortId;
	}
	
	public static Warehouse[] knownValues() {
		Warehouse[] knownValues = new Warehouse[values().length-3];
		
		int index = 0;
		for(Warehouse warehouse : values()) {
			if(warehouse != UNKNOWN && warehouse != ALL && warehouse != SUBSCRIPTION) {
				knownValues[index] = warehouse;
				index++;
			}
		}
		
		return knownValues;
	}
}
