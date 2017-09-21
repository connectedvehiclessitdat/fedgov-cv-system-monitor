package gov.usdot.cv.system.monitor;

import java.sql.SQLException;
import java.util.Date;

import org.apache.log4j.Logger;

import gov.usdot.cv.system.monitor.SystemMonitorConfig.SenderEntry;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.UptimeConfig;
import gov.usdot.cv.system.monitor.constants.MonitorGroupId;
import gov.usdot.cv.system.monitor.constants.Warehouse;
import gov.usdot.cv.system.monitor.uptime.Asset.AssetState;
import gov.usdot.cv.system.monitor.uptime.Asset;
import gov.usdot.cv.system.monitor.uptime.AssetUpdater;
import gov.usdot.cv.system.monitor.util.DateUtil;

public class SenderHistory extends FailureTracker {

	private static final Logger logger = Logger.getLogger(SenderHistory.class);
	
	private final SenderEntry sender;
	private final String key;
	private final Warehouse target;
	private Date lastMessageCreatedAt = DateUtil.DATE_NOT_SET;
	
	private AssetUpdater assetUpdater;
	
	public SenderHistory(SenderEntry senderEntry, Warehouse target, UptimeConfig uptimeConfig) {
		this.sender = senderEntry;
		this.key = SenderEntry.buildSenderKey(
									MonitorGroupId.getByIpTypeAndSecurity(senderEntry.ipType, senderEntry.secure),
									senderEntry.monitorDialogId);
		this.target = target;

		this.assetUpdater = new AssetUpdater(uptimeConfig);
		try {
			assetUpdater.start();
		} catch (SQLException e) {
			logger.error("Failed to start Asset Updater.", e);
		}
	}
	
	/**
	 * 
	 * @param messageCreatedAt - The creation time the message to be compared against the current saved time
	 * @return true if the date of the message is newer than the last message or if it has not been enough
	 *          time since the last message was received to have expected a new message.
	 *         false if the message is not new and enough time has passed to have received a new message.
	 */
	public boolean isNewMessageOrWithinTimeThreshold(Date messageCreatedAt) {
		
		if(lastMessageCreatedAt.equals(DateUtil.DATE_NOT_SET)) {
			// This is the first time we've received a message for this sender
			logger.debug("This is the first message received for this sender");
		}
		else if (messageCreatedAt.after(lastMessageCreatedAt)){
			// New message received
			logger.debug("Message successfully received.");
		}
		else if(messageCreatedAt.equals(lastMessageCreatedAt.getTime())) {
			// Have yet to receive a new message
			logger.debug("New message creation time is the same as last recorded message creation time. No new message received");
			
			// Make sure that it has been enough time based on the sender's frequency
			// to even expect a new message
			long timeSinceLastMessage = (DateUtil.currentTime()).getTime() - messageCreatedAt.getTime();
			if(timeSinceLastMessage > sender.frequency) {
				// Should of seen a message by now, record the failure
				logger.debug(
						String.format("It has been %d milliseconds since the last message was recorded. " +
									  "This is more than the expected time", timeSinceLastMessage));					
				return false;
			}
		}
		else {
			// The current message is timestamped before the last recorded message for this sender
			// Something isn't right...
			logger.warn(
					String.format(
						"Current message has timestamp [%s] before last recorded message timestamp [%s] for sender [%s].",
						DateUtil.format(messageCreatedAt),
						DateUtil.format(lastMessageCreatedAt),
						key
					));
		}
		
		return true;
		
	}
	
	public void recordMessageDate(Date messageCreatedAt) {
		lastMessageCreatedAt = messageCreatedAt;
	}
	
	public SenderEntry getSender() {
		return sender;
	}
	
	public String getKey() {
		return key;
	}
	
	public Warehouse getTarget() {
		return target;
	}
	
	public Date getLastMessageCreatedAt() {
		return lastMessageCreatedAt;
	}
	
	public void updateAssetState(AssetState state) {
		Asset asset = new Asset();
		
		asset.setIpType(sender.ipType);
		asset.setSecure(sender.secure);
		asset.setDialogId(sender.monitorDialogId);
		asset.setTarget(target);
		asset.setState(state);
		asset.setTimestamp(
				(lastMessageCreatedAt.equals(DateUtil.DATE_NOT_SET)) ?
						(DateUtil.currentTime().getTime()) :
						(lastMessageCreatedAt.getTime()));
		
		assetUpdater.updateAsset(asset);
	}
}
