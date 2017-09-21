package gov.usdot.cv.system.monitor.constants;

import java.util.Date;
import java.util.List;
import java.util.Properties;

import gov.usdot.cv.resources.PrivateResourceLoader;
import gov.usdot.cv.system.monitor.SenderHistory;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.SubscriptionEntry;
import gov.usdot.cv.system.monitor.util.DateUtil;

public class EmailConfiguration {

	public static final String HOST = "email-smtp.us-east-1.amazonaws.com";
	public static final int PORT = 25;
	public static final String PROTOCOL = "smtp";
	public static final String USERNAME = PrivateResourceLoader.getProperty("@system-monitor/email.config.username@");
	public static final String PASSWORD = PrivateResourceLoader.getProperty("@system-monitor/email.config.password@");
	public static final String FROM = PrivateResourceLoader.getProperty("@system-monitor/email.config.from@");

	public static Properties getDefaultProperties() {
		Properties props = System.getProperties();
		props.put("mail.transport.protocol", EmailConfiguration.PROTOCOL);
		props.put("mail.smtp.port", EmailConfiguration.PORT);

		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.starttls.required", "true");
		
		return props;
	}
	
	public static final String ALERT_SUBJECT = "USDOT System Monitor Alert";
	public static final String ALERT_SENDER_MESSAGE_FORMAT =
					"System monitor has detected that the %s %s %s message is not making it through the system to the %s warehouse.%n" +
					"Last successful message received was dated %s.";
	public static final String ALERT_SUBSCRIPTION_MESSAGE_FORMAT =
					"System monitor has detected that the %s %s subscription is failing.%n" +
					"Subscription started failing at %s.";
	
	public static final String buildSenderAlertMessage(SenderHistory senderHistory, Warehouse warehouse) {
		return String.format(ALERT_SENDER_MESSAGE_FORMAT,
								senderHistory.getSender().ipType.toString(),
								(senderHistory.getSender().secure)?("secure"):("non-secure"),
								senderHistory.getSender().monitorDialogId.getType(),
								warehouse,
								(senderHistory.getLastMessageCreatedAt().equals(DateUtil.DATE_NOT_SET)) ?
									("unknown"):
									(DateUtil.format(senderHistory.getLastMessageCreatedAt())));
	}
	
	public static final String buildSubscriptionAlertMessage(SubscriptionEntry subscription, Date failureDate) {
		return String.format(ALERT_SUBSCRIPTION_MESSAGE_FORMAT,
								subscription.ipType.toString(),
								(subscription.secure)?("secure"):("non-secure"),
								DateUtil.format(failureDate));
	}
	
	
	public static final String RECOVERY_SUBJECT = "USDOT System Monitor Recovery";
	public static final String RECOVERY_SENDER_MESSAGE_FORMAT = 
						"System monitor has detected that the %s %s %s message is successfully making it through the system to the %s warehouse again.%n" +
						"The total downtime was %s.";
	public static final String RECOVERY_SUBSCRIPTION_MESSAGE_FORMAT =
						"System monitor has detected that the %s %s subscription is succeeding again.%n" +
						"The total downtime was %s.";
	
	public static final String buildSenderRecoveryMessage(SenderHistory senderHistory, Warehouse warehouse, String duration) {
		return String.format(RECOVERY_SENDER_MESSAGE_FORMAT,
								senderHistory.getSender().ipType.toString(),
								(senderHistory.getSender().secure)?("secure"):("non-secure"),
								senderHistory.getSender().monitorDialogId.getType(),
								warehouse,
								duration);
	}
	
	public static final String buildSubscriptionRecoveryMessage(SubscriptionEntry subscription, String duration) {
		return String.format(RECOVERY_SUBSCRIPTION_MESSAGE_FORMAT,
								subscription.ipType.toString(),
								(subscription.secure)?("secure"):("non-secure"),
								duration);
	}	
	
	
	public static final String HEARTBEAT_SUBJECT = "USDOT System Monitor Heartbeat";
	public static final String HEARTBEAT_OK_MESSAGE_FORMAT = "System monitor is still alive. Next message expected in %d hours.";
	public static final String HEARTBEAT_WARNING_SUBJECT = String.format("%s - WARNING ", HEARTBEAT_SUBJECT);
	public static final String HEARTBEAT_DEAD_THREAD_MESSAGE_FORMAT = "System monitor threads [%s] are no longer running.  Next message expected in %d hours.";
	
	public static final String buildOkHeartbeatMessage(long frequencyInHours) {
		return String.format(HEARTBEAT_OK_MESSAGE_FORMAT, frequencyInHours);
	}
	
	public static final String buildDeadThreadsHeartbeatMessage(List<String> deadThreads, long frequencyInHours) {
		StringBuilder sb = new StringBuilder();
		for(String deadThread : deadThreads) {
			if(sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(deadThread);
		}
		
		return String.format(HEARTBEAT_DEAD_THREAD_MESSAGE_FORMAT, sb.toString(), frequencyInHours);
	}
	
	
	public static final String REPORT_SUBJECT = "USDOT System Monitor Uptime Report";
	public static final String REPORT_MESSAGE_FORMAT = 
									"Attached is the Uptime Report for the period of %s - %s.";
	
	public static final String buildReportMessage(Date startDate, Date endDate) {
		return String.format(REPORT_MESSAGE_FORMAT,
								DateUtil.format(startDate),
								DateUtil.format(endDate));
	}
}
