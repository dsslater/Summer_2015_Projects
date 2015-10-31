/*
 * Author: David S Slater
 * File: balance_checker.java
 * 
 * This program uses two API calls to ___. The first gets an Authentication Key and the second uses that key to get the current balance. 
 * This information is stored in a simple log file in the same folder as the executable called balance_log.txt. This program also makes use of 
 * Runtime.getRuntime().exec() to utilize a shell command (tail) this is done to grab the last line of the log. This last line's balance is then 
 * Compared to the news lines balance. If the drop is > than the Warning Percentage Change (WARNING_PRCTCHANGE) as a decimal then a warning email
 * is sent due to volatile account activity. Finally the program adds the new balance to the log. [[This program is currently being called by chrontab]]
 */
import java.net.*;
import java.io.*;
import java.util.Date;
import java.util.Properties;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import org.json.JSONObject;
import java.lang.String;
import javax.mail.internet.*;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.mail.*;

public class balance_checker {
	
	private final static String KEY_URL = "URL TO GET KEY";
	private final static String KEY_INPUT = "PAYLOAD FOR KEY";
	private final static String BALANCE_URL = "URL TO CHECK BALANCE";
	private final static double WARNING_PRCT_CHANGE = 0.05;
	private final static double MIN_BALANCE = 20;
	private final static String WARNING_MESSAGE = "WARNING: Aggressive Withdraws on ___ Account, Current Balance: ";
	private final static String WARNING_SUBJECT = "WARNING";
	private final static String WARNING_TO = "EMAIL ADDRESS";
	private final static String WARNING_FROM = "EMAIL ADDRESS";
	private final static String LOG_FILE = "WHERE TO LOG DATA";
	
	/*
	 * Determines the date, gets an Authentication key, uses that to get the balance, 
	 * checks for volatility in the balance log, sends a  warning email if needed and 
	 * then updates the log with the new balance.
	 */
	public static void main(String[] args) throws Exception 
	{
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		Date date = new Date();
		String key = get_key();
		if(key == null) throw new Exception("The ___ API call did not return a key");
		double balance = get_balance(key);
		String entry = balance + " " + dateFormat.format(date);
		String last = getLastLine();
		if(last != null) {
			Double last_amt = Double.valueOf(last.substring(0, last.indexOf(' ')));
			if((((last_amt - balance) / last_amt) > WARNING_PRCT_CHANGE) || balance < MIN_BALANCE)
			{
				System.out.println("Volatile Balance: Emailed Warning");
				sendWarning(balance);
			}
		}
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(LOG_FILE, true)));
		out.println(entry);
		out.close();
		System.out.println("Done");
	}

	/*
	 * Returns an Authentication key as a String. Uses the JSON input to fulfill the PUT request. 
	 */
	private static String get_key() throws Exception 
	{
		URL url = new URL(KEY_URL);
		JSONObject input = new JSONObject(KEY_INPUT);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setDoOutput(true);
		connection.setRequestMethod("PUT");
		connection.setRequestProperty("Accept", "application/json");
		OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
		out.write(input.toString());
		out.close();
		if (connection.getResponseCode() >= 400) throw new Exception(
				"The ___ API call to get the Authentication Key was unable to properly connect  Response Code: "
				+ connection.getResponseCode());
		connection.getInputStream();
		BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String next;
		while ((next = in.readLine()) != null) {
			response.append(next);
		}
		JSONObject request = new JSONObject(response.toString());
		in.close();
		connection.disconnect();
		return request.getString("auth_token");
	}
	
	/*
	 * Returns the balance as a double. Uses an Authentication Key to create a GET request 
	 */
	private static double get_balance(String key) throws Exception
	{
		URL url = new URL(BALANCE_URL);
		HttpURLConnection connection = (HttpURLConnection)url.openConnection();
		connection.setDoOutput(true);
		connection.setRequestMethod("GET");
		connection.setRequestProperty("Accept", "application/json");
		connection.setRequestProperty("X-Auth-Token", key);
		if(connection.getResponseCode() >= 400) throw new Exception("The ___ API call to get the Balance was unable to properly connect  Response Code: " + connection.getResponseCode());
		BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		JSONObject input = new JSONObject(in.readLine());
		JSONObject data = (JSONObject)input.get("data");
		double balance = data.getDouble("amount");
		in.close();
		return balance;
	}
	
	/*
	 * Uses Runtime.getRuntime().exec and the tail command in Unix to get the last line in the log file
	 */
	private static String getLastLine() {
		String line = "";
		try {
			Process p = Runtime.getRuntime().exec("tail -1 " + LOG_FILE);
			java.io.BufferedReader input = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
			line = input.readLine();
		} catch (java.io.IOException e) {
			e.printStackTrace();
		}
		return line;
	}
	
	/*
	 * Uses the Global variables and the balance passed by main to send a warning email
	 */
    public static void sendWarning(double balance) throws MessagingException
    {
        final Properties p = new Properties();
        p.put("mail.smtp.host", "localhost");
        final Message msg = new MimeMessage(Session.getDefaultInstance(p));
        msg.setFrom(new InternetAddress(WARNING_FROM));
        msg.addRecipient(RecipientType.TO, new InternetAddress(WARNING_TO));
        msg.setSubject(WARNING_SUBJECT);
        msg.setText(WARNING_MESSAGE + balance);
        Transport.send(msg);
    }
}
