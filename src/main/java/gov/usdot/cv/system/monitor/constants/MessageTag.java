package gov.usdot.cv.system.monitor.constants;

public enum MessageTag {
	
	CONNECTED("CONNECTED:"),
	START("START:"),
	STOP("STOP:"),
	ERROR("ERROR:"),
	NO_TAG("");
	
	private String tag;
	
	private MessageTag(String tag) {
		this.tag = tag;
	}
	
	public static MessageTag getFromMessage(String message) {
		for(MessageTag messageTag : values()) {
			if(message.startsWith(messageTag.tag)) {
				return messageTag;
			}
		}
		
		return NO_TAG;
	}
	public String getTag() {
		return tag;
	}
	
	public String toString() {
		return tag;
	}
}
