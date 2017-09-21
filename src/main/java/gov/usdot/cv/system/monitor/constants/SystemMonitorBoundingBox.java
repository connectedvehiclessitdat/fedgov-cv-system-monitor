package gov.usdot.cv.system.monitor.constants;

public enum SystemMonitorBoundingBox {

	NW_LAT(28.049),
	NW_LON(-81.653),
	CENTER_LAT(28.048),
	CENTER_LON(-81.652),
	SE_LAT(28.047),
	SE_LON(-81.651);
	
	double value;
	
	private SystemMonitorBoundingBox(double value) {
		this.value = value;
	}
	
	public String toString() {
		return Double.toString(value);
	}
}
