package gov.usdot.cv.system.monitor;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;

import gov.usdot.cv.system.monitor.SystemMonitorConfig.MonitorConfig;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.SenderEntry;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.Senders;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.UptimeConfig;
import gov.usdot.cv.system.monitor.constants.EmailConfiguration;
import gov.usdot.cv.system.monitor.constants.MessageTag;
import gov.usdot.cv.system.monitor.constants.MonitorDialogId;
import gov.usdot.cv.system.monitor.constants.MonitorGroupId;
import gov.usdot.cv.system.monitor.constants.Warehouse;
import gov.usdot.cv.system.monitor.uptime.Asset.AssetState;
import gov.usdot.cv.system.monitor.util.DateUtil;
import gov.usdot.cv.system.monitor.util.JsonUtil;
import gov.usdot.cv.whtools.client.config.WarehouseConfig;
import gov.usdot.cv.whtools.client.handler.ResponseHandler;

public class SystemMonitorResponseHandler  extends ResponseHandler {
	
	private static final Logger logger = Logger.getLogger(SystemMonitorResponseHandler.class);
	
	private int failureTolerance;
	//private FailureTracker failureTracker = new FailureTracker();
	private String[] alertEmails;
	private String[] recoveryEmails;

	private String systemName;
	private Warehouse batchWarehouse;
	private MonitorDialogId batchMonitorDialogId;
	private Map<String, Date> batchOfLatestMessages;

	private Map<Warehouse, Map<String, SenderHistory>> senderHistoriesByWarehouseAndKey = new HashMap<Warehouse, Map<String, SenderHistory>>();
	
	private boolean connected = false;
	
	public SystemMonitorResponseHandler(WarehouseConfig wsConfig, MonitorConfig monitorConfig,
											Senders senders, UptimeConfig uptimeConfig) {
		super(wsConfig);

		this.systemName = monitorConfig.systemName;
				
		this.failureTolerance = monitorConfig.failureTolerance;
		this.alertEmails = monitorConfig.alertEmails;
		this.recoveryEmails = monitorConfig.recoveryEmails;
		
		// Create an entry for each warehouse
		for(Warehouse warehouse : Warehouse.knownValues()) {
			senderHistoriesByWarehouseAndKey.put(warehouse, new HashMap<String, SenderHistory>());
		}
		
		// Populate the sender histories
		for(SenderEntry senderEntry : senders.entries) {
			if(senderEntry.targetWarehouse == Warehouse.ALL) {
				// Create a sender history for all warehouses
				for(Warehouse warehouse : Warehouse.knownValues()) {
					SenderHistory senderHistory = new SenderHistory(senderEntry, warehouse, uptimeConfig);
					senderHistoriesByWarehouseAndKey.get(warehouse).put(senderHistory.getKey(), senderHistory);
					
					// Assume the initial asset state to be UP
					senderHistory.updateAssetState(AssetState.UP);
				}
			}
			else {
				// Create a single sender history for the specified warehouse
				SenderHistory senderHistory = new SenderHistory(senderEntry, senderEntry.targetWarehouse, uptimeConfig);
				senderHistoriesByWarehouseAndKey.get(senderEntry.targetWarehouse).put(senderHistory.getKey(), senderHistory);

				// Assume the initial asset state to be UP
				senderHistory.updateAssetState(AssetState.UP);
			}
		}
		logger.debug(String.format("senderHistoriesByWarehouseAndKey = %s", Arrays.toString(senderHistoriesByWarehouseAndKey.entrySet().toArray())));
	}

	public void handleMessage(String message) {
		logger.debug(String.format("Monitor received message %s", message));
		
		super.handleMessage(message);
		
		performMonitorLogic(message);
	}
	
	private void performMonitorLogic(String message) {
		MessageTag messageTag = MessageTag.getFromMessage(message);
		
		String jsonMessage = message.substring(messageTag.toString().length());
		
		switch(messageTag) {
			case ERROR:
				logger.error(message);
				break;
			case CONNECTED:
				connected = true;
				break;
			case START:
				// Start a new batch of messages
				try {
					JsonNode rootNode = JsonUtil.mapper.readTree(jsonMessage);

					String warehouse = rootNode.get("systemQueryName").asText();
					batchWarehouse = Warehouse.getByValue(warehouse);
					logger.debug(String.format("Message batch is for warehouse %s", batchWarehouse));
					
					long dialogId = rootNode.get("dialogID").asLong();
					batchMonitorDialogId = MonitorDialogId.getByDialogId(dialogId);
					logger.debug(String.format("Message batch has dialog Id of %s", batchMonitorDialogId.toString()));
				} catch (IOException e) {
					logger.error(
							String.format("Failed to parse message [%s]", jsonMessage), e);
					batchWarehouse = Warehouse.UNKNOWN;
					batchMonitorDialogId = MonitorDialogId.UNKNOWN;
				}
				batchOfLatestMessages = new HashMap<String, Date>();
				break;
			case STOP:
				if(logger.isDebugEnabled()) {
					StringBuilder debugList = new StringBuilder("Batch Of Latest Messages for ")
														.append(batchWarehouse)
														.append(" contains:")
														.append("\n");
					for(Entry<String, Date> senderKeyAndDate : batchOfLatestMessages.entrySet()) {
						debugList.append(senderKeyAndDate.getKey())
								 .append(" @ ")
								 .append(DateUtil.format(senderKeyAndDate.getValue()))
								 .append("\n");
					}
					logger.debug(debugList.toString());
				}
				
				List<SenderHistory> processedSenders = new ArrayList<SenderHistory>();
				// Process the batch of messages
				for(Entry<String, Date> senderKeyAndDate : batchOfLatestMessages.entrySet()) {
					String senderKey = senderKeyAndDate.getKey();
					Date messageCreatedAt = senderKeyAndDate.getValue();
							
					SenderHistory senderHistory = senderHistoriesByWarehouseAndKey.get(batchWarehouse).get(senderKey);
					if(senderHistory != null) {
						if(senderHistory.isNewMessageOrWithinTimeThreshold(messageCreatedAt)) {
							// Save the new message date since it was not a failure
							senderHistory.recordMessageDate(messageCreatedAt);
							
							// If we have sent an alert, send out that we have now recovered.
							if(senderHistory.hasAlerted()) {
								performReocvery(senderHistory, batchWarehouse);
							}
							senderHistory.clearFailure();
						}
						else {
							// The message was determined not to be a new message or it has been long enough to expect a new message,
							// see if we have failed enough times
							senderHistory.recordFailure(messageCreatedAt);
							if(senderHistory.getFailureCount() >= failureTolerance) {
								logger.debug(String.format("Sender %s has failed more times than tolerable.", senderKey));
								performAlert(senderHistory, batchWarehouse);
							}
						}
						
						processedSenders.add(senderHistory);
					}
					else {
						logger.warn(String.format("Message [%s] received for unknown sender [%s]", jsonMessage, senderKey));
					}
				}
				
				// Ensure we received messages for all senders
				checkUnprocessedSenders(batchWarehouse, batchMonitorDialogId, processedSenders);
				
				break;
			case NO_TAG:
			default:
				// Save the latest messages for each sender in a batch to be processed when all messages arrive
				try {
					JsonNode rootNode = JsonUtil.mapper.readTree(jsonMessage);
					
					long dialogId = rootNode.get("dialogId").asLong();
					MonitorDialogId monitorDialogId = MonitorDialogId.getByDialogId(dialogId);
					logger.debug(String.format("Message has dialog Id %s", monitorDialogId.toString()));
					
					int groupIdInt = rootNode.get("groupId").asInt();
					MonitorGroupId groupId = MonitorGroupId.getByGroupId(groupIdInt);
					logger.debug(String.format("Message has group Id %s", groupId.toString()));
					
					// Get the key of sender this message is related to
					String senderKey = SenderEntry.buildSenderKey(groupId, monitorDialogId);
					logger.debug(String.format("Sender key %s built for message.", senderKey));
					
					// Get the message creation timestamp
					String messageCreatedAtString = rootNode.get("createdAt").get("$date").asText();
					try {
						Date messageCreatedAt = DateUtil.parse(messageCreatedAtString);
						
						if(!batchOfLatestMessages.containsKey(senderKey)) {
							// First message for this sender, save it
							batchOfLatestMessages.put(senderKey, messageCreatedAt);
						}
						else {
							// Compare the saved message date to this one and keep whichever is later in time
							if(messageCreatedAt.after(batchOfLatestMessages.get(senderKey))) {
								batchOfLatestMessages.put(senderKey, messageCreatedAt);
							}
						}
					} catch (ParseException e) {
						logger.error(
								String.format("Failed to parse createdAt date [%s] from message [%s]",
													messageCreatedAtString, jsonMessage), e);
					}
				} catch (JsonProcessingException e) {
					logger.error(e);
				} catch (IOException e) {
					logger.error(e);
				}
		}
	}
	
	private void checkUnprocessedSenders(Warehouse warehouse, MonitorDialogId monitorDialogId, List<SenderHistory> processedSenders) {
		for(SenderHistory senderHistory : senderHistoriesByWarehouseAndKey.get(warehouse).values()) {
			if(!processedSenders.remove(senderHistory) && monitorDialogId == senderHistory.getSender().monitorDialogId) {
				// This sender was not processed in this batch and is the same dialog Id as the batch
				
				// If the sender has never received a message before, use the current date as the failure date				
				Date failureDate = 
						(senderHistory.getLastMessageCreatedAt().equals(DateUtil.DATE_NOT_SET)) ?
								(DateUtil.currentTime()) :
								(senderHistory.getLastMessageCreatedAt());
		
				senderHistory.recordFailure(failureDate);
				logger.debug(
						String.format("Sender\n%s\ndid not appear in latest batch of messages for warehouse %s. Incremented failure count to %d.",
								senderHistory.getSender().toString(),
								warehouse,
								senderHistory.getFailureCount()));
				
				// See if we are past the point of failure
				if(senderHistory.getFailureCount() >= failureTolerance) {
					logger.debug("Sender has failed to receive any messages beyond the point of failure.");
					performAlert(senderHistory, warehouse);
				}
			}
		}
	}
	
	private void performAlert(SenderHistory senderHistory, Warehouse warehouse) {
		// Only alert if it hasn't been 24 hours since the first failure was recorded
		if(senderHistory.getAlertSentDate().equals(DateUtil.DATE_NOT_SET) ||
			(DateUtil.currentTime().getTime() - senderHistory.getAlertSentDate().getTime()) >= DateUtil.ONE_DAY) {

			// Update the asset state to down
			logger.debug("Updating asset state to down.");
			senderHistory.updateAssetState(AssetState.DOWN);
			
			try {
				logger.debug("Generating alert emails.");
				EmailSender.sendEmail(alertEmails, 
											EmailConfiguration.ALERT_SUBJECT,
											systemName,
											EmailConfiguration.buildSenderAlertMessage(senderHistory, warehouse));
				senderHistory.recordAlert(DateUtil.currentTime());
			} catch (Exception e) {
				logger.error("Failed to send alert emails.", e);
			}
		}
	}
	
	private void performReocvery(SenderHistory senderHistory, Warehouse warehouse) {

		// Update the asset state to up
		logger.debug("Updating asset state to up.");
		senderHistory.updateAssetState(AssetState.UP);
		
		try {
			String duration = senderHistory.getFirstFailureDate().equals(DateUtil.DATE_NOT_SET) ?
												"unknown" :
												DateUtil.duration(senderHistory.getFirstFailureDate(), DateUtil.currentTime());
			String recoveryMessage =
					EmailConfiguration.buildSenderRecoveryMessage(senderHistory, warehouse, duration);
			logger.debug(String.format("Generating recovery emails: %s", recoveryMessage));
			EmailSender.sendEmail(recoveryEmails, 
										EmailConfiguration.RECOVERY_SUBJECT,
										systemName,
										recoveryMessage);
			senderHistory.clearAlert();
		} catch (Exception e) {
			logger.error("Failed to send recovery emails.", e);
		}
	}
	
	public boolean isConnected() {	
		return connected;
	}
}