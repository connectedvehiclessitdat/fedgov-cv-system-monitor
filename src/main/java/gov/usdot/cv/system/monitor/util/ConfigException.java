package gov.usdot.cv.system.monitor.util;

public class ConfigException extends Exception {

	private static final long serialVersionUID = 2297032900809034033L;

	public ConfigException(String message) {
		super(message);
    }
	
	public ConfigException(Throwable cause) {
		super(cause);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
