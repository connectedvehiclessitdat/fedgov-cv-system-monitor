package gov.usdot.cv.system.monitor;

import java.io.File;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.log4j.Logger;

import gov.usdot.cv.system.monitor.constants.EmailConfiguration;

public class EmailSender {
	
	private static final Logger logger = Logger.getLogger(EmailSender.class);

	public static void sendEmail(String[] recipients, String subject, String systemName, String message) throws AddressException, MessagingException {
		if(recipients != null && recipients.length != 0) {
			Session session = Session.getDefaultInstance(EmailConfiguration.getDefaultProperties());
	
			MimeMessage msg = new MimeMessage(session);
			
			msg.setFrom(new InternetAddress(EmailConfiguration.FROM));
			
			InternetAddress[] recipientAddresses = new InternetAddress[recipients.length];
			for(int i = 0; i < recipients.length; i++) {
				recipientAddresses[i] = new InternetAddress(recipients[i]);
			}
			msg.addRecipients(Message.RecipientType.TO, recipientAddresses);
			
			msg.setSubject(String.format("%s: %s", subject, systemName));
	
			msg.setContent(message, "text/plain");
			
			Transport transport = session.getTransport();
			
			logger.debug("Attempting to send an email through the Amazon SES SMTP interface...");
			transport.connect(EmailConfiguration.HOST, EmailConfiguration.USERNAME, EmailConfiguration.PASSWORD);
			transport.sendMessage(msg, msg.getAllRecipients());
		}
		else {
			logger.warn("No alert email recipeints were configured.");
		}
		
	}
	
	public static void sendEmail(String[] recipients, String subject, String systemName, String message, File file)
							throws AddressException, MessagingException {
		if(recipients != null && recipients.length != 0) {
			Session session = Session.getDefaultInstance(EmailConfiguration.getDefaultProperties());
	
			MimeMessage msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress(EmailConfiguration.FROM));
			InternetAddress[] recipientAddresses = new InternetAddress[recipients.length];
			for(int i = 0; i < recipients.length; i++) {
				recipientAddresses[i] = new InternetAddress(recipients[i]);
			}
			msg.addRecipients(Message.RecipientType.TO, recipientAddresses);
			msg.setSubject(String.format("%s: %s", subject, systemName));
			msg.setText(message);
			
			// Create the message part
            BodyPart messageBodyPart = new MimeBodyPart();

            // Now set the actual message
            messageBodyPart.setText(message);

            // Create a multipar message
            Multipart multipart = new MimeMultipart();

            // Set text message part
            multipart.addBodyPart(messageBodyPart);

            // Part two is attachment
            messageBodyPart = new MimeBodyPart();
            DataSource source = new FileDataSource(file);
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName(file.getName());
            multipart.addBodyPart(messageBodyPart);

            // Send the complete message parts
            msg.setContent(multipart);
            
			Transport transport = session.getTransport();
			
			logger.debug("Attempting to send an email with attachment through the Amazon SES SMTP interface...");
			transport.connect(EmailConfiguration.HOST, EmailConfiguration.USERNAME, EmailConfiguration.PASSWORD);
			transport.sendMessage(msg, msg.getAllRecipients());
		}
		else {
			logger.warn("No alert email recipeints were configured.");
		}
	}

}
