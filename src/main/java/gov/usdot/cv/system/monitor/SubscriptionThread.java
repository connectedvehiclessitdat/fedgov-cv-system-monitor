package gov.usdot.cv.system.monitor;

import java.sql.SQLException;

import org.apache.log4j.Logger;

import gov.usdot.cv.common.dialog.DPCSubscription;
import gov.usdot.cv.common.dialog.DPCSubscriptionException;
import gov.usdot.cv.system.monitor.MonitorThread.Heartbeat;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.MonitorConfig;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.SubscriptionEntry;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.UptimeConfig;
import gov.usdot.cv.system.monitor.constants.EmailConfiguration;
import gov.usdot.cv.system.monitor.constants.MonitorDialogId;
import gov.usdot.cv.system.monitor.constants.Warehouse;
import gov.usdot.cv.system.monitor.uptime.Asset;
import gov.usdot.cv.system.monitor.uptime.Asset.AssetState;
import gov.usdot.cv.system.monitor.uptime.AssetUpdater;
import gov.usdot.cv.system.monitor.util.DateUtil;

public class SubscriptionThread implements Runnable {

	private static Logger logger = Logger.getLogger(SubscriptionThread.class);

	private String systemName;
	private SubscriptionEntry subscriptionEntry;
	private long frequency;
	private long interval;
	private int failureTolerance;
	private FailureTracker failureTracker = new FailureTracker();
	private String[] alertEmails;
	private String[] recoveryEmails;
	
	private DPCSubscription subscription;

	private AssetUpdater assetUpdater;
	
	public SubscriptionThread(SubscriptionEntry subscriptionEntry, MonitorConfig monitorConfig,
								Heartbeat heartbeat, UptimeConfig uptimeConfig)
										throws DPCSubscriptionException {
		this.systemName = monitorConfig.systemName;
		this.subscriptionEntry = subscriptionEntry;
		this.frequency = monitorConfig.queryFrequency;
		this.interval = subscriptionEntry.interval;
		this.failureTolerance = monitorConfig.failureTolerance;
		this.alertEmails = monitorConfig.alertEmails;
		this.recoveryEmails = monitorConfig.recoveryEmails;
		
		// Register this thread with the heartbeat
		heartbeat.registerThread(String.format("%s %s Subscription Thread", 
										subscriptionEntry.ipType.toString(),
										(subscriptionEntry.secure)?("secure"):("non-secure")),
								Thread.currentThread());
		
		subscription = new DPCSubscription(subscriptionEntry.subscription);
		try {
			subscription.setSecureEnabled(subscriptionEntry.secure);
		} catch (Exception e) {
			logger.error("Failed to turn on subscription security.", e);
		}
		
		this.assetUpdater = new AssetUpdater(uptimeConfig);
		try {
			assetUpdater.start();

			// Assume the initial asset state to be UP
			updateAssetState(AssetState.UP);
		} catch (SQLException e) {
			logger.error("Failed to start Asset Updater.", e);
		}	
	}
	
	public void run() {
		logger.debug(String.format("Subscription thread\n%s\nis now running", toString()));

		while(true) {
			long nextIterationTime = System.currentTimeMillis() + frequency;
			
			boolean requestSuccess = false;
			boolean cancelSuccess = false;
			
			// Send the request
			Integer subscriptionId = null;
			try {
				subscriptionId = subscription.request();
				logger.debug(String.format("Subscribed with request id %d and subscription id %d", subscription.getRequestID(), subscriptionId));
				requestSuccess = true;
			}
			catch(DPCSubscriptionException e) {
				logger.error("Subscription request failed.", e);
			}
			
			if(requestSuccess) {
				// Sleep for the configured interval
				try {
					Thread.sleep(interval);
				} catch (InterruptedException ignore) {}
				
				// Cancel the subscription
				try {
					if (subscription.cancel(subscriptionId)) {
						logger.debug(String.format("Successfully cancelled subscription with id %d", subscriptionId));
						cancelSuccess = true;
					}
					else {
						logger.debug(String.format("Failed to cancel subscription with id %d", subscriptionId));
					}
				}
				catch(DPCSubscriptionException e) {
					logger.error("Subscription cancel has failed.", e);
				}
			}
			
			if(requestSuccess && cancelSuccess){
				// Successful request and cancel
				if(failureTracker.hasAlerted()) {
					String duration = failureTracker.getFirstFailureDate().equals(DateUtil.DATE_NOT_SET) ?
											"unknown" :
											DateUtil.duration(failureTracker.getFirstFailureDate(), DateUtil.currentTime());
					performRecovery(duration);
				}
				failureTracker.clearFailure();
			}
			else {
				// The subscription request or cancel has failed
				failureTracker.recordFailure();
				
				if(failureTracker.getFailureCount() >= failureTolerance) {
					logger.debug("Subscription process has failed more times than tolerable.");
					performAlert();
				}
			}

			long sleepTime = nextIterationTime - System.currentTimeMillis();
			if(sleepTime > 0) {
				try {
					Thread.sleep(sleepTime);
				} catch (InterruptedException ignore) {}
			}
		}
	}
	
	private void updateAssetState(AssetState state) {
		Asset asset = new Asset();
		
		asset.setIpType(subscriptionEntry.ipType);
		asset.setSecure(subscriptionEntry.secure);
		asset.setDialogId(MonitorDialogId.SUB);
		asset.setTarget(Warehouse.SUBSCRIPTION);
		asset.setState(state);
		asset.setTimestamp(
						(state == AssetState.DOWN) ?
							(failureTracker.getFirstFailureDate().getTime()) :
							(DateUtil.currentTime().getTime())
					);
		
		assetUpdater.updateAsset(asset);
	}
	
	private void performAlert() {
		// Only alert if it hasn't been 24 hours since the first failure was recorded
		if(failureTracker.getAlertSentDate().equals(DateUtil.DATE_NOT_SET) ||
			(DateUtil.currentTime().getTime() - failureTracker.getAlertSentDate().getTime()) >= DateUtil.ONE_DAY) {
			
			// Update the asset state to down
			logger.debug("Updating asset state to down.");
			updateAssetState(AssetState.DOWN);
			
			try {
				logger.debug("Generating alert emails.");
				EmailSender.sendEmail(alertEmails,
										EmailConfiguration.ALERT_SUBJECT,
										systemName,
										EmailConfiguration.buildSubscriptionAlertMessage(subscriptionEntry, failureTracker.getFirstFailureDate()));
				failureTracker.recordAlert();
			} catch (Exception e) {
				logger.error("Failed to send alert emails", e);
			}
		}
	}
	
	private void performRecovery(String duration) {

		// Update the asset state to up
		logger.debug("Updating asset state to up.");
		updateAssetState(AssetState.UP);
		
		try {
			logger.debug("Generating recovery emails.");
			EmailSender.sendEmail(recoveryEmails,
									EmailConfiguration.RECOVERY_SUBJECT,
									systemName,
									EmailConfiguration.buildSubscriptionRecoveryMessage(subscriptionEntry, duration));
			failureTracker.clearAlert();
		} catch (Exception e) {
			logger.error("Failed to send recovery emails", e);
		}
	}
	
	@Override
	public String toString() {
		return String.format("frequency\t%d\nfailureTolerance\t%d\nsubscription\t%s", frequency, failureTolerance, subscriptionEntry.toString());
	}

}
