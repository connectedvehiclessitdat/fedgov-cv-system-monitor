package gov.usdot.cv.system.monitor.uptime;

import gov.usdot.cv.system.monitor.constants.IpType;
import gov.usdot.cv.system.monitor.constants.MonitorDialogId;
import gov.usdot.cv.system.monitor.constants.Warehouse;

/**
 * The Asset that maps to our H2 database table.
 */
public class Asset {

	private IpType ipType;
	private boolean secure;
	private MonitorDialogId dialogId;
	private Warehouse target;
	private AssetState state;
	private long timestamp;
	
	public Asset() {
		super();
	}

	public IpType getIpType() {
		return ipType;
	}

	public void setIpType(IpType ipType) {
		this.ipType = ipType;
	}

	public boolean isSecure() {
		return secure;
	}

	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	public MonitorDialogId getDialogId() {
		return dialogId;
	}

	public void setDialogId(MonitorDialogId dialogId) {
		this.dialogId = dialogId;
	}

	public Warehouse getTarget() {
		return target;
	}

	public void setTarget(Warehouse target) {
		this.target = target;
	}
	
	public AssetState getState() {
		return state;
	}
	
	public void setState(AssetState state) {
		this.state = state;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	@Override
	public String toString() {
		return "Asset [ipType=" + ipType + ", secure=" + secure +  ", dialogId=" + dialogId +
				", target=" + target + ", timestamp=" + timestamp + ", state=" + state + "]";
	}

	public enum AssetState {
		UP ("UP"),
	    DOWN ("DOWN"),
	    UNKNOWN ("UNKNOWN");

	    private final String state;       

	    private AssetState(String state) {
	    	this.state = state;
	    }
	    
	    public String toString() {
	    	return state;
	    }
	}
}
