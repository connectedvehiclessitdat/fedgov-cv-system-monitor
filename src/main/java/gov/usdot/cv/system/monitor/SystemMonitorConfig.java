package gov.usdot.cv.system.monitor;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;

import gov.usdot.cv.system.monitor.constants.IpType;
import gov.usdot.cv.system.monitor.constants.MonitorDialogId;
import gov.usdot.cv.system.monitor.constants.MonitorGroupId;
import gov.usdot.cv.system.monitor.constants.Warehouse;
import gov.usdot.cv.system.monitor.util.DateUtil;
import gov.usdot.cv.whtools.client.config.ConfigException;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class SystemMonitorConfig {
	
	private static Logger logger = Logger.getLogger(SystemMonitorConfig.class);

	static private final String SYSTEM_NAME_NAME = "systemName";
	
	public final Senders senders;
	public final MonitorConfig monitorConfig;
	public final UptimeConfig uptimeConfig;
	
	public SystemMonitorConfig(JSONObject config) throws ConfigException, ParseException {
		senders = new Senders(config);
		monitorConfig = new MonitorConfig(config);
		uptimeConfig = new UptimeConfig(config);
	}
	
	public class Senders {
		static private final String SECTION_NAME = "senders";
		
		public final SenderEntry[] entries;
		
		private Senders(JSONObject config) throws ConfigException {
			if (config.has(SECTION_NAME)) {
				JSONArray jsonSenders = config.getJSONArray(SECTION_NAME);
				final int count = jsonSenders.size();
				if(count > 0) {
					entries = new SenderEntry[count];
					for(int i = 0; i < count; i++)
						entries[i] = new SenderEntry(jsonSenders.getJSONObject(i));
					
					logger.debug(String.format("%d senders found in configuration", count));
				}
				else {
					throw new ConfigException(String.format("%s is a requires at least one sender.", SECTION_NAME));
				}
			}
			else {
				throw new ConfigException(String.format("%s is a required configuration field.", SECTION_NAME));
			}
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(String.format("\t%s\n", SECTION_NAME));
			if (entries != null)
				for(SenderEntry sender : entries)
					sb.append(sender.toString() + "\n");
			
			return sb.toString();
		}
	}
	
	public static class SenderEntry {
		static private final String SECTION_NAME = "sender";
		static private final long DEFAULT_FREQUENCY = 60 * 1000;
		static private final String DEFAULT_IP_TYPE = IpType.IPv4.toString();
		static private final boolean DEFAULT_SECURE = false;
		static private final String DEFAULT_MESSAGE_TYPE = MonitorDialogId.VSD.toString();
		static private final String DEFAULT_TARGET_WAREHOUSE = Warehouse.ALL.toString();
		
		public final long frequency;
		public final String senderConfig;
		public final IpType ipType;
		public final boolean secure;
		public final MonitorDialogId monitorDialogId;
		public final Warehouse targetWarehouse;
		
		public SenderEntry(JSONObject config) {
			JSONObject sender = config.has(SECTION_NAME) ? config.getJSONObject(SECTION_NAME) : new JSONObject();
			
			frequency = sender.optLong("frequency", DEFAULT_FREQUENCY);
			senderConfig = sender.optString("senderConfig", null);
			ipType = IpType.getByValue(sender.optString("ipType", DEFAULT_IP_TYPE));
			secure = sender.optBoolean("secure", DEFAULT_SECURE);
			monitorDialogId = MonitorDialogId.getByType(sender.optString("messageType", DEFAULT_MESSAGE_TYPE));
			targetWarehouse = Warehouse.getByShortValue(sender.optString("targetWarehouse", DEFAULT_TARGET_WAREHOUSE));
		}
		
		@Override
		public String toString() {
			return String.format("    %s\n\tfrequency\t%d\n\tsenderConfig\t%s\n\tipType\t\t%s\n\tsecure\t\t%b\n\tmonitorDialogId\t\t%s\n\ttargetWarehouse\t\t%s", 
												SECTION_NAME,
												frequency,
												senderConfig != null ? senderConfig : "",
												ipType,
												secure,
												monitorDialogId,
												targetWarehouse.getShortValue()
											);
		}
		
		public static String buildSenderKey(MonitorGroupId groupId, MonitorDialogId monitorDialogId) {
			return String.format("%s_%s",
							groupId.toString(),
							monitorDialogId.getType());
		}
	}
	
	public class MonitorConfig {
		static private final String SECTION_NAME = "monitorConfig";
		static private final String ALERT_EMAILS_NAME = "alertEmails";
		static private final String RECOVERY_EMAILS_NAME = "recoveryEmails";
		static private final long DEFAULT_QUERY_FREQUENCY = 60 * 1000;
		static private final int DEFAULT_FAILURE_TOLERANCE = 3;

		public final String systemName;
		public final String warehouseConfig;
		public final Subscriptions subscriptions;
		public final long queryFrequency;
		public final int failureTolerance;
		public final String[] alertEmails;
		public final String[] recoveryEmails;
		public final HeartbeatConfig heartbeat;
		
		public MonitorConfig(JSONObject config) throws ConfigException, ParseException {
			systemName = config.optString(SYSTEM_NAME_NAME, "");
			
			JSONObject monitorConfig = config.has(SECTION_NAME) ? config.getJSONObject(SECTION_NAME) : new JSONObject();
			
			warehouseConfig = monitorConfig.optString("warehouseConfig", null);
			if(warehouseConfig == null) {
				throw new ConfigException("warehouseConfig is a required configuration field.");
			}

			subscriptions = new Subscriptions(monitorConfig);
			
			queryFrequency = monitorConfig.optLong("queryFrequency", DEFAULT_QUERY_FREQUENCY);
			failureTolerance = monitorConfig.optInt("failureTolerance", DEFAULT_FAILURE_TOLERANCE);
			
			if (monitorConfig.has(ALERT_EMAILS_NAME)) {
				JSONArray jsonAlertEmails = monitorConfig.getJSONArray(ALERT_EMAILS_NAME);
				final int count = jsonAlertEmails.size();
				alertEmails = new String[count];
				for(int i = 0; i < count; i++)
					alertEmails[i] = jsonAlertEmails.getString(i);

				
				logger.debug(String.format("%d alert emails found in configuration", count));
			} else {
				logger.info("No alert emails were configured.");
				alertEmails = null;
			}
			
			if (monitorConfig.has(RECOVERY_EMAILS_NAME)) {
				JSONArray jsonRecoveryEmails = monitorConfig.getJSONArray(RECOVERY_EMAILS_NAME);
				final int count = jsonRecoveryEmails.size();
				recoveryEmails = new String[count];
				for(int i = 0; i < count; i++)
					recoveryEmails[i] = jsonRecoveryEmails.getString(i);

				
				logger.debug(String.format("%d recovery emails found in configuration", count));
			} else {
				logger.info("No recovery emails were configured.");
				recoveryEmails = null;
			}

			heartbeat = new HeartbeatConfig(monitorConfig);
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(
									String.format(
										"    %s\n\tsystemName\t\t%s\n\twarehouseConfig\t\t%s\n\tqueryFrequency\t\t%d\n\tfailureTolerance\t%d\n\talertEmails\n",
										SECTION_NAME, systemName, warehouseConfig, queryFrequency, failureTolerance));
			if (alertEmails != null)
				for(String alertEmail : alertEmails)
					sb.append("\t\t\t\t").append(alertEmail).append("\n");
			if (recoveryEmails != null)
				for(String recoveryEmail : recoveryEmails)
					sb.append("\t\t\t\t").append(recoveryEmail).append("\n");
			sb.append(String.format("%s", heartbeat.toString()));
			
			return sb.toString();
		}
	}

	public class Subscriptions {
		static private final String SECTION_NAME = "subscriptions";
		
		public final SubscriptionEntry[] entries;
		
		private Subscriptions(JSONObject config) throws ConfigException {
			if (config.has(SECTION_NAME)) {
				JSONArray jsonSubscriptions = config.getJSONArray(SECTION_NAME);
				final int count = jsonSubscriptions.size();
				if(count > 0) {
					entries = new SubscriptionEntry[count];
					for(int i = 0; i < count; i++)
						entries[i] = new SubscriptionEntry(jsonSubscriptions.getJSONObject(i));
					
					logger.debug(String.format("%d subscriptions found in configuration", count));
				}
				else {
					throw new ConfigException(String.format("%s is a requires at least one subscription.", SECTION_NAME));
				}
			}
			else {
				throw new ConfigException(String.format("%s is a required configuration field.", SECTION_NAME));
			}
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(String.format("\t%s\n", SECTION_NAME));
			if (entries != null)
				for(SubscriptionEntry subscription : entries)
					sb.append(subscription.toString() + "\n");
			
			return sb.toString();
		}
	}
	
	public static class SubscriptionEntry {
		static private final String SECTION_NAME = "subscription";
		static private final long DEFAULT_INTERVAL = 40 * 1000;
		static private final String DEFAULT_IP_TYPE = IpType.IPv4.toString();
		static private final boolean DEFAULT_SECURE = false;

		public final long interval;
		public final JSONObject subscription;
		public final IpType ipType;
		public final boolean secure;
		
		public SubscriptionEntry(JSONObject config) {
			JSONObject subscriptionEntry = config.has(SECTION_NAME) ? config.getJSONObject(SECTION_NAME) : new JSONObject();
			
			interval = subscriptionEntry.optLong("interval", DEFAULT_INTERVAL);
			subscription = subscriptionEntry.optJSONObject("subscription");
			ipType = IpType.getByValue(subscriptionEntry.optString("ipType", DEFAULT_IP_TYPE));
			secure = subscriptionEntry.optBoolean("secure", DEFAULT_SECURE);
		}
		
		@Override
		public String toString() {
			return String.format("    %s\n\tsubscription\n\t%s\n\tipType\t\t%s\n\tsecure\t\t%b", 
												SECTION_NAME,
												subscription.toString(),
												ipType,
												secure
											);
		}
	}
	
	public class HeartbeatConfig {
		static private final String SECTION_NAME = "heartbeat";
		static private final String HEARTBEAT_EMAILS_NAME = "heartbeatEmails";
		private final SimpleDateFormat HEARTBEAT_SDF = new SimpleDateFormat("HH:mm");
		static private final String DEFAULT_INITIAL_HEARTBEAT = "12:00"; 
		static private final int DEFAULT_FREQUENCY_IN_HOURS = 12;
		
		public final String[] heartbeatEmails;
		public final Date initialHeartbeat;
		public final int frequencyInHours;
		
		public HeartbeatConfig(JSONObject config) throws ConfigException, ParseException {
			JSONObject heartbeatConfig = config.has(SECTION_NAME) ? config.getJSONObject(SECTION_NAME) : new JSONObject();

			if (heartbeatConfig.has(HEARTBEAT_EMAILS_NAME)) {
				JSONArray jsonHeartbeatEmails = heartbeatConfig.getJSONArray(HEARTBEAT_EMAILS_NAME);
				final int count = jsonHeartbeatEmails.size();
				heartbeatEmails = new String[count];
				for(int i = 0; i < count; i++)
					heartbeatEmails[i] = jsonHeartbeatEmails.getString(i);

				
				logger.debug(String.format("%d heartbeat emails found in configuration", count));
			} else {
				logger.info("No heartbeat emails were configured.");
				heartbeatEmails = null;
			}
			
			String initialHeartbeatString = heartbeatConfig.optString("initialHeartbeat", DEFAULT_INITIAL_HEARTBEAT);
			initialHeartbeat = DateUtil.getNextTime(initialHeartbeatString, HEARTBEAT_SDF);
			logger.debug(String.format("Current time is %s and initialHeartbeat set to %s.",
											DateUtil.format(DateUtil.currentTime()),
											DateUtil.format(initialHeartbeat)));
			
			frequencyInHours = heartbeatConfig.optInt("frequencyInHours", DEFAULT_FREQUENCY_IN_HOURS);
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(
									String.format(
											"\t%s\n\t\theartbeatEmails\n", 
											SECTION_NAME));
			if (heartbeatEmails != null)
				for(String heartbeatEmail : heartbeatEmails)
					sb.append("\t\t\t\t").append(heartbeatEmail).append("\n");
			sb.append(String.format("\t\tinitialHeartbeat\t%s\n", DateUtil.format(initialHeartbeat)));
			sb.append(String.format("\t\tfrequencyInHours\t%d\n", frequencyInHours));
			
			return sb.toString();
		}
	}

	public class UptimeConfig {
		static private final String SECTION_NAME = "uptimeConfig";
		static private final int DEFAULT_DATABSE_PORT = 8161; 
		static private final int DEFAULT_UPDATE_INTERVAL_IN_MINUTES = 30;

		public final String systemName;
		public final String databasePath;
		public final int databasePort;
		public final int updateIntervalInMinutes;
		
		public final ReporterConfig reporter;
		
		public UptimeConfig(JSONObject config) {
			systemName = config.optString(SYSTEM_NAME_NAME, "");
			
			JSONObject uptimeConfig = config.has(SECTION_NAME) ? config.getJSONObject(SECTION_NAME) : new JSONObject();
			
			databasePath = uptimeConfig.optString("databasePath", null);
			databasePort = uptimeConfig.optInt("databasePort", DEFAULT_DATABSE_PORT);
			updateIntervalInMinutes = uptimeConfig.optInt("updateIntervalInMinutes", DEFAULT_UPDATE_INTERVAL_IN_MINUTES);
			
			reporter = new ReporterConfig(uptimeConfig);
		}
		
		@Override
		public String toString() {
			return String.format("    %s\n\tsystemName\t\t%s\n\tdatabasePath\n\t%s\n\tdatabasePort\t\t%d\n\tupdateIntervalInMinutes\t\t%d\n%s", 
					SECTION_NAME,
					systemName,
					databasePath,
					databasePort,
					updateIntervalInMinutes,
					reporter.toString()
				);
		}
	}
	
	public class ReporterConfig {
		static private final String SECTION_NAME = "reporter";
		static private final String REPORT_EMAILS_NAME = "reportEmails";
		
		public final String reportLocation;
		public final String[] reportEmails;
		
		public ReporterConfig(JSONObject config) {
			JSONObject reporterConfig = config.has(SECTION_NAME) ? config.getJSONObject(SECTION_NAME) : new JSONObject();

			this.reportLocation = reporterConfig.optString("reportLocation", null);
			
			if (reporterConfig.has(REPORT_EMAILS_NAME)) {
				JSONArray jsonReportEmails = reporterConfig.getJSONArray(REPORT_EMAILS_NAME);
				final int count = jsonReportEmails.size();
				reportEmails = new String[count];
				for(int i = 0; i < count; i++)
					reportEmails[i] = jsonReportEmails.getString(i);

				logger.debug(String.format("%d report emails found in configuration", count));
			} else {
				logger.info("No report emails were configured.");
				reportEmails = null;
			}
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(
									String.format(
											"\t%s\n\t\treportLocation\t%s\n\t\treportEmails\n",
											SECTION_NAME,
											reportLocation));
			if (reportEmails != null)
				for(String reportEmail : reportEmails)
					sb.append("\t\t\t\t").append(reportEmail).append("\n");
			
			return sb.toString();
		}
	}
}
