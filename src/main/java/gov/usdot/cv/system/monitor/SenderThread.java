package gov.usdot.cv.system.monitor;

import gov.usdot.cv.apps.sender.Sender;
import gov.usdot.cv.apps.sender.SenderConfig;
import gov.usdot.cv.security.cert.CertificateException;
import gov.usdot.cv.security.crypto.CryptoException;
import gov.usdot.cv.system.monitor.SystemMonitorConfig.SenderEntry;
import gov.usdot.cv.system.monitor.util.JsonUtil;

import java.io.IOException;
import java.net.UnknownHostException;

import net.sf.json.JSONObject;

import org.apache.commons.codec.DecoderException;
import org.apache.log4j.Logger;

public class SenderThread implements Runnable {

	private static Logger logger = Logger.getLogger(SenderThread.class);
	
	private long frequency;
	private Sender sender;
	
	public SenderThread(SenderEntry senderEntry) throws DecoderException, CertificateException,
																	IOException, CryptoException, UnknownHostException {
		this.frequency = senderEntry.frequency;
		
		JSONObject config = JsonUtil.createJsonFromFile(senderEntry.senderConfig);
		
		SenderConfig senderConfig = new SenderConfig(config);
		
		sender = new Sender(senderConfig);
	}
	
	public void run() {
		logger.debug(String.format("Sender thread %s is now running", toString()));
		while(true) {
			try {
				sender.send();
			}
			catch (Throwable t) {
				logger.error(String.format("Sender %s failed to send message.", toString()), t);
			}
			
			try {
				Thread.sleep(frequency);
			} catch (InterruptedException ignore) {}
		}
	}
	
	@Override
	public String toString() {
		return String.format("frequency\t%d\nsender\t%s", frequency, sender.toString());
	}

}
