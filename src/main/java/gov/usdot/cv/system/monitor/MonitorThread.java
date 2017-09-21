package gov.usdot.cv.system.monitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import gov.usdot.cv.system.monitor.SystemMonitorConfig.HeartbeatConfig;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.MonitorConfig;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.SenderEntry;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.Senders;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.SubscriptionEntry;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.UptimeConfig;
import gov.usdot.cv.system.monitor.constants.EmailConfiguration;
import gov.usdot.cv.system.monitor.constants.MonitorDialogId;
import gov.usdot.cv.system.monitor.constants.SystemMonitorBoundingBox;
import gov.usdot.cv.system.monitor.constants.Warehouse;
import gov.usdot.cv.system.monitor.util.DateUtil;
import gov.usdot.cv.whtools.client.CASClient;
import gov.usdot.cv.whtools.client.WarehouseClient;
import gov.usdot.cv.whtools.client.config.ConfigUtils;
import gov.usdot.cv.whtools.client.config.WarehouseConfig;

public class MonitorThread implements Runnable {
	
	private static final Logger logger = Logger.getLogger(MonitorThread.class);

	private final String QUERY_FORMAT =
			"QUERY:" +
				"{" +
					"\"systemQueryName\":\"%s\"," +
					"\"dialogID\":\"%d\"," +
					"%s" +									// Place holder to include a startDate field
					"\"startDateOperator\":\"GTE\"," +
					"\"endDateOperator\":\"LTE\"," +
					"\"nwLat\":\"" + SystemMonitorBoundingBox.NW_LAT + "\"," +
					"\"nwLon\":\"" + SystemMonitorBoundingBox.NW_LON + "\"," +
					"\"seLat\":\"" + SystemMonitorBoundingBox.SE_LAT + "\"," +
					"\"seLon\":\"" + SystemMonitorBoundingBox.SE_LON + "\"," +
					"\"orderByField\":\"createdAt\"," +		// Order by date
					"\"orderByOrder\":\"-1\"," +			// Descending
					"\"skip\":\"0\"," +
					"\"limit\":\"%d\"," +
					"\"resultEncoding\":\"full\"" +		// ALWAYS return full - results come as JSON with info about the result
				"}";
	private final String QUERY_START_DATE_FORMAT = "\"startDate\":\"%s\",";
	
	private final int QUERY_RECONNECT_MAX = 3;

	private String systemName;
	private long queryFrequency;
	private int failureTolerance;
	private String[] alertEmails;
	private String[] recoveryEmails;
	private Heartbeat heartbeat;
	
	private WarehouseConfig warehouseConfig;
	private WarehouseClient warehouseClient;
	private SystemMonitorResponseHandler responseHandler = null;
	
	private Map<MonitorDialogId, Integer> dialogIdCounts = new HashMap<MonitorDialogId, Integer>();
	private Map<Warehouse, Map<MonitorDialogId, Date>> warehouseDialogIdLastQueryTime = new HashMap<Warehouse, Map<MonitorDialogId, Date>>();
	private Map<SenderEntry, Warehouse> senderToWarehouseMaps = new HashMap<SenderEntry, Warehouse>();
	
	private List<SubscriptionThread> subscriptionThreads = new ArrayList<SubscriptionThread>();
	
	public MonitorThread(MonitorConfig monitorConfig, Senders senders, UptimeConfig uptimeConfig)
					throws Exception {

		// Set up the Mapping of warehouses to the last time that a dialog ID was queries against that warehouse
		for(Warehouse warehouse : Warehouse.knownValues()) {
			warehouseDialogIdLastQueryTime.put(warehouse, new HashMap<MonitorDialogId, Date>());
		}
		
		for(SenderEntry senderEntry : senders.entries) {
			// Increment the number of times we've seen a sender of this DialogId
			Integer dialogIdCount = dialogIdCounts.get(senderEntry.monitorDialogId);
			dialogIdCounts.put(senderEntry.monitorDialogId, (dialogIdCount == null)?(1):(dialogIdCount + 1));
			
			// Save what warehouses this sender sends data to
			senderToWarehouseMaps.put(senderEntry, senderEntry.targetWarehouse);
		}
		logger.debug(String.format("dialogIdCounts = %s", Arrays.toString(dialogIdCounts.entrySet().toArray())));

		this.systemName = monitorConfig.systemName;
		this.queryFrequency = monitorConfig.queryFrequency;
		this.failureTolerance = monitorConfig.failureTolerance;
		this.alertEmails = monitorConfig.alertEmails;
		this.recoveryEmails = monitorConfig.recoveryEmails;

		// Create the heartbeat thread
		this.heartbeat = new Heartbeat(monitorConfig.heartbeat);
		
		// Create the subscription threads
		for(SubscriptionEntry subscriptionEntry : monitorConfig.subscriptions.entries) {
			try {
				logger.debug(String.format("Creating subscription thread for subscription entry\n%s", subscriptionEntry.toString()));
				SubscriptionThread subscriptionThread = 
						new SubscriptionThread(subscriptionEntry, monitorConfig, heartbeat, uptimeConfig);
				subscriptionThreads.add(subscriptionThread);
			} catch (Exception e) {
				logger.error(String.format("Failed to create subscription [%s]", subscriptionEntry.toString()), e);
			}
		}

		warehouseConfig = ConfigUtils.loadConfigBean(monitorConfig.warehouseConfig, WarehouseConfig.class);
		warehouseConfig.postLoadCalculateValues();
		
		responseHandler = new SystemMonitorResponseHandler(warehouseConfig, monitorConfig, senders, uptimeConfig);
		
		try {
			connectToWarehouseClient();
		} catch (Exception e) {
			logger.error(String.format("Failed to connect to warehouse client %s.", warehouseConfig), e);
			return;
		}
	}
	
	private void connectToWarehouseClient() throws Exception {
		// Get a jSessionID from CAS
		CASClient casClient = CASClient.configure(warehouseConfig);
		String jSessionID = casClient.login();
		warehouseConfig.jSessionID = jSessionID;
		
		warehouseClient = WarehouseClient.configure(warehouseConfig, responseHandler);
		logger.info("Opening WebSocket to " + warehouseConfig.warehouseURL);
		warehouseClient.connect();
		
		// Give the thread 3 seconds to connect
		try {
			Thread.sleep(3000);
		} catch(InterruptedException ignore) {}
	}
	
	public void run() {
		FailureTracker responseHandlerConnectionFailureTracker = new FailureTracker();
		FailureTracker warehouseClientConnectionFailureTracker = new FailureTracker();
		FailureTracker unexpectedFailureTracker = new FailureTracker();
		
		// Start the subscriptions
		ExecutorService exec = Executors.newFixedThreadPool(subscriptionThreads.size());
		for(SubscriptionThread subscriptionThread : subscriptionThreads) {
			logger.debug(String.format("Starting subscription thread\n%s", subscriptionThread.toString()));
			exec.execute(subscriptionThread);
			// Delay 10 seconds between starting each subscription thread so that there is no competition during
			// run time of multiple subscriptions trying to access same address:port at the same destination
			try {
				Thread.sleep(10000);
			} catch (InterruptedException ignore) {}
		}
		
		// Add this monitor thread to be watched by the heartbeat
		heartbeat.registerThread("Monitor Thread", Thread.currentThread());
		
		// Start the heartbeat
		ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
		long initialDelay = heartbeat.initialHeartbeat.getTime() - System.currentTimeMillis();
		long period = heartbeat.frequencyInHours * 60 * 60 * 1000;
		ses.scheduleAtFixedRate(heartbeat, initialDelay, period, TimeUnit.MILLISECONDS);
		
		while(true) {
			//long sleepTime = queryFrequency;
			
			if(responseHandler.isConnected()) {
				if(responseHandlerConnectionFailureTracker.hasAlerted()) {
					performRecovery(responseHandlerConnectionFailureTracker);
				}
				responseHandlerConnectionFailureTracker.clearFailure();
				
				try {
					logger.debug("Running monitor iteration");

					// Keep track if there is a failure connecting to the warehouse client
					boolean warehouseClientConnectionFailureOccured = false;
					
					for(Entry<MonitorDialogId, Integer> dialogIdCount : dialogIdCounts.entrySet()) {
						MonitorDialogId monitorDialogId = dialogIdCount.getKey();
						Integer count = dialogIdCount.getValue(); 
						logger.debug(String.format("Looking for dialog ID %s with count of %d", monitorDialogId, count));
						
						// Query all the warehouses
						for(Warehouse warehouse : Warehouse.knownValues()) {
							
							// Query for 5 times the amount of expected values for this dialog ID so that we
							// have ample data to check for the latest values in
							String query = String.format(QUERY_FORMAT,
									warehouse,
									monitorDialogId.getDialogId().longValue(),
									(warehouseDialogIdLastQueryTime.get(warehouse).containsKey(monitorDialogId)) ?
											(String.format(
														QUERY_START_DATE_FORMAT,
														DateUtil.format(warehouseDialogIdLastQueryTime.get(warehouse).get(monitorDialogId)))) :
											(""),
									count);
			
							// Save the time this query happened so the next query will only look for data since this point
							warehouseDialogIdLastQueryTime.get(warehouse).put(monitorDialogId, DateUtil.currentTime());
							
							if(queryWarehouseWithReconnect(query, QUERY_RECONNECT_MAX)) {
								// Record that there was a successful connection to the warehouse client
								warehouseClientConnectionFailureOccured = false;
							}
							else {
								// Record that there was failure to connect to the warehouse client
								warehouseClientConnectionFailureOccured = true;
							}
						}
					}
					
					// Check if we were unable to connect to the client warehouse
					if(warehouseClientConnectionFailureOccured) {
						warehouseClientConnectionFailureTracker.recordFailure();

						logger.debug(String.format(
										"Querying warehouse failed with %d reconnect attempts. " +
										"Incrementing Warehouse Client connection failure count to %d.",
											QUERY_RECONNECT_MAX,
											warehouseClientConnectionFailureTracker.getFailureCount()));
						if(warehouseClientConnectionFailureTracker.getFailureCount() >= failureTolerance) {
							logger.error("Warehouse Client failed to connect more times than the failure tolerance. Sending alert emails.");
							performAlert(warehouseClientConnectionFailureTracker,
											"Monitor unable to connect to data warehouse more times than the failure tolerance.");
						}
						else {
							logger.warn("Warehouse Client is not connected. Waiting until next iteration to query.");
						}
					}
					else {
						if(warehouseClientConnectionFailureTracker.hasAlerted()) {
							performRecovery(warehouseClientConnectionFailureTracker);
						}
						warehouseClientConnectionFailureTracker.clearFailure();
					}
					
					// If we reach here, there were no unexpected exceptions, clear any previous failures
					if(unexpectedFailureTracker.hasAlerted()) {
						performRecovery(unexpectedFailureTracker);
					}
					unexpectedFailureTracker.clearFailure();
				} catch (Exception e) {
					unexpectedFailureTracker.recordFailure();
					logger.error
							(String.format("Unexpected error sending monitor queries. Incremented unexpected error count to %d.",
										unexpectedFailureTracker.getFailureCount()),
							e);

					if(unexpectedFailureTracker.getFailureCount() >= failureTolerance) {
						logger.error("Unexpected errors have occured more times than the failure tolerance. " + 
									 "Sending alert emails.");
						performAlert(unexpectedFailureTracker,
										"Monitor has experienced unexpected errors more times than the failulre tolerance.");
					}
				}
			}
			else {
				responseHandlerConnectionFailureTracker.recordFailure();

				if(responseHandlerConnectionFailureTracker.getFailureCount() >= failureTolerance) { 
					logger.error("Response handler failed to connect more times than the failure tolerance. Sending alert emails.");

					performAlert(responseHandlerConnectionFailureTracker,
									"Monitor's response handler has failed to connect more times than the failulre tolerance.");
				}
				else {
					logger.warn("Response handler is not yet connected. Waiting until next iteration to query");
				}
			}
			
			try {
				logger.debug(String.format("Sleeping for %d", queryFrequency));
				Thread.sleep(queryFrequency);
				logger.debug("Woke up from sleep");
			} catch (InterruptedException ignore) {}
		}
	}
	
	private boolean queryWarehouseWithReconnect(String query, int attempts) throws Exception {
		for(int i = 1; i <= attempts; i++) {
			logger.debug(String.format("Attempt #%d of sending query: %s", i, query));
			try {
				warehouseClient.send(query);
				
				return true;
			} catch (Exception e) {
				// Make sure the warehouse client is shutdown
				warehouseClient.close();
				
				// Try to reconnect:
				connectToWarehouseClient();
			}
		}
		
		return false;
	}

	private void performAlert(FailureTracker failureTracker, String alertMessage) {
		// Only alert if it has been 24 hours since the first failure was recorded
		if(failureTracker.getAlertSentDate().equals(DateUtil.DATE_NOT_SET) ||
				(DateUtil.currentTime().getTime() - failureTracker.getAlertSentDate().getTime()) >= DateUtil.ONE_DAY) {
			try {
				logger.debug("Generating alert emails.");
				EmailSender.sendEmail(alertEmails,
										EmailConfiguration.ALERT_SUBJECT,
										systemName,
										alertMessage);
				failureTracker.recordAlert();
			} catch (Exception e) {
				logger.error("Failed to send email alerts", e);
			}
		}
	}
	
	private void performRecovery(FailureTracker failureTracker) {
		String duration = failureTracker.getFirstFailureDate().equals(DateUtil.DATE_NOT_SET) ?
								"unknown" :
								DateUtil.duration(failureTracker.getFirstFailureDate(), DateUtil.currentTime());
		try {
			logger.debug("Generating recovery emails.");
			EmailSender.sendEmail(recoveryEmails,
									EmailConfiguration.RECOVERY_SUBJECT,
									systemName,
									String.format("Monitor has recovered from previously alerted issues.%nTotal downtime was %s.", duration));
			failureTracker.clearAlert();
		} catch (Exception e) {
			logger.error("Failed to send recovery emails", e);
		}
	}
	
	public class Heartbeat implements Runnable {
		private final Logger logger = Logger.getLogger(Heartbeat.class);
		
		private String[] heartbeatEmails;
		private Date initialHeartbeat;
		private long frequencyInHours;
		
		private Map<String, Thread> watchedThreads;
		
		public Heartbeat(HeartbeatConfig heartbeatConfig) {
			this.watchedThreads = new HashMap<String, Thread>();
			this.heartbeatEmails = heartbeatConfig.heartbeatEmails;
			this.initialHeartbeat = heartbeatConfig.initialHeartbeat;
			this.frequencyInHours = heartbeatConfig.frequencyInHours;
		}
		
		public void run() {
			List<String> deadThreads = new ArrayList<String>();
			
			for(Entry<String, Thread> namedThread : watchedThreads.entrySet()) {
				if(!namedThread.getValue().isAlive()) {
					deadThreads.add(namedThread.getKey());
				}
			}
			
			if(deadThreads.size() == 0) {
				try {
					EmailSender.sendEmail(heartbeatEmails,
											EmailConfiguration.HEARTBEAT_SUBJECT,
											systemName,
											EmailConfiguration.buildOkHeartbeatMessage(frequencyInHours));
				} catch (Exception e) {
					logger.error("Failed to send heartbeat email", e);
				}
			}
			else {
				try {
					EmailSender.sendEmail(heartbeatEmails,
											EmailConfiguration.HEARTBEAT_WARNING_SUBJECT,
											systemName,
											EmailConfiguration.buildDeadThreadsHeartbeatMessage(deadThreads, frequencyInHours));
				} catch (Exception e) {
					logger.error("Failed to send heartbeat email", e);
				}
			}
		}
		
		public void registerThread(String name, Thread newThread) {
			watchedThreads.put(name, newThread);
		}
	}
}